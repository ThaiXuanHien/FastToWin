import fs from 'node:fs';
const [role,action]=process.argv.slice(2);
const files=role==='sender'?['grafana-write','config.alloy']:role==='collector'?['redis-monitor']:null;
const dir='/run/secrets',marker='.launcher-owner';
try{
  if(!files||!['start','cleanup'].includes(action))throw Error();
  const uid=process.getuid(),gid=process.getgid();
  const check=(file,directory=false)=>{const s=fs.lstatSync(file);if((directory?!s.isDirectory():!s.isFile())||s.uid!==uid||s.gid!==gid||(s.mode&0o777)!==(directory?0o700:0o600)||(!directory&&s.nlink!==1))throw Error();};
  check('/run',true);
  if(fs.existsSync(dir)){
    check(dir,true);
    check(dir+'/'+marker);
    if(fs.readFileSync(dir+'/'+marker,'utf8')!==role+'-runtime-v1\n')throw Error();
    const present=fs.readdirSync(dir);
    for(const name of present){if(![marker,...files].includes(name))throw Error();check(dir+'/'+name);}
    // Validate the entire owned directory before removing only its known files.
    for(const name of present)fs.unlinkSync(dir+'/'+name);
    fs.rmdirSync(dir);
  }
  if(action==='start'){
    fs.mkdirSync(dir,{mode:0o700});
    fs.writeFileSync(dir+'/'+marker,role+'-runtime-v1\n',{mode:0o600,flag:'wx'});
  }
}catch{process.exitCode=1;}
