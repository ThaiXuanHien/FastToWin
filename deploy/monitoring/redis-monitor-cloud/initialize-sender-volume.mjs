// Separate, secret-free one-shot command. Never starts Alloy or repairs a WAL.
import fs from 'node:fs';

const directory='/var/lib/alloy';
const marker=directory+'/.fasttowin-sender-volume';
const identity='fasttowin-alloy-volume-v1\n';
const component='prometheus.remote_write.grafana';
const uid=65532,gid=65532;
const requireValid=value=>{if(!value)throw Error();};
try{
  requireValid(process.platform==='linux'&&process.getuid()===0&&process.argv.length===2);
  requireValid(!Object.keys(process.env).some(k=>/(?:TOKEN|PASSWORD|SECRET|PRIVATE_KEY)/i.test(k)));
  // Only an exact attached volume is eligible; image directories are not.
  const mounts=fs.readFileSync('/proc/self/mountinfo','utf8');
  requireValid(mounts.length<=1024*1024&&mounts.split('\n').filter(line=>line.split(' ')[4]===directory).length===1);
  for(const parent of ['/var','/var/lib',directory]){
    const s=fs.lstatSync(parent);
    requireValid(s.isDirectory()&&(s.mode&0o7022)===0);
    requireValid(parent===directory?((s.uid===0&&s.gid===0)||(s.uid===uid&&s.gid===gid)):(s.uid===0&&s.gid===0));
  }
  const entries=fs.readdirSync(directory);
  // Some filesystems supply this empty root-owned directory. Never traverse it.
  if(entries.includes('lost+found')){
    const s=fs.lstatSync(directory+'/lost+found');
    requireValid(s.isDirectory()&&s.uid===0&&s.gid===0&&(s.mode&0o7022)===0);
    requireValid(fs.readdirSync(directory+'/lost+found').length===0);
  }
  if(entries.includes('.fasttowin-sender-volume')){
    const root=fs.lstatSync(directory),s=fs.lstatSync(marker);
    requireValid(root.uid===uid&&root.gid===gid&&(root.mode&0o7777)===0o700);
    requireValid(s.isFile()&&s.nlink===1&&s.uid===uid&&s.gid===gid&&(s.mode&0o7777)===0o600&&s.size===identity.length);
    requireValid(fs.readFileSync(marker,'utf8')===identity);
    requireValid(entries.every(e=>['.fasttowin-sender-volume','lost+found',component,'alloy_seed.json','remotecfg'].includes(e)));
    for(const name of [component,'remotecfg'])if(entries.includes(name)){
      const child=fs.lstatSync(directory+'/'+name);
      requireValid(child.isDirectory()&&child.uid===uid&&child.gid===gid&&(child.mode&0o7777)===0o700);
      if(name==='remotecfg')requireValid(fs.readdirSync(directory+'/'+name).length===0);
    }
    if(entries.includes('alloy_seed.json')){
      const seed=fs.lstatSync(directory+'/alloy_seed.json');
      requireValid(seed.isFile()&&seed.nlink===1&&seed.uid===uid&&seed.gid===gid&&(seed.mode&0o7777)===0o600&&seed.size>0&&seed.size<=4096);
    }
    // Repeat calls validate only: never chmod/chown/delete existing history.
  }else{
    requireValid(entries.every(e=>e==='lost+found'));
    const fd=fs.openSync(marker,fs.constants.O_WRONLY|fs.constants.O_CREAT|fs.constants.O_EXCL|fs.constants.O_NOFOLLOW,0o600);
    try{
      fs.writeFileSync(fd,identity);
      fs.fchmodSync(fd,0o600);
      fs.fchownSync(fd,uid,gid);
      fs.fsyncSync(fd);
    }finally{fs.closeSync(fd);}
    fs.chmodSync(directory,0o700);
    fs.chownSync(directory,uid,gid);
  }
  console.log('Sender volume initialized or validated; daemon not started');
}catch{
  console.error('Invalid sender volume initialization');
  process.exitCode=1;
}
