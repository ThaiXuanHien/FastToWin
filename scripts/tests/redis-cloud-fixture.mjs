// Bounded opt-in local shipping acceptance; never uses production credentials.
import fs from 'node:fs';
import path from 'node:path';
import cp from 'node:child_process';
import {randomBytes} from 'node:crypto';
import assert from 'node:assert/strict';
import {fileURLToPath} from 'node:url';
import {renderAlloy,metricNames} from '../../deploy/monitoring/redis-monitor-cloud/prepare.mjs';
import {dockerConfig} from './redis-monitor-docker-config.mjs';
export function ownedPath(repo,id){
  if(!/^[a-f0-9]{32}$/.test(id))throw new Error('invalid fixture identity');
  return path.join(path.resolve(repo),'.artifacts/redis-cloud-local-20261103','run-'+id);
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
async function shipping(repo,id){
  const artifact=ownedPath(repo,id);assert.ok(fs.existsSync(artifact));
  const env={...process.env};for(const k of Object.keys(env))if(/^(DOCKER_|BUILDX_|BUILDKIT_)/.test(k))delete env[k];
  env.DOCKER_HOST='npipe:////./pipe/docker_engine';env.DOCKER_CONFIG=path.join(artifact,'docker-config');
  env.FTW_CLOUD_RUN_ID=id;env.FTW_CLOUD_ARTIFACT=artifact;env.FTW_CLOUD_IPV6_SUBNET='fd43:'+id.slice(0,4)+':'+id.slice(4,8)+'::/64';
  const executable=cp.spawnSync('where.exe',['docker'],{encoding:'utf8',windowsHide:true}).stdout.trim().split(/\r?\n/)[0];assert.ok(path.isAbsolute(executable));
  fs.mkdirSync(env.DOCKER_CONFIG);write(path.join(env.DOCKER_CONFIG,'config.json'),dockerConfig(executable));
  const compose=['compose','-f',path.join(repo,'deploy/monitoring/redis-monitor-cloud/compose.local.yaml')];
  const commands=[],extraContainers=[];let stage='prepare',started=false;
  const receipt={id,artifact,startedAt:new Date().toISOString(),gates:{},limitations:['Local fixture receipt is not Grafana Cloud acceptance.','Named Docker volume has no portable hard 1-GB quota; physical disk durability is untested.']};
  function save(){write(path.join(artifact,'acceptance.json'),receipt);write(path.join(artifact,'commands.json'),commands);}
  function docker(args,seconds=90){
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
  async function until(fn,seconds=45){const end=Date.now()+seconds*1000;while(Date.now()<end){try{const value=fn();if(value)return value;}catch{}await delay(1000);}throw new Error('gate_deadline_'+stage);}
  const receiverMode=value=>write(path.join(artifact,'receiver/receiver-mode'),value);
  const receiverReceipts=()=>fs.existsSync(path.join(artifact,'receiver/receipts.jsonl'))?fs.readFileSync(path.join(artifact,'receiver/receipts.jsonl'),'utf8').trim().split('\n').filter(Boolean).map(JSON.parse):[];
  try{
    for(const dir of ['image/deploy/monitoring/redis-monitor-cloud','sender','receiver'])fs.mkdirSync(path.join(artifact,dir),{recursive:true});
    const source=path.join(repo,'deploy/monitoring/redis-monitor-cloud'),target=path.join(artifact,'image/deploy/monitoring/redis-monitor-cloud');
    for(const f of ['Dockerfile.collector','Dockerfile.sender','prepare.mjs','prepare-runtime.mjs','collector-entrypoint.sh','sender-entrypoint.sh'])fs.copyFileSync(path.join(source,f),path.join(target,f));
    fs.copyFileSync(path.join(repo,'.artifacts/redis-monitor-local-20261103/redis-monitor-linux-amd64'),path.join(artifact,'image/redis-monitor'));
    certificates(path.join(artifact,'receiver'),repo);
    fs.copyFileSync(path.join(artifact,'receiver/ca.pem'),path.join(artifact,'sender/ca.pem'));
    const secret=()=>randomBytes(48).toString('hex'),monitor=secret(),token=secret();
    write(path.join(artifact,'redis.env'),`FASTTOWIN_RATE_LIMIT_NAMESPACE=fixture_${id}\nFASTTOWIN_REDIS_APP_PASSWORD=${secret()}\nFASTTOWIN_REDIS_MONITOR_PASSWORD=${monitor}\nFASTTOWIN_REDIS_BACKUP_PASSWORD=${secret()}\n`);
    write(path.join(artifact,'collector.env'),`FASTTOWIN_REDIS_MONITOR_ADDRESS=redis:6379\nFASTTOWIN_REDIS_MONITOR_PASSWORD=${monitor}\n`);
    for(const dir of ['sender','receiver'])write(path.join(artifact,dir,'token'),token);
    write(path.join(artifact,'sender/config.alloy'),localAlloy());receiverMode('accept');
    fs.copyFileSync(path.join(repo,'scripts/tests/redis-cloud-receiver.mjs'),path.join(artifact,'receiver/receiver.mjs'));
    fs.copyFileSync(path.join(repo,'scripts/tests/redis-cloud-probe.mjs'),path.join(artifact,'receiver/probe.mjs'));
    stage='compose-security';const config=JSON.parse(docker([...compose,'config','--format','json']));
    // Sanitized policy receipt; compose config itself contains synthetic env secrets.
    assert.equal(Object.keys(config.services).length,5);assert.equal(config.networks.fixture.internal,true);
    for(const service of Object.values(config.services)){assert.ok(!service.ports);assert.ok(!service.privileged);assert.ok(!service.network_mode);assert.ok((service.volumes??[]).every(v=>v.target!=='/var/run/docker.sock'));}
    assert.equal(Number(config.services.sender.mem_limit),268435456);assert.equal(Number(config.services.collector.mem_limit),67108864);
    gate('ownership-and-isolation',{localDaemon:true,emptyAuthConfig:true,internalNetwork:true,noHostPorts:true,syntheticCredentials:true});
    stage='build';docker([...compose,'build','--builder','default'],240);started=true;
    stage='startup';docker([...compose,'up','-d','--no-build']);
    const networkName=Object.keys(inspect('container',container('collector')).NetworkSettings.Networks)[0],network=inspect('network',networkName);
    assert.ok(owns(network));assert.equal(network.Internal,true);assert.equal(network.EnableIPv6,true);
    const containers=Object.fromEntries(['redis','collector','sender','receiver','prometheus'].map(s=>[s,container(s)]));
    receipt.resources={containers,network:network.Id,images:Object.fromEntries(Object.entries(containers).map(([s,cid])=>[s,inspect('container',cid).Image]))};save();
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
    let replay=[],replayPassed=true;
    try{replay=await until(()=>{const series=query('fasttowin_redis_monitor_memory_used_bytes{job="fasttowin-redis-monitor",instance="redis-primary"}[4m]');const values=series[0]?.values??[];write(path.join(artifact,'replay-observed.json'),{outage,recoveredAt,values});return values.some(([t])=>t*1000>=outage&&t*1000<recoveredAt-10000)?values:false;},60);}catch{replayPassed=false;}
    const replayTimes=replay.map(([t])=>t*1000).filter(t=>t>=outage&&t<recoveredAt);replayPassed=replayPassed&&replayTimes.length>=2;
    gate('persistent-wal-restart-backfill',{passed:replayPassed,outageStart:outage,recoveredAt,preservedOriginalSampleTimes:replayTimes,senderContainerUnchanged:true});
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
    // Accelerated retention is a separate fixture configuration, never production.
    stage='accelerated-retention';docker(['stop','-t','10',containers.sender]);write(path.join(artifact,'sender/config.alloy'),localAlloy({accelerated:true}));receiverMode('unavailable');docker(['start',containers.sender]);const expiryStart=Date.now();await delay(85000);receiverMode('accept');const expiryRecovery=Date.now();
    await until(()=>receiverReceipts().some(r=>r.at>expiryRecovery&&r.status===204),40);
    const expirySamples=query('fasttowin_redis_monitor_memory_used_bytes{job="fasttowin-redis-monitor",instance="redis-primary"}[4m]')[0]?.values??[];
    const expiredWindow=expirySamples.filter(([t])=>t*1000>expiryStart+15000&&t*1000<expiryRecovery-45000);
    gate('accelerated-retention-expiry',{passed:expiredWindow.length===0,truncate:'10s',minimum:'10s',maximum:'30s',outageMs:expiryRecovery-expiryStart,expiredWindowSamples:expiredWindow.length,productionUnchanged:'5m/5m/2h',twoHourWallClockTest:false});
    // Bounded writable tmpfs models full filesystem behavior, not physical durability.
    stage='full-filesystem';docker(['stop','-t','10',containers.sender]);
    const fullName='ftw-cloud-full-'+id;
    const full=docker(['create','--name',fullName,'--label','fasttowin.redis-cloud-run='+id,'--network',network.Id,'--user','65532:65532','--cpus','0.1','--memory','256m','--pids-limit','64','--cap-drop','ALL','--security-opt','no-new-privileges:true','--read-only','--tmpfs','/var/lib/alloy:uid=65532,gid=65532,mode=0700,size=192m','--mount',`type=bind,source=${path.join(artifact,'sender')},target=/fixture,readonly`,'--mount',`type=bind,source=${path.join(artifact,'sender/token')},target=/run/secrets/grafana-write,readonly`,'--entrypoint','/bin/alloy',config.services.sender.image,'run','--disable-reporting','--server.http.listen-addr=127.0.0.1:12345','--storage.path=/var/lib/alloy','/fixture/config.alloy']);
    extraContainers.push(full);assert.ok(owns(inspect('container',full).Config));docker(['start',full]);await delay(16000);
    const fill=JSON.parse(docker(['exec',full,'node','--input-type=module','-e','import fs from "node:fs";const f=fs.openSync("/var/lib/alloy/fixture-full","w");let bytes=0,code="limit-not-hit";try{for(let i=0;i<193;i++){fs.writeSync(f,Buffer.alloc(1048576));bytes+=1048576;}}catch(e){code=e.code;}finally{fs.closeSync(f);}console.log(JSON.stringify({bytes,code}));']));assert.equal(fill.code,'ENOSPC');await delay(20000);
    const fullLogs=docker(['logs','--since','40s',full]);const diskFailureObserved=/no space left|ENOSPC|failed.*(append|write)|error.*(append|write)/i.test(fullLogs);
    docker(['exec',full,'node','-e','require("fs").unlinkSync("/var/lib/alloy/fixture-full")']);const freeAt=Date.now();await until(()=>receiverReceipts().some(r=>r.at>freeAt&&r.status===204),45);
    gate('constrained-filesystem-full-and-recovery',{passed:diskFailureObserved,tmpfsMiB:192,fill,diskFailureObserved,recoveredReceipt:true,physicalDurability:false});
    docker(['stop','-t','10',full]);write(path.join(artifact,'sender/config.alloy'),localAlloy());docker(['start',containers.sender]);
    receipt.failedGates=Object.entries(receipt.gates).filter(([,value])=>value.passed===false).map(([name])=>name);receipt.completed=receipt.failedGates.length===0;receipt.finishedAt=new Date().toISOString();save();
    if(!receipt.completed)throw new Error('local_acceptance_gates_failed');
  }catch(error){receipt.completed=false;receipt.failedStage=stage;receipt.error=error.message;save();throw error;}
  finally{
    const cleanup={at:new Date().toISOString(),containers:[],images:[],secretsRemoved:[],complete:false};
    try{
      for(const cid of extraContainers){assert.ok(owns(inspect('container',cid).Config));docker(['rm','-f',cid]);cleanup.containers.push(cid);}
      if(started){for(const cid of docker([...compose,'ps','-aq']).split(/\r?\n/).filter(Boolean))assert.ok(owns(inspect('container',cid).Config));docker([...compose,'down','--volumes','--remove-orphans']);}
      for(const service of ['redis','collector','sender']){const name='ftw-redis-cloud-'+service+':'+id;try{const image=inspect('image',name);assert.ok(owns(image.Config));docker(['image','rm',name]);cleanup.images.push(image.Id);}catch{}}
      cleanup.complete=docker(['ps','-aq','--filter','label=fasttowin.redis-cloud-run='+id])===''&&docker(['volume','ls','-q','--filter','label=fasttowin.redis-cloud-run='+id])===''&&docker(['network','ls','-q','--filter','label=fasttowin.redis-cloud-run='+id])==='';
    }finally{
      for(const relative of ['redis.env','collector.env','sender/token','receiver/token','receiver/key.pem']){const file=path.join(artifact,relative);if(fs.existsSync(file)){fs.unlinkSync(file);cleanup.secretsRemoved.push(relative);}}
      write(path.join(artifact,'cleanup.json'),cleanup);
    }
  }
}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
  const [mode,repo,id]=process.argv.slice(2);
  try{if(mode==='shipping')await shipping(path.resolve(repo),id);else if(mode==='ui'){const {nativeCloudUI}=await import('./redis-cloud-ui.mjs');await nativeCloudUI(path.resolve(repo),ownedPath(repo,id));}else throw new Error('unknown_fixture_mode');}
  catch(error){console.error('local_cloud_fixture_failed: '+error.message);process.exitCode=1;}
}
