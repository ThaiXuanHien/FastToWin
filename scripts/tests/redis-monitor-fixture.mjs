// Test-only, loopback fixture. Never use against production.
import net from 'node:net';
import fs from 'node:fs';
import path from 'node:path';
import cp from 'node:child_process';
import {randomBytes} from 'node:crypto';
import {fileURLToPath} from 'node:url';
const delay=ms=>new Promise(r=>setTimeout(r,ms));
const info='used_memory:2097152\r\nmaxmemory:67108864\r\nevicted_keys:0\r\naof_enabled:1\r\naof_current_size:4096\r\naof_base_size:1024\r\naof_last_write_status:ok\r\naof_last_bgrewrite_status:ok\r\naof_rewrite_in_progress:0\r\naof_pending_bio_fsync:0\r\naof_delayed_fsync:0\r\n';
function frame(args){return '*'+args.length+'\r\n'+args.map(v=>'$'+Buffer.byteLength(v)+'\r\n'+v+'\r\n').join('');}
function parse(buf){
  const m=/^\*(\d+)\r\n/.exec(buf); if(!m)return null;
  let offset=m[0].length,args=[];
  for(let i=0;i<Number(m[1]);i++){
    const h=/^\$(\d+)\r\n/.exec(buf.slice(offset)); if(!h)return null;
    offset+=h[0].length; const n=Number(h[1]); if(buf.length<offset+n+2)return null;
    args.push(buf.slice(offset,offset+n));offset+=n+2;
  }return {args,offset};
}
export async function createPeer(secret,mode){
  let count=0; const sockets=new Set();
  const server=net.createServer(s=>{
    sockets.add(s);s.on('close',()=>sockets.delete(s));s.on('error',()=>{});s.setTimeout(4000,()=>s.destroy());let buf='',step=0;
    s.on('data',chunk=>{
      buf+=chunk.toString();if(buf.length>1024){s.destroy();return;}
      let p;while((p=parse(buf))){buf=buf.slice(p.offset); const expected=[['AUTH','ftw_monitor',secret],['PING'],['INFO','memory','persistence','stats']][step];
        if(!expected||JSON.stringify(p.args)!==JSON.stringify(expected)){s.end('-ERR fixed exchange only\r\n');return;}
        if(step++===0){count++;s.write('+OK\r\n');}else if(step===2){s.write('+PONG\r\n');}else if(mode()==='fault'){s.end('-ERR synthetic unavailable\r\n');}else{s.end('$'+Buffer.byteLength(info)+'\r\n'+info+'\r\n');}
      }
    });
  });await new Promise((resolve,reject)=>{server.once('error',reject);server.listen(0,'127.0.0.1',resolve);});
  return {port:server.address().port,attempts:()=>count,close:()=>new Promise(r=>{for(const s of sockets)s.destroy();server.close(r);})};
}
async function freePort(){const s=net.createServer();await new Promise(r=>s.listen(0,'127.0.0.1',r));const p=s.address().port;await new Promise(r=>s.close(r));return p;}
async function waitFor(fn,seconds=45){for(let i=0;i<seconds*4;i++){try{if(await fn())return;}catch{}await delay(250);}throw new Error('fixture readiness deadline');}
function write(p,v){fs.writeFileSync(p,typeof v==='string'?v:JSON.stringify(v,null,2)+'\n');}
export async function nativeFixture(artifact,repo){
  const owned=path.join(repo,'.artifacts/redis-monitor-local-20261103')+path.sep;
  if(!artifact.startsWith(owned)||!/^run-[a-f0-9]{32}$/.test(path.basename(artifact)))throw new Error('invalid owned fixture path');
  const toolRoot=path.join(repo,'.artifacts/redis-monitor-local-20261103');
  const grafana=JSON.parse(fs.readFileSync(path.join(toolRoot,'tools/grafana-provenance.json'))).executable;
  const prometheus=path.join(toolRoot,'tools/prometheus-windows/prometheus.exe');
  const children=[],logs=[];let peer,stopping=false;
  const stop=()=>{stopping=true;};process.once('SIGINT',stop);process.once('SIGTERM',stop);
  const secret=()=>randomBytes(48).toString('hex');write(path.join(artifact,'monitor'),secret());write(path.join(artifact,'grafana-secret'),secret());write(path.join(artifact,'mode'),'healthy');
  function launch(name,exe,args,env={}){
    const out=fs.openSync(path.join(artifact,name+'.log'),'a');logs.push(out);
    const inherited=Object.fromEntries(['SystemRoot','WINDIR','PATH','PATHEXT','TEMP','TMP','USERPROFILE','APPDATA','LOCALAPPDATA'].filter(k=>process.env[k]!==undefined).map(k=>[k,process.env[k]]));
    const child=cp.spawn(exe,args,{cwd:artifact,windowsHide:true,stdio:['ignore',out,out],env:{...inherited,...env}});children.push({name,child,exe});
    child.on('error',()=>{});write(path.join(artifact,'processes.json'),children.map(x=>({name:x.name,pid:x.child.pid,exe:x.exe})));return child;
  }
  try{
    peer=await createPeer(fs.readFileSync(path.join(artifact,'monitor'),'utf8'),()=>fs.readFileSync(path.join(artifact,'mode'),'utf8').trim());
    const collectorPort=await freePort(),promPort=await freePort(),grafPort=await freePort();
    const collector=launch('collector',path.join(toolRoot,'redis-monitor.exe'),[],{FASTTOWIN_REDIS_MONITOR_ADDRESS:'127.0.0.1:'+peer.port,FASTTOWIN_REDIS_MONITOR_PASSWORD_FILE:path.join(artifact,'monitor'),FASTTOWIN_REDIS_MONITOR_LISTEN:'127.0.0.1:'+collectorPort});
    const rule=path.join(repo,'deploy/monitoring/redis-monitor/redis-alerts.yml').replaceAll('\\','/');
    write(path.join(artifact,'prometheus.yml'),`global:\n  scrape_interval: 15s\n  evaluation_interval: 15s\nrule_files: ['${rule}']\nscrape_configs:\n  - job_name: fasttowin-redis-monitor\n    scrape_timeout: 5s\n    static_configs:\n      - targets: ['127.0.0.1:${collectorPort}']\n`);
    launch('prometheus',prometheus,['--config.file='+path.join(artifact,'prometheus.yml'),'--storage.tsdb.path='+path.join(artifact,'tsdb'),'--storage.tsdb.retention.time=2h','--web.listen-address=127.0.0.1:'+promPort]);
    for(const sub of ['provisioning/datasources','provisioning/dashboards','dashboards','grafana-data','plugins'])fs.mkdirSync(path.join(artifact,sub),{recursive:true});
    fs.copyFileSync(path.join(repo,'deploy/monitoring/grafana/dashboards/fasttowin-redis.json'),path.join(artifact,'dashboards/redis.json'));
    write(path.join(artifact,'provisioning/datasources/local.yml'),`apiVersion: 1\ndatasources:\n  - name: Prometheus\n    uid: prometheus\n    type: prometheus\n    access: proxy\n    url: http://127.0.0.1:${promPort}\n    isDefault: true\n    editable: false\n`);
    write(path.join(artifact,'provisioning/dashboards/local.yml'),`apiVersion: 1\nproviders:\n  - name: Local Redis\n    type: file\n    options:\n      path: ${path.join(artifact,'dashboards').replaceAll('\\','/')}\n`);
    write(path.join(artifact,'grafana.ini'),`[server]\nhttp_addr = 127.0.0.1\nhttp_port = ${grafPort}\n[paths]\ndata = ${path.join(artifact,'grafana-data')}\nlogs = ${artifact}\nplugins = ${path.join(artifact,'plugins')}\nprovisioning = ${path.join(artifact,'provisioning')}\n[security]\nadmin_password = $__file{${path.join(artifact,'grafana-secret')}}\n[auth.anonymous]\nenabled = true\norg_role = Viewer\n[users]\nallow_sign_up = false\n[analytics]\nreporting_enabled = false\ncheck_for_updates = false\n[plugins]\npreinstall_disabled = true\n[log]\nmode = console\nlevel = error\n`);
    launch('grafana',grafana,['server','--homepath',path.dirname(path.dirname(grafana)),'--config',path.join(artifact,'grafana.ini')]);
    const base='http://127.0.0.1:';
    await waitFor(async()=> (await fetch(base+collectorPort+'/ready')).ok && (await fetch(base+promPort+'/-/ready')).ok && (await fetch(base+grafPort+'/api/health')).ok,60);
    const state={artifact,collector:base+collectorPort,prometheus:base+promPort,grafana:base+grafPort,dashboard:base+grafPort+'/d/fasttowin-redis?orgId=1&from=now-5m&to=now&refresh=5s&kiosk',synthetic:true};write(path.join(artifact,'endpoints.json'),state);
    write(path.join(artifact,'healthy.metrics'),await (await fetch(state.collector+'/metrics')).text());
    console.log(JSON.stringify(state));
    while(!stopping&&!fs.existsSync(path.join(artifact,'stop'))){
      if(fs.existsSync(path.join(artifact,'collector-stop'))&&!collector.killed)collector.kill();
      if(children.some(x=>x.name!=='collector'&&(x.child.exitCode!==null||x.child.signalCode!==null)))throw new Error('fixture child exited');
      await delay(250);
    }
  }finally{
    process.removeListener('SIGINT',stop);process.removeListener('SIGTERM',stop);
    const exited=x=>x.child.exitCode!==null||x.child.signalCode!==null;
    for(const x of children.reverse())if(!exited(x))x.child.kill();
    await Promise.all(children.map(x=>new Promise(r=>{if(exited(x))return r();const timer=setTimeout(r,5000);x.child.once('exit',()=>{clearTimeout(timer);r();});})));
    if(peer)await peer.close();for(const fd of logs)fs.closeSync(fd);
    for(const f of ['monitor','grafana-secret'])if(fs.existsSync(path.join(artifact,f)))fs.unlinkSync(path.join(artifact,f));
    // Grafana's local DB contains only the synthetic admin hash; remove exact owned file.
    for(const f of ['grafana-data/grafana.db','grafana-data/grafana.db-shm','grafana-data/grafana.db-wal'])if(fs.existsSync(path.join(artifact,f)))fs.unlinkSync(path.join(artifact,f));
    write(path.join(artifact,'cleanup.json'),{children:children.map(x=>({name:x.name,pid:x.child.pid,exited:exited(x)})),syntheticSecretsRemoved:true,peerClosed:true});
  }
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
  try{if(process.argv[2]==='native')await nativeFixture(path.resolve(process.argv[3]),path.resolve(process.argv[4]));else throw new Error('unknown fixture mode');}
  catch{console.error('fixture_failed');process.exitCode=1;}
}
