// Bounded opt-in local shipping acceptance; never uses production credentials.
import fs from 'node:fs';
import path from 'node:path';
import cp from 'node:child_process';
import {randomBytes} from 'node:crypto';
import assert from 'node:assert/strict';
import {fileURLToPath} from 'node:url';
import {renderAlloy,metricNames} from '../../deploy/monitoring/redis-monitor-cloud/prepare.mjs';
import {dockerConfig} from './redis-monitor-docker-config.mjs';
import {restartGapEvidence,finalizeCleanup} from './redis-cloud-evidence.mjs';
export function ownedPath(repo,id){
  if(!/^[a-f0-9]{32}$/.test(id))throw new Error('invalid fixture identity');
  return path.join(path.resolve(repo),'.artifacts/redis-cloud-local-20261103','run-'+id);
}
export function stageImageContext(repo,artifact,collectorBinary=path.join(repo,'.artifacts/redis-monitor-local-20261103/redis-monitor-linux-amd64')){
  const source=path.join(repo,'deploy/monitoring/redis-monitor-cloud'),target=path.join(artifact,'image/deploy/monitoring/redis-monitor-cloud');
  fs.mkdirSync(target,{recursive:true});
  for(const f of ['Dockerfile.collector','Dockerfile.sender','prepare.mjs','prepare-runtime.mjs','runtime-directory.mjs','initialize-sender-volume.mjs','collector-entrypoint.sh','sender-entrypoint.sh'])fs.copyFileSync(path.join(source,f),path.join(target,f));
  fs.copyFileSync(collectorBinary,path.join(artifact,'image/redis-monitor'));
}
export function provisionDecoders({repo,artifact,mode,copyFile=fs.copyFileSync}){
  if(mode==='ui')return;
  if(!['shipping','waldiagnostic','storage','fullonly','fullrecovery','fullreset'].includes(mode))throw new Error('unknown_fixture_mode');
  const bootstrap=path.join(repo,'.artifacts/redis-cloud-local-20261103/decoder-bootstrap');
  copyFile(path.join(bootstrap,'decoder.exe'),path.join(artifact,'decoder.exe'));
  copyFile(path.join(bootstrap,'decoder-linux'),path.join(artifact,'receiver/diagnostic-decoder'));
}
export function localAlloy({accelerated=false}={}){
  let config=renderAlloy({url:'https://prometheus-fixture.grafana.net/api/prom/push',expectedHost:'prometheus-fixture.grafana.net',tenantId:'12345',collectorAddress:'redis-monitor.railway.internal:19121'}).replace('https://prometheus-fixture.grafana.net/api/prom/push','https://receiver:9443/api/v1/write').replace('    remote_timeout = "10s"','    tls_config {\n      ca_file = "/fixture/ca.pem"\n    }\n    remote_timeout = "10s"');
  if(accelerated)config=config.replace('truncate_frequency = "5m"','truncate_frequency = "10s"').replace('min_keepalive_time = "5m"','min_keepalive_time = "10s"').replace('max_keepalive_time = "2h"','max_keepalive_time = "30s"');
  return config;
}
export function certificates(dir,repo=path.resolve('.')){
  const tool=JSON.parse(fs.readFileSync(path.join(repo,'.artifacts/redis-monitor-local-20261103/toolchain.json')));
  const result=cp.spawnSync(tool.executable,['run',path.join(repo,'scripts/tests/redis-cloud-cert.go'),dir],{env:{...process.env,GOCACHE:tool.goCache,GOPATH:tool.goPath,GOTOOLCHAIN:'local',GOTELEMETRY:'off',GOOS:process.platform==='win32'?'windows':process.platform,GOARCH:'amd64',CGO_ENABLED:'0'},encoding:'utf8',windowsHide:true,timeout:60000});
  assert.equal(result.status,0,'fixture certificate generation failed');
}
const delay=ms=>new Promise(r=>setTimeout(r,ms));
const write=(file,value)=>fs.writeFileSync(file,typeof value==='string'?value:JSON.stringify(value,null,2)+'\n');
export function fixtureModeOptions(mode){
  if(!['shipping','waldiagnostic','storage','fullonly','fullrecovery','fullreset'].includes(mode))throw new Error('unknown_fixture_mode');
  return {diagnostic:mode==='waldiagnostic',storage:['storage','fullonly','fullrecovery','fullreset'].includes(mode),fullOnly:['fullonly','fullrecovery','fullreset'].includes(mode),manualRecovery:['fullrecovery','fullreset'].includes(mode),resetWal:mode==='fullreset'};
}
async function shipping(repo,id,{diagnostic=false,storage=false,fullOnly=false,manualRecovery=false,resetWal=false}={}){
  const artifact=ownedPath(repo,id);assert.ok(fs.existsSync(artifact));
  const env={...process.env};for(const k of Object.keys(env))if(/^(DOCKER_|BUILDX_|BUILDKIT_)/.test(k))delete env[k];
  env.DOCKER_HOST='npipe:////./pipe/docker_engine';env.DOCKER_CONFIG=path.join(artifact,'docker-config');
  env.FTW_CLOUD_RUN_ID=id;env.FTW_CLOUD_ARTIFACT=artifact;env.FTW_CLOUD_IPV6_SUBNET='fd43:'+id.slice(0,4)+':'+id.slice(4,8)+'::/64';
  const executable=cp.spawnSync('where.exe',['docker'],{encoding:'utf8',windowsHide:true}).stdout.trim().split(/\r?\n/)[0];assert.ok(path.isAbsolute(executable));
  fs.mkdirSync(env.DOCKER_CONFIG);write(path.join(env.DOCKER_CONFIG,'config.json'),dockerConfig(executable));
  const compose=['compose','-f',path.join(repo,'deploy/monitoring/redis-monitor-cloud/compose.local.yaml')];
  const commands=[];let stage='prepare',runtimeDeadline=Infinity;
  const receipt={id,artifact,startedAt:new Date().toISOString(),gates:{},limitations:['Local fixture receipt is not Grafana Cloud acceptance.','Named Docker volume has no portable hard 1-GB quota; physical disk durability is untested.']};
  function save(){write(path.join(artifact,'acceptance.json'),receipt);write(path.join(artifact,'commands.json'),commands);}
  function docker(args,seconds=90){
    if(Date.now()>=runtimeDeadline)throw new Error('diagnostic_runtime_deadline');
    seconds=Math.min(seconds,(runtimeDeadline-Date.now())/1000);
    commands.push({at:Date.now(),args});save();
    const r=cp.spawnSync(executable,args,{env,encoding:'utf8',windowsHide:true,timeout:seconds*1000,maxBuffer:4*1024*1024});
    if(r.status!==0){write(path.join(artifact,'docker-failure.json'),{stage,status:r.status,code:r.error?.code,diagnostic:(r.stderr??'').replace(/[a-f0-9]{96}/g,'[synthetic-redacted]').slice(-2200)});throw new Error('owned_docker_command_failed');}
    return (args[0]==='logs'?r.stdout+'\n'+r.stderr:r.stdout).trim();
  }
  const inspect=(type,name)=>JSON.parse(docker([type,'inspect',name]))[0];
  const owns=x=>x?.Labels?.['fasttowin.redis-cloud-run']===id;
  function container(service){const cid=docker([...compose,'ps','-aq',service]);assert.match(cid,/^[a-f0-9]{64}$/);assert.ok(owns(inspect('container',cid).Config));return cid;}
  const probe=(mode,arg)=>JSON.parse(docker(['exec',container('receiver'),'node','/fixture/probe.mjs',mode,...(arg?[arg]:[])]));
  const query=expr=>probe('query',expr).result;
  function gate(name,value){receipt.gates[name]={at:new Date().toISOString(),...value};save();console.log('Local acceptance gate: '+name);}
  async function until(fn,seconds=45){const end=Math.min(runtimeDeadline,Date.now()+seconds*1000);while(Date.now()<end){try{const value=fn();if(value)return value;}catch{}await delay(1000);}throw new Error('gate_deadline_'+stage);}
  const receiverMode=value=>write(path.join(artifact,'receiver/receiver-mode'),value);
  const receiverReceipts=()=>fs.existsSync(path.join(artifact,'receiver/receipts.jsonl'))?fs.readFileSync(path.join(artifact,'receiver/receipts.jsonl'),'utf8').trim().split('\n').filter(Boolean).map(JSON.parse):[];
  try{
    for(const dir of ['image/deploy/monitoring/redis-monitor-cloud','sender','receiver'])fs.mkdirSync(path.join(artifact,dir),{recursive:true});
    stageImageContext(repo,artifact);
    certificates(path.join(artifact,'receiver'),repo);
    fs.copyFileSync(path.join(artifact,'receiver/ca.pem'),path.join(artifact,'sender/ca.pem'));
    const secret=()=>randomBytes(48).toString('hex'),monitor=secret(),token=secret();
    write(path.join(artifact,'redis.env'),`FASTTOWIN_RATE_LIMIT_NAMESPACE=fixture_${id}\nFASTTOWIN_REDIS_APP_PASSWORD=${secret()}\nFASTTOWIN_REDIS_MONITOR_PASSWORD=${monitor}\nFASTTOWIN_REDIS_BACKUP_PASSWORD=${secret()}\n`);
    write(path.join(artifact,'collector.env'),`FASTTOWIN_REDIS_MONITOR_ADDRESS=redis:6379\nFASTTOWIN_REDIS_MONITOR_PASSWORD=${monitor}\n`);
    for(const dir of ['sender','receiver'])write(path.join(artifact,dir,'token'),token);
    write(path.join(artifact,'sender/config.alloy'),localAlloy());receiverMode('accept');
    fs.copyFileSync(path.join(repo,'scripts/tests/redis-cloud-receiver.mjs'),path.join(artifact,'receiver/receiver.mjs'));
    fs.copyFileSync(path.join(repo,'scripts/tests/redis-cloud-probe.mjs'),path.join(artifact,'receiver/probe.mjs'));
    provisionDecoders({repo,artifact,mode:resetWal?'fullreset':manualRecovery?'fullrecovery':diagnostic?'waldiagnostic':storage?'storage':'shipping'});
    stage='compose-security';const config=JSON.parse(docker([...compose,'config','--format','json']));
    // Sanitized policy receipt; compose config itself contains synthetic env secrets.
    assert.equal(Object.keys(config.services).length,5);assert.equal(config.networks.fixture.internal,true);
    for(const service of Object.values(config.services)){assert.ok(!service.ports);assert.ok(!service.privileged);assert.ok(!service.network_mode);assert.ok((service.volumes??[]).every(v=>v.target!=='/var/run/docker.sock'));}
    assert.equal(Number(config.services.sender.mem_limit),268435456);assert.equal(Number(config.services.collector.mem_limit),67108864);
    gate('ownership-and-isolation',{localDaemon:true,emptyAuthConfig:true,internalNetwork:true,noHostPorts:true,syntheticCredentials:true});
    stage='build';docker([...compose,'build','--builder','default'],240);
    stage='startup';docker([...compose,'up','-d','--no-build']);
    if(diagnostic||storage)runtimeDeadline=Date.now()+(!fullOnly&&storage?480000:240000);
    const networkName=Object.keys(inspect('container',container('collector')).NetworkSettings.Networks)[0],network=inspect('network',networkName);
    assert.ok(owns(network));assert.equal(network.Internal,true);assert.equal(network.EnableIPv6,true);
    const containers=Object.fromEntries(['redis','collector','sender','receiver','prometheus'].map(s=>[s,container(s)]));
    receipt.resources={containers,network:network.Id,images:Object.fromEntries(Object.entries(containers).map(([s,cid])=>[s,inspect('container',cid).Image]))};save();
    if(diagnostic){
      stage='wal-diagnostic';
      const {runWalDiagnostic}=await import('./redis-cloud-wal-diagnostic.mjs');
      const evidence=await runWalDiagnostic({artifact,docker,containers,query,until,delay:async ms=>{if(Date.now()+ms>runtimeDeadline)throw new Error('diagnostic_runtime_deadline');await delay(ms);},inspect,receiverMode,receiverReceipts,write,deadlineAt:runtimeDeadline});
      receipt.diagnosticConfirmed=evidence.diagnosticConfirmed;receipt.replayAcceptancePassed=false;save();
      throw new Error(evidence.diagnosticConfirmed?'wal_restart_cutoff_diagnostic_confirmed_acceptance_failed':'wal_diagnostic_inconclusive');
    }
    if(storage){
      stage='storage-readiness';
      await until(()=>query('{job="fasttowin-redis-monitor",instance="redis-primary"}').length===22,65);
      const {runStoragePhases}=await import('./redis-cloud-storage.mjs');
      await runStoragePhases({artifact,id,docker,containers,query,until,delay:async ms=>{if(Date.now()+ms>runtimeDeadline)throw new Error('storage_runtime_deadline');await delay(ms);},inspect,owns,receiverMode,receiverReceipts,write,localAlloy,network,config,gate,setStage:value=>{stage=value;},fullOnly,manualRecovery,resetWal,deadlineAt:runtimeDeadline});
      receipt.failedGates=Object.entries(receipt.gates).filter(([,value])=>value.passed===false).map(([name])=>name);
      receipt.gatesPassed=receipt.failedGates.length===0;receipt.completed=false;receipt.scope=resetWal?'local manual monitoring-WAL reset':manualRecovery?'manual filesystem recovery':fullOnly?'filesystem only':'remaining storage only';save();
      if(!receipt.gatesPassed)throw new Error('remaining_storage_gates_failed');
      return;
    }
    stage='healthy-reception';const healthy=await until(()=>{const result=query('{job="fasttowin-redis-monitor",instance="redis-primary"}');return result.length===22&&result.some(s=>s.metric.__name__==='fasttowin_redis_monitor_up'&&s.value[1]==='1')?result:false;},75);
    const reasons=new Set(['connect','timeout','auth','protocol','oversized','missing','invalid']);
    for(const s of healthy){assert.ok(metricNames.includes(s.metric.__name__));assert.equal(s.metric.job,'fasttowin-redis-monitor');assert.equal(s.metric.instance,'redis-primary');assert.ok(Object.keys(s.metric).every(k=>['__name__','job','instance','reason'].includes(k)));if(s.metric.__name__==='fasttowin_redis_monitor_collection_errors_total')assert.ok(reasons.has(s.metric.reason));else assert.equal(s.metric.reason,undefined);}
    assert.equal(healthy.filter(s=>s.metric.__name__==='fasttowin_redis_monitor_collection_errors_total').length,7);
    gate('actual-remote-write',{series:healthy.length,names:[...new Set(healthy.map(s=>s.metric.__name__))].sort(),labelsSanitized:true,adversarialExtraMetricAndReasonDropped:true,successfulRequests:receiverReceipts().filter(r=>r.status===204).length});
    stage='private-ip';const families=probe('network');assert.ok(families.some(x=>x.family===4&&x.status===200));assert.ok(families.some(x=>x.family===6&&x.status===200));gate('private-ipv4-ipv6',{families});
    stage='tls-auth';const tls=probe('tls');assert.equal(tls['wrong-token'],401);assert.equal(tls.hostname,'ERR_TLS_CERT_ALTNAME_INVALID');assert.match(tls.untrusted,/SELF_SIGNED|UNABLE_TO_VERIFY/);gate('tls-and-auth',{results:tls});
    stage='actual-sender-negative';
    for(const failure of ['hostname','untrusted','wrong-token']){
      docker(['stop','-t','10',containers.sender]);
      let candidate=localAlloy();
      if(failure==='hostname')candidate=candidate.replace('ca_file = "/fixture/ca.pem"','ca_file = "/fixture/ca.pem"\n      server_name = "wrong.invalid"');
      if(failure==='untrusted')candidate=candidate.replace('ca_file = "/fixture/ca.pem"','ca_file = "/etc/ssl/certs/ca-certificates.crt"');
      write(path.join(artifact,'sender/config.alloy'),candidate);
      if(failure==='wrong-token')write(path.join(artifact,'sender/token'),'deliberately-wrong-synthetic');
      const since=Date.now();docker(['start',containers.sender]);
      if(failure==='wrong-token')await until(()=>receiverReceipts().some(r=>r.at>since&&r.status===401),40);
      else await until(()=>{const logs=docker(['logs','--since',new Date(since).toISOString(),containers.sender]);return /x509:/.test(logs)&&(failure==='hostname'?/wrong.invalid/.test(logs):/unknown authority/.test(logs));},40);
      assert.equal(receiverReceipts().filter(r=>r.at>since&&r.status===204).length,0);
      gate('actual-sender-'+failure+'-rejection',{senderErrorObserved:true,noAcceptedRequests:true,fallbackUsed:false});
    }
    docker(['stop','-t','10',containers.sender]);write(path.join(artifact,'sender/config.alloy'),localAlloy());write(path.join(artifact,'sender/token'),token);docker(['start',containers.sender]);await until(()=>query('time() - timestamp(up{job="fasttowin-redis-monitor",instance="redis-primary"})')[0]?.value[1]<30,45);
    stage='cadence';const cadenceStart=Date.now();await delay(31000);
    const scrapes=fs.readFileSync(path.join(artifact,'receiver/scrapes.jsonl'),'utf8').trim().split('\n').map(JSON.parse),intervals=scrapes.slice(1).map((s,i)=>s.at-scrapes[i].at);
    const stableIntervals=scrapes.filter(s=>s.at>=cadenceStart).slice(1).map((s,i)=>s.at-scrapes.filter(s=>s.at>=cadenceStart)[i].at);
    assert.ok(stableIntervals.length>=1&&stableIntervals.every(ms=>ms>=14000&&ms<=16000));gate('single-15-second-cadence',{intervalsMs:stableIntervals});
    stage='redirect';const redirectStart=Date.now();receiverMode('redirect');await until(()=>receiverReceipts().some(r=>r.at>=redirectStart&&r.status===307),30);await delay(2000);assert.ok(!receiverReceipts().some(r=>r.at>=redirectStart&&r.status===404));receiverMode('accept');gate('sender-redirect-rejection',{received307:true,followedRedirect:false});
    stage='replay';receiverMode('unavailable');const outage=Date.now();await delay(46000);
    docker(['stop','-t','10',containers.sender]);const recoveredAt=Date.now();receiverMode('accept');docker(['start',containers.sender]);
    const restartedSamples=await until(()=>{const values=query('fasttowin_redis_monitor_memory_used_bytes{job="fasttowin-redis-monitor",instance="redis-primary"}[4m]')[0]?.values??[];return values.some(([t])=>t*1000>=recoveredAt)?values:false;},60);
    gate('sender-restart-gap-contract',{...restartGapEvidence({restart:recoveredAt,now:Date.now(),receivedTimes:restartedSamples.map(([t])=>t*1000)}),outageStart:outage,senderContainerUnchanged:true});
    stage='sender-death-freshness';docker(['stop','-t','10',containers.sender]);await delay(65000);
    const raw=query('fasttowin_redis_monitor_memory_used_bytes{job="fasttowin-redis-monitor",instance="redis-primary"}');assert.equal(raw.length,1);
    const current=query('(fasttowin_redis_monitor_memory_used_bytes{job="fasttowin-redis-monitor",instance="redis-primary"}) and on(job,instance) ((time() - timestamp(up{job="fasttowin-redis-monitor",instance="redis-primary"})) >= 0 and (time() - timestamp(up{job="fasttowin-redis-monitor",instance="redis-primary"})) <= 60)');assert.equal(current.length,0);
    docker(['start',containers.sender]);await until(()=>query('time() - timestamp(up{job="fasttowin-redis-monitor",instance="redis-primary"})')[0]?.value[1]<30);gate('sender-death-freshness-and-recovery',{oldLookbackSeries:raw.length,currentSeriesAfter65s:current.length,recovered:true});
    stage='collector-death';docker(['stop','-t','10',containers.collector]);await until(()=>query('up{job="fasttowin-redis-monitor",instance="redis-primary"}')[0]?.value[1]==='0',40);docker(['start',containers.collector]);await until(()=>query('up{job="fasttowin-redis-monitor",instance="redis-primary"}')[0]?.value[1]==='1',40);gate('collector-death-and-recovery',{scrapeUpZero:true,recovered:true});
    stage='resources';const stats=JSON.parse('['+docker(['stats','--no-stream','--format','{{json .}}',containers.sender,containers.collector]).split(/\r?\n/).join(',')+']');
    const states=Object.fromEntries(['sender','collector'].map(s=>{const c=inspect('container',containers[s]);assert.equal(c.HostConfig.NanoCpus,100000000);assert.equal(c.State.OOMKilled,false);return [s,{memoryCap:c.HostConfig.Memory,nanoCPUs:c.HostConfig.NanoCpus,oomKilled:c.State.OOMKilled}];}));
    const wal=JSON.parse(docker(['exec',containers.sender,'node','--input-type=module','-e','import fs from "node:fs";let bytes=0,files=0;function walk(p){for(const d of fs.readdirSync(p,{withFileTypes:true})){const f=p+"/"+d.name;if(d.isDirectory())walk(f);else{files++;bytes+=fs.statSync(f).size;}}}walk("/var/lib/alloy");console.log(JSON.stringify({bytes,files}));']));
    const accepted=receiverReceipts().filter(r=>r.status===204),duration=(accepted.at(-1).at-accepted[0].at)/1000,compressedBytes=accepted.reduce((n,r)=>n+r.bytes,0);
    gate('resource-observation',{stats,states,wal,compressedAcceptedBytes:compressedBytes,observedSeconds:duration,compressedGiBPer30Days:compressedBytes/duration*2592000/1073741824,series:22,pointsPerMinute:88,billingProjectionEstablished:false});
    const {runStoragePhases}=await import('./redis-cloud-storage.mjs');
    await runStoragePhases({artifact,id,docker,containers,query,until,delay,inspect,owns,receiverMode,receiverReceipts,write,localAlloy,network,config,gate,setStage:value=>{stage=value;}});
    receipt.failedGates=Object.entries(receipt.gates).filter(([,value])=>value.passed===false).map(([name])=>name);receipt.gatesPassed=receipt.failedGates.length===0;receipt.completed=false;receipt.finishedAt=new Date().toISOString();save();
    if(!receipt.gatesPassed)throw new Error('local_acceptance_gates_failed');
  }catch(error){receipt.completed=false;receipt.failedStage=stage;receipt.error=error.message;save();throw error;}
  finally{
    runtimeDeadline=Infinity;
    const actions=[],checks=[];
    // Enumerate only this run; every action is independent so one failure cannot
    // skip later resources or secrets. Checks include partially built images.
    for(const [kind,list] of [['container',['ps','-aq']],['volume',['volume','ls','-q']],['network',['network','ls','-q']],['image',['image','ls','-q']]]){
      const args=[...list,'--filter','label=fasttowin.redis-cloud-run='+id];
      let items=[];
      try{items=[...new Set(docker(args).split(/\r?\n/).filter(Boolean))];}
      catch{actions.push({name:'discover-'+kind,run:()=>{throw new Error('discovery_failed');}});}
      for(const item of items)actions.push({name:kind+':'+item,run:()=>{
        const object=inspect(kind,item);assert.ok(owns(kind==='container'||kind==='image'?object.Config:object));
        docker(kind==='container'?['rm','-f',item]:[kind,'rm',item]);
      }});
      checks.push({name:kind,empty:()=>docker(args)===''});
    }
    for(const relative of ['redis.env','collector.env','sender/token','receiver/token','receiver/key.pem']){
      const file=path.join(artifact,relative);
      actions.push({name:'secret:'+relative,run:()=>{if(fs.existsSync(file))fs.unlinkSync(file);}});
      checks.push({name:'secret:'+relative,empty:()=>!fs.existsSync(file)});
    }
    const walCopy=path.join(artifact,'wal-copy');
    actions.push({name:'diagnostic-wal-copy',run:()=>{if(fs.existsSync(walCopy))fs.rmSync(walCopy,{recursive:true});}});
    checks.push({name:'diagnostic-wal-copy',empty:()=>!fs.existsSync(walCopy)});
    finalizeCleanup(receipt,{actions,checks,persistCleanup:value=>write(path.join(artifact,'cleanup.json'),value),persistAcceptance:save});
  }
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
  const [mode,repo,id]=process.argv.slice(2);
  try{if(mode==='ui'){const {nativeCloudUI}=await import('./redis-cloud-ui.mjs');await nativeCloudUI(path.resolve(repo),ownedPath(repo,id));}else await shipping(path.resolve(repo),id,fixtureModeOptions(mode));}
  catch(error){console.error('local_cloud_fixture_failed: '+error.message);process.exitCode=1;}
}
