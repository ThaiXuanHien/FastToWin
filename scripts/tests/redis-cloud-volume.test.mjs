// Opt-in Docker acceptance: synthetic, run-owned volumes only; no cloud access.
import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {randomUUID} from 'node:crypto';
import {dockerConfig} from './redis-monitor-docker-config.mjs';
import {stageImageContext} from './redis-cloud-fixture.mjs';

const source=path.resolve('deploy/monitoring/redis-monitor-cloud');
const init=path.join(source,'initialize-sender-volume.mjs');
const artifact=path.resolve('.artifacts/redis-monitor-production-20261103','volume-local-'+randomUUID());
const docker=process.env.FTW_TEST_DOCKER||'C:/Users/Hien Hue/AppData/Local/Programs/DockerDesktop/resources/bin/docker.exe';
const image='node@sha256:c86ecead9e55a5210ac1727de38a4ca840bf03dcaf34dc9749addbf383b1920c';
const label='fasttowin.monitor-volume-test',identity=randomUUID();
let ready=false;
function setup(){
  if(ready)return;
  fs.mkdirSync(artifact,{recursive:true});
  if(process.platform==='win32')assert.equal(spawnSync('icacls.exe',[artifact,'/inheritance:r','/grant:r',process.env.USERDOMAIN+'\\'+process.env.USERNAME+':(OI)(CI)F'],{windowsHide:true,stdio:'ignore'}).status,0);
  fs.mkdirSync(path.join(artifact,'docker-config'));
  fs.writeFileSync(path.join(artifact,'docker-config/config.json'),JSON.stringify(dockerConfig(docker)));
  ready=true;
}
function command(args){
  setup();
  const r=spawnSync(docker,['--config',path.join(artifact,'docker-config'),'--host','npipe:////./pipe/docker_engine',...args],{encoding:'utf8',windowsHide:true,timeout:60000,maxBuffer:1024*1024});
  assert.equal(r.error,undefined);
  assert.equal(r.status,0,r.stdout+r.stderr);
  return (args[0]==='logs'?r.stdout+'\n'+r.stderr:r.stdout).trim();
}
const prelude=`const fs=require('fs'),assert=require('assert/strict'),cp=require('child_process');
const root='/var/lib/alloy',marker=root+'/.fasttowin-sender-volume';
const launch=(args=[],env={PATH:'/usr/local/bin:/usr/bin:/bin'},uid=0)=>cp.spawnSync('/usr/local/bin/node',['/opt/redis-monitor-cloud/initialize-sender-volume.mjs',...args],{encoding:'utf8',uid,gid:uid,env});
const reject=r=>{assert.equal(r.status,1);assert.equal(r.stdout,'');assert.equal(r.stderr,'Invalid sender volume initialization\\n');};
`;
function scenario(code,{mounted=true}={}){
  assert.ok(fs.existsSync(init),'Sender volume initializer is not implemented');
  const volume='ftw-monitor-volume-'+randomUUID();
  command(['volume','create','--label',label+'='+identity,volume]);
  try{
    const args=['run','--rm','--name','ftw-monitor-init-'+randomUUID(),'--label',label+'='+identity,'--network','none','--read-only','--cap-drop','ALL','--cap-add','CHOWN','--cap-add','FOWNER','--cap-add','DAC_OVERRIDE','--cap-add','SETUID','--cap-add','SETGID','--security-opt','no-new-privileges','--pids-limit','32','--cpus','0.25','--memory','250000000','--user','0:0','--tmpfs','/tmp:rw,noexec,nosuid,size=1m','--mount',`type=bind,source=${source},target=/opt/redis-monitor-cloud,readonly`];
    if(mounted)args.push('--mount',`type=volume,source=${volume},target=/var/lib/alloy`);
    args.push('--entrypoint','/usr/local/bin/node',image,'-e',prelude+code);
    command(args);
  }finally{
    const info=JSON.parse(command(['volume','inspect',volume]))[0];
    assert.equal(info.Labels[label],identity);
    command(['volume','rm',volume]);
    assert.equal(command(['volume','ls','-q','--filter','name=^'+volume+'$']),'');
  }
}

test('new mounted volume permits private nonroot writes after one-time initialization',()=>scenario(`
fs.chmodSync(root,0o755);fs.chownSync(root,0,0);
const r=launch();assert.equal(r.status,0,r.stderr);
const s=fs.lstatSync(root),m=fs.lstatSync(marker);
assert.equal(s.uid,65532);assert.equal(s.gid,65532);assert.equal(s.mode&0o777,0o700);
assert.equal(m.uid,65532);assert.equal(m.mode&0o777,0o600);
const write=cp.spawnSync('/usr/local/bin/node',['-e',"require('fs').writeFileSync('/var/lib/alloy/owned-write','nonroot')"],{uid:65532,gid:65532,encoding:'utf8'});
assert.equal(write.status,0,write.stderr);assert.equal(fs.readFileSync(root+'/owned-write','utf8'),'nonroot');
`));

test('repeat initialization preserves existing WAL and refuses unrecognized contents',()=>scenario(`
assert.equal(launch().status,0);
const component=root+'/prometheus.remote_write.grafana';fs.mkdirSync(component,{mode:0o700});fs.chownSync(component,65532,65532);
fs.mkdirSync(component+'/wal',{mode:0o700});fs.chownSync(component+'/wal',65532,65532);
fs.writeFileSync(component+'/wal/00000000','synthetic unsent history');
fs.writeFileSync(root+'/alloy_seed.json','{}',{mode:0o600});fs.chownSync(root+'/alloy_seed.json',65532,65532);
fs.mkdirSync(root+'/remotecfg',{mode:0o700});fs.chownSync(root+'/remotecfg',65532,65532);
const before=fs.readFileSync(component+'/wal/00000000');
assert.equal(launch().status,0);assert.deepEqual(fs.readFileSync(component+'/wal/00000000'),before);
fs.writeFileSync(root+'/foreign','preserve me');reject(launch());assert.equal(fs.readFileSync(root+'/foreign','utf8'),'preserve me');
`));

test('initializer rejects unmounted path rather than changing image filesystem',()=>scenario(`reject(launch());`,{mounted:false}));

test('initializer rejects symlink, hardlink, bad marker and foreign owner without touching targets',()=>scenario(`
fs.writeFileSync('/tmp/outside','outside');
fs.symlinkSync('/tmp/outside',marker);reject(launch());assert.equal(fs.readFileSync('/tmp/outside','utf8'),'outside');fs.unlinkSync(marker);
fs.writeFileSync(root+'/foreign','untouched');reject(launch());assert.equal(fs.statSync(root).uid,0);fs.unlinkSync(root+'/foreign');
assert.equal(launch().status,0);
fs.writeFileSync(marker,'wrong marker');reject(launch());
fs.writeFileSync(marker,'fasttowin-alloy-volume-v1\\n');
fs.linkSync(marker,root+'/linked');reject(launch());fs.unlinkSync(root+'/linked');
fs.chownSync(marker,1000,1000);reject(launch());fs.chownSync(marker,65532,65532);
fs.symlinkSync('/tmp',root+'/prometheus.remote_write.grafana');reject(launch());fs.unlinkSync(root+'/prometheus.remote_write.grafana');
fs.chmodSync(root,0o777);reject(launch());
`));

test('initializer refuses credentials, arguments and nonroot invocation with sanitized errors',()=>scenario(`
reject(launch(['unexpected']));
reject(launch([],{GRAFANA_CLOUD_WRITE_TOKEN:'SYNTHETIC_NEVER_LOG'}));
assert.equal(fs.existsSync(marker),false);
fs.chmodSync(root,0o777);reject(launch([],{},65532));assert.equal(fs.existsSync(marker),false);
`));

test('filesystem recovery directory must be empty and special permission bits are refused',()=>scenario(`
fs.mkdirSync(root+'/lost+found',{mode:0o700});
fs.writeFileSync(root+'/lost+found/recovered','untouched');reject(launch());assert.equal(fs.readFileSync(root+'/lost+found/recovered','utf8'),'untouched');
fs.unlinkSync(root+'/lost+found/recovered');assert.equal(launch().status,0);
fs.chmodSync(root,0o1700);reject(launch());
`));

test('real nonroot Alloy and collector fit revised caps; normal restart preserves the owned volume',async()=>{
  setup();
  stageImageContext(path.resolve('.'),artifact);
  const senderImage='ftw-monitor-volume-sender:'+identity,collectorImage='ftw-monitor-volume-collector:'+identity;
  const volume='ftw-monitor-runtime-'+identity;
  const sender='ftw-monitor-sender-'+identity,collector='ftw-monitor-collector-'+identity;
  const token='synthetic_only_volume_runtime_123456789';
  const senderEnv=path.join(artifact,'sender.env'),collectorEnv=path.join(artifact,'collector.env');
  fs.writeFileSync(senderEnv,[`GRAFANA_CLOUD_WRITE_TOKEN=${token}`,'GRAFANA_CLOUD_REMOTE_WRITE_URL=https://prometheus-fixture.grafana.net/api/prom/push','GRAFANA_CLOUD_EXPECTED_HOST=prometheus-fixture.grafana.net','GRAFANA_CLOUD_TENANT_ID=12345','FASTTOWIN_REDIS_MONITOR_SCRAPE_ADDRESS=redis-monitor.railway.internal:19121'].join('\n'));
  fs.writeFileSync(collectorEnv,'FASTTOWIN_REDIS_MONITOR_ADDRESS=127.0.0.1:6379\nFASTTOWIN_REDIS_MONITOR_PASSWORD='+'a'.repeat(96));
  const initArgs=['run','--rm','--label',label+'='+identity,'--network','none','--user','0:0','--read-only','--cap-drop','ALL','--cap-add','CHOWN','--cap-add','FOWNER','--cap-add','DAC_OVERRIDE','--mount',`type=volume,source=${volume},target=/var/lib/alloy,volume-nocopy`,'--entrypoint','/usr/bin/env',senderImage,'-i','PATH=/usr/local/bin:/usr/bin:/bin','/usr/local/bin/node','/opt/redis-monitor-cloud/initialize-sender-volume.mjs'];
  const snapshot=()=>JSON.parse(command(['run','--rm','--network','none','--user','65532:65532','--read-only','--cap-drop','ALL','--mount',`type=volume,source=${volume},target=/var/lib/alloy,readonly`,'--entrypoint','/usr/local/bin/node',senderImage,'-e',`const fs=require('fs'),crypto=require('crypto'),out={};const walk=p=>{for(const n of fs.readdirSync(p)){const f=p+'/'+n,s=fs.lstatSync(f);if(s.isDirectory())walk(f);else {if(!s.isFile())throw Error();out[f]={bytes:s.size,hash:crypto.createHash('sha256').update(fs.readFileSync(f)).digest('hex')};}}};walk('/var/lib/alloy');console.log(JSON.stringify(out));` ]));
  const state=name=>JSON.parse(command(['container','inspect',name]))[0];
  const waitReady=async(name,url)=>{
    // Probe in another cgroup sharing only the isolated network namespace.
    // Repeated Node execs inside the250MB sender charge probe memory/cache to it.
    const check=command(['run','--rm','--label',label+'='+identity,'--network','container:'+name,'--read-only','--user','65532:65532','--cap-drop','ALL','--pids-limit','24','--memory','100000000','--entrypoint','/usr/local/bin/node',image,'-e',`(async()=>{const end=Date.now()+25000;while(Date.now()<end){try{const r=await fetch('${url}',{signal:AbortSignal.timeout(1000)});if(r.status===200){console.log('ready');return;}}catch{}await new Promise(r=>setTimeout(r,500));}console.log('not-ready');})();`]);
    if(check==='ready')return;
    const s=state(name),logs=command(['logs',name]);assert.doesNotMatch(logs,new RegExp(token+'|'+'a'.repeat(96)));
    const processes=s.State.Running?command(['exec',name,'/usr/local/bin/node','-e',"const fs=require('fs'),out=[];for(const n of fs.readdirSync('/proc').filter(x=>/^[0-9]+$/.test(x))){try{out.push({pid:n,status:fs.readFileSync('/proc/'+n+'/status','utf8').split('\\n').filter(x=>/^(Name|State|Threads|VmRSS):/.test(x))});}catch{}}console.log(JSON.stringify(out));"]):null;
    fs.writeFileSync(path.join(artifact,'readiness-failure.json'),JSON.stringify({running:s.State.Running,exitCode:s.State.ExitCode,oomKilled:s.State.OOMKilled,logs,processes,stats:command(['stats','--no-stream','--format','json',name])},null,2));
    assert.fail('local runtime readiness deadline');
  };
  const gracefulStop=async name=>{
    // Pinned Alloy v1.18.0 allows one minute to flush remote_write on shutdown.
    // A disconnected fixture must not replace that normal exit with Docker's
    // default ten-second SIGKILL. Keep the daemon/config unchanged and observe.
    const started=Date.now(),deadline=started+75000;
    command(['kill','--signal','TERM',name]);
    while(Date.now()<deadline){
      const s=state(name);
      if(!s.State.Running){
        const logs=command(['logs',name]);assert.doesNotMatch(logs,new RegExp(token+'|'+'a'.repeat(96)));
        const result={elapsedMs:Date.now()-started,exitCode:s.State.ExitCode,oomKilled:s.State.OOMKilled};
        fs.appendFileSync(path.join(artifact,'shutdown.jsonl'),JSON.stringify(result)+'\n');
        assert.equal(result.exitCode,0);assert.equal(result.oomKilled,false);return;
      }
      await new Promise(r=>setTimeout(r,500));
    }
    assert.fail('Alloy normal shutdown exceeded bounded flush allowance');
  };
  const cleanup=[];
  try{
    for(const [role,imageName] of [['sender',senderImage],['collector',collectorImage]])command(['build','--network','none','--label',label+'='+identity,'-t',imageName,'-f',path.join(artifact,'image/deploy/monitoring/redis-monitor-cloud/Dockerfile.'+role),path.join(artifact,'image')]);
    command(['volume','create','--label',label+'='+identity,volume]);
    assert.match(command(initArgs),/daemon not started/);
    for(const [name,imageName,envFile] of [[sender,senderImage,senderEnv],[collector,collectorImage,collectorEnv]]){
      const args=['run','-d','--name',name,'--label',label+'='+identity,'--network','none','--read-only','--cap-drop','ALL','--security-opt','no-new-privileges','--pids-limit','48','--cpus','0.25','--memory','250000000','--tmpfs','/run:rw,noexec,nosuid,size=1m,mode=0700,uid=65532,gid=65532','--env-file',envFile];
      if(name===sender)args.push('--mount',`type=volume,source=${volume},target=/var/lib/alloy,volume-nocopy`);
      command([...args,imageName]);
    }
    await waitReady(sender,'http://127.0.0.1:12345/-/ready');
    await waitReady(collector,'http://127.0.0.1:19121/health');
    const walDeadline=Date.now()+35000;
    let walBytes=0;
    while(Date.now()<walDeadline){
      walBytes=Number(command(['run','--rm','--label',label+'='+identity,'--network','none','--read-only','--cap-drop','ALL','--user','65532:65532','--memory','100000000','--mount',`type=volume,source=${volume},target=/var/lib/alloy,readonly`,'--entrypoint','/usr/local/bin/node',image,'-e',"const fs=require('fs'),p='/var/lib/alloy/prometheus.remote_write.grafana/wal';console.log(fs.readdirSync(p).filter(n=>/^[0-9]+$/.test(n)).reduce((sum,n)=>sum+fs.statSync(p+'/'+n).size,0));"]));
      if(walBytes>0)break;
      await new Promise(r=>setTimeout(r,500));
    }
    assert.ok(walBytes>0,'real Alloy must buffer a sample in WAL');
    const receipts={};
    for(const name of [sender,collector]){
      const s=state(name);assert.equal(s.Config.User,'65532:65532');assert.equal(s.HostConfig.Memory,250000000);assert.equal(s.HostConfig.NanoCpus,250000000);assert.equal(s.State.OOMKilled,false);
      assert.match(command(['exec',name,'/usr/local/bin/node','-e',"console.log(require('fs').readFileSync('/proc/1/status','utf8').split('\\n').filter(x=>/^(Uid|Gid):/.test(x)).join('\\n'))"]),/Uid:\s+65532\s+65532\s+65532\s+65532/);
      receipts[name===sender?'sender':'collector']={uid:65532,memoryLimit:s.HostConfig.Memory,nanoCpus:s.HostConfig.NanoCpus,oomKilled:false,stats:JSON.parse(command(['stats','--no-stream','--format','json',name]))};
      assert.doesNotMatch(command(['logs',name]),new RegExp(token+'|'+'a'.repeat(96)));
    }
    await gracefulStop(sender);
    const before=snapshot();fs.writeFileSync(path.join(artifact,'wal-before.json'),JSON.stringify(before,null,2));
    fs.writeFileSync(path.join(artifact,'volume-layout.json'),command(['run','--rm','--network','none','--user','0:0','--read-only','--mount',`type=volume,source=${volume},target=/var/lib/alloy,readonly`,'--entrypoint','/usr/local/bin/node',senderImage,'-e',`const fs=require('fs'),root='/var/lib/alloy';console.log(JSON.stringify([root,...fs.readdirSync(root).map(n=>root+'/'+n)].map(p=>{const s=fs.lstatSync(p);return {path:p,uid:s.uid,gid:s.gid,mode:s.mode&4095};})));`]));
    assert.ok(Object.keys(before).some(p=>p.includes('/wal/')),'real Alloy must create a WAL');
    assert.match(command(initArgs),/validated/);assert.deepEqual(snapshot(),before,'repeat initializer must not touch WAL bytes');
    command(['start',sender]);await waitReady(sender,'http://127.0.0.1:12345/-/ready');
    await gracefulStop(sender);
    const after=snapshot();assert.ok(Object.keys(after).some(p=>p.includes('/wal/')));assert.deepEqual(after['/var/lib/alloy/.fasttowin-sender-volume'],before['/var/lib/alloy/.fasttowin-sender-volume']);
    fs.writeFileSync(path.join(artifact,'runtime-acceptance.json'),JSON.stringify({passed:true,receipts,volumePreserved:true,normalRestart:true,realCloudReceipt:false},null,2));
  }finally{
    for(const [kind,name] of [['container',sender],['container',collector],['volume',volume],['image',senderImage],['image',collectorImage]]){
      try{
        const r=spawnSync(docker,['--config',path.join(artifact,'docker-config'),'--host','npipe:////./pipe/docker_engine',kind,'inspect',name],{encoding:'utf8',windowsHide:true,timeout:10000});
        if(r.status!==0){assert.match(r.stderr,/No such|not found|does not exist/i);continue;}
        const info=JSON.parse(r.stdout)[0];assert.equal((kind==='volume'?info.Labels:info.Config.Labels)[label],identity);
        command(kind==='container'?['rm','-f',name]:kind==='volume'?['volume','rm',name]:['image','rm',name]);cleanup.push({kind,removed:true});
      }catch(e){cleanup.push({kind,removed:false});}
    }
    fs.rmSync(senderEnv,{force:true});fs.rmSync(collectorEnv,{force:true});
    fs.writeFileSync(path.join(artifact,'runtime-cleanup.json'),JSON.stringify(cleanup));
    assert.ok(cleanup.every(r=>r.removed),'owned runtime cleanup failed');
  }
});

test.after(()=>{if(ready){fs.rmSync(path.join(artifact,'image'),{recursive:true,force:true});fs.rmSync(path.join(artifact,'docker-config'),{recursive:true,force:true});console.log('Sanitized local evidence: '+artifact);}});
