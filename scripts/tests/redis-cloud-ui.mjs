// Independent localhost-only synthetic UI acceptance of the shipped Cloud
// dashboard. This never stands in for Alloy remote-write receipt.
import fs from 'node:fs';
import path from 'node:path';
import http from 'node:http';
import net from 'node:net';
import cp from 'node:child_process';
import {randomBytes} from 'node:crypto';
const delay=ms=>new Promise(r=>setTimeout(r,ms));
export async function waitForUIReady(urls,{deadline,attempts=60,request=fetch,pause=delay}={}){
  for(let i=0;i<attempts;i++){
    let ready=true;
    for(const url of urls){
      const remaining=deadline-Date.now();
      if(remaining<=0)throw new Error('ui_readiness_deadline');
      const controller=new AbortController();
      const timer=setTimeout(()=>controller.abort(),Math.min(1000,remaining));
      try{if(!(await request(url,{signal:controller.signal})).ok)ready=false;}catch{ready=false;}finally{clearTimeout(timer);}
    }
    if(ready&&Date.now()<deadline)return;
    if(Date.now()>=deadline)throw new Error('ui_readiness_deadline');
    await pause(Math.min(1000,deadline-Date.now()));
  }
  throw new Error('ui_readiness_exhausted');
}
export async function cleanupUI({children,closeListener,closeLogs=[],removeSecrets=[],save,waitChild=async child=>{
  if(child.exitCode!==null||child.signalCode!==null)return;
  await new Promise(resolve=>{const done=()=>{clearTimeout(timer);child.removeListener('exit',done);resolve();};const timer=setTimeout(done,5000);child.once('exit',done);});
}}){
  const actions=[];
  async function attempt(name,action){try{await action();actions.push({name,ok:true});}catch{actions.push({name,ok:false});}}
  for(const {name,child} of children)await attempt('kill:'+name,()=>{if(child.exitCode===null&&child.signalCode===null)child.kill();});
  for(const {name,child} of children)await attempt('wait:'+name,()=>waitChild(child));
  await attempt('listener',closeListener);
  for(const [i,action] of closeLogs.entries())await attempt('log:'+i,action);
  for(const [i,action] of removeSecrets.entries())await attempt('secret:'+i,action);
  const states=children.map(({name,child})=>({name,pid:child.pid,exited:child.exitCode!==null||child.signalCode!==null}));
  const complete=actions.every(x=>x.ok)&&states.every(x=>x.exited);
  await save({children:states,actions,syntheticSecretsRemoved:actions.filter(x=>x.name.startsWith('secret:')).every(x=>x.ok),listenerClosed:actions.find(x=>x.name==='listener').ok,complete});
  if(!complete)throw new Error('ui_cleanup_incomplete');
}
async function freePort(){const s=net.createServer();await new Promise(r=>s.listen(0,'127.0.0.1',r));const p=s.address().port;await new Promise(r=>s.close(r));return p;}
const write=(file,value)=>fs.writeFileSync(file,typeof value==='string'?value:JSON.stringify(value,null,2)+'\n');
export async function nativeCloudUI(repo,artifact){
  const tools=path.join(repo,'.artifacts/redis-monitor-local-20261103/tools'),children=[],logs=[];
  const prometheus=path.join(tools,'prometheus-windows/prometheus.exe'),grafana=JSON.parse(fs.readFileSync(path.join(tools,'grafana-provenance.json'))).executable;
  let server,lastTimestamp=Date.now(),lastMode='healthy';const started=Date.now();
  const values={up:1,collection_duration_seconds:0.002,last_success_timestamp_seconds:Date.now()/1000,memory_used_bytes:2097152,memory_max_bytes:67108864,evicted_keys_total:0,aof_enabled:1,aof_current_size_bytes:4096,aof_base_size_bytes:1024,aof_last_write_ok:1,aof_last_rewrite_ok:1,aof_rewrite_in_progress:0,aof_pending_fsync:0,aof_delayed_fsync_total:0};
  function launch(name,exe,args){const fd=fs.openSync(path.join(artifact,name+'.log'),'a');logs.push(fd);const env=Object.fromEntries(['SystemRoot','WINDIR','PATH','PATHEXT','TEMP','TMP','USERPROFILE','APPDATA','LOCALAPPDATA'].filter(k=>process.env[k]!==undefined).map(k=>[k,process.env[k]]));const child=cp.spawn(exe,args,{cwd:artifact,windowsHide:true,stdio:['ignore',fd,fd],env});children.push({name,child,exe});child.on('error',()=>{});write(path.join(artifact,'processes.json'),children.map(x=>({name:x.name,pid:x.child.pid,exe:x.exe})));}
  try{
    for(const dir of ['provisioning/datasources','provisioning/dashboards','dashboards','grafana-data','plugins'])fs.mkdirSync(path.join(artifact,dir),{recursive:true});
    write(path.join(artifact,'mode'),'healthy');write(path.join(artifact,'grafana-secret'),randomBytes(48).toString('hex'));
    server=http.createServer((req,res)=>{
      const mode=fs.readFileSync(path.join(artifact,'mode'),'utf8').trim();if(mode==='healthy')lastTimestamp=Date.now();
      if(mode!==lastMode){write(path.join(artifact,'transition-'+mode+'.json'),{at:Date.now(),lastSampleTimestamp:lastTimestamp});lastMode=mode;}
      const labels='job="fasttowin-redis-monitor",instance="redis-primary"';
      const metrics=Object.entries(values).map(([name,value])=>'fasttowin_redis_monitor_'+name+'{'+labels+'} '+(name==='last_success_timestamp_seconds'?lastTimestamp/1000:value)+' '+lastTimestamp);
      for(const reason of ['connect','timeout','auth','protocol','oversized','missing','invalid'])metrics.push('fasttowin_redis_monitor_collection_errors_total{'+labels+',reason="'+reason+'"} 0 '+lastTimestamp);
      metrics.push('up{'+labels+'} 1 '+lastTimestamp);res.writeHead(200,{'content-type':'text/plain'}).end(metrics.join('\n')+'\n');
    });await new Promise(r=>server.listen(0,'127.0.0.1',r));
    const promPort=await freePort(),grafPort=await freePort();
    write(path.join(artifact,'prometheus.yml'),`global:\n  scrape_interval: 15s\nscrape_configs:\n  - job_name: synthetic-cloud-ui\n    honor_labels: true\n    honor_timestamps: true\n    static_configs:\n      - targets: ['127.0.0.1:${server.address().port}']\n`);
    const dashboard=fs.readFileSync(path.join(repo,'deploy/monitoring/grafana/dashboards/fasttowin-redis-cloud.json'),'utf8');
    write(path.join(artifact,'dashboards/redis-cloud.json'),dashboard.replaceAll('${DS_PROMETHEUS}','fixture-cloud-prometheus'));
    write(path.join(artifact,'provisioning/datasources/local.yml'),`apiVersion: 1\ndatasources:\n  - name: Cloud UI synthetic fixture\n    uid: fixture-cloud-prometheus\n    type: prometheus\n    access: proxy\n    url: http://127.0.0.1:${promPort}\n    isDefault: true\n    editable: false\n`);
    write(path.join(artifact,'provisioning/dashboards/local.yml'),`apiVersion: 1\nproviders:\n  - name: Cloud UI synthetic\n    type: file\n    options:\n      path: ${path.join(artifact,'dashboards').replaceAll('\\','/')}\n`);
    write(path.join(artifact,'grafana.ini'),`[server]\nhttp_addr = 127.0.0.1\nhttp_port = ${grafPort}\n[paths]\ndata = ${path.join(artifact,'grafana-data')}\nlogs = ${artifact}\nplugins = ${path.join(artifact,'plugins')}\nprovisioning = ${path.join(artifact,'provisioning')}\n[security]\nadmin_password = $__file{${path.join(artifact,'grafana-secret')}}\n[auth.anonymous]\nenabled = true\norg_role = Viewer\n[users]\nallow_sign_up = false\n[analytics]\nreporting_enabled = false\ncheck_for_updates = false\n[plugins]\npreinstall_disabled = true\n[log]\nmode = console\nlevel = error\n`);
    launch('prometheus',prometheus,['--config.file='+path.join(artifact,'prometheus.yml'),'--storage.tsdb.path='+path.join(artifact,'tsdb'),'--storage.tsdb.retention.time=2h','--web.listen-address=127.0.0.1:'+promPort]);
    launch('grafana',grafana,['server','--homepath',path.dirname(path.dirname(grafana)),'--config',path.join(artifact,'grafana.ini')]);
    const endpoint={artifact,prometheus:'http://127.0.0.1:'+promPort,grafana:'http://127.0.0.1:'+grafPort,dashboard:'http://127.0.0.1:'+grafPort+'/d/fasttowin-redis-cloud?orgId=1&from=now-5m&to=now&refresh=5s&kiosk',synthetic:true,deadlineMinutes:12};
    await waitForUIReady([endpoint.grafana+'/api/health',endpoint.prometheus+'/-/ready'],{deadline:started+12*60*1000});
    write(path.join(artifact,'endpoints.json'),endpoint);console.log(JSON.stringify(endpoint));
    while(!fs.existsSync(path.join(artifact,'stop'))&&Date.now()-started<12*60*1000){if(children.some(x=>x.child.exitCode!==null||x.child.signalCode!==null))throw new Error('ui_child_exited');await delay(500);}
  }finally{
    await cleanupUI({children,
      closeListener:async()=>{if(server)await new Promise((resolve,reject)=>{const timer=setTimeout(()=>reject(new Error('listener_close_timeout')),5000);server.close(error=>{clearTimeout(timer);error?reject(error):resolve();});server.closeAllConnections();});},
      closeLogs:logs.map(fd=>()=>fs.closeSync(fd)),
      removeSecrets:['grafana-secret','grafana-data/grafana.db','grafana-data/grafana.db-shm','grafana-data/grafana.db-wal'].map(relative=>()=>{const file=path.join(artifact,relative);if(fs.existsSync(file))fs.unlinkSync(file);}),
      save:receipt=>write(path.join(artifact,'cleanup.json'),receipt)
    });
  }
}
