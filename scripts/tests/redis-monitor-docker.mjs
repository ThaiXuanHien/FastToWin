// Opt-in real Redis acceptance. Never repairs/launches Docker Desktop or touches shared resources.
import fs from 'node:fs';
import path from 'node:path';
import cp from 'node:child_process';
import {dockerConfig} from './redis-monitor-docker-config.mjs';
import {randomBytes} from 'node:crypto';
import assert from 'node:assert/strict';
const [artifactArg,repoArg,id]=process.argv.slice(2);
const artifact=path.resolve(artifactArg),repo=path.resolve(repoArg);
const owned=path.join(repo,'.artifacts/redis-monitor-local-20261103')+path.sep;
if(!/^[a-f0-9]{32}$/.test(id)||!artifact.startsWith(owned)||path.basename(artifact)!=='run-'+id)throw new Error('invalid owned resource path');
const secret=()=>randomBytes(48).toString('hex'),monitor=secret(),app=secret(),backup=secret(),namespace='fixture_'+id;
const env={...process.env,FTW_MONITOR_RUN_ID:id,FTW_MONITOR_ARTIFACT:artifact,FTW_MONITOR_CONTEXT:path.join(artifact,'image')};
// Local named pipe and an empty owned Docker config prevent remote contexts,
// credential helpers, private registry credentials and cloud builders.
for(const k of Object.keys(env))if(k.startsWith('DOCKER_')||k.startsWith('BUILDX_')||k.startsWith('BUILDKIT_'))delete env[k];
env.DOCKER_HOST='npipe:////./pipe/docker_engine';
env.DOCKER_CONFIG=path.join(artifact,'docker-config');
const dockerExecutable=cp.spawnSync('where.exe',['docker'],{encoding:'utf8',windowsHide:true}).stdout.trim().split(/\r?\n/)[0];
assert.ok(path.isAbsolute(dockerExecutable));
fs.mkdirSync(env.DOCKER_CONFIG);fs.writeFileSync(path.join(env.DOCKER_CONFIG,'config.json'),JSON.stringify(dockerConfig(dockerExecutable))+'\n');
const compose=['compose','-f',path.join(repo,'deploy/monitoring/redis-monitor/compose.local.yaml')];
function docker(args){const r=cp.spawnSync(dockerExecutable,args,{env,encoding:'utf8',windowsHide:true});if(r.status!==0){const error=new Error('docker_fixture_command_failed');error.commandResult={status:r.status,signal:r.signal,errorCode:r.error?.code,stdoutBytes:Buffer.byteLength(r.stdout??''),stderrBytes:Buffer.byteLength(r.stderr??'')};error.diagnostic=(r.stderr??'').replace(/[a-f0-9]{96}/g,'[synthetic-secret-redacted]').split(/\r?\n/).slice(-4).join('\n').slice(-1600);throw error;}return r.stdout.trim();}
const ownedLabel=x=>x?.Labels?.['fasttowin.monitor-run']===id;
const inspect=(type,x)=>JSON.parse(docker([type,'inspect',x]))[0];
const container=s=>{const x=docker([...compose,'ps','-aq',s]);assert.match(x,/^[a-f0-9]{64}$/);const c=inspect('container',x);assert.ok(ownedLabel(c.Config));return x;};
const probeIds=[];
function probe(phase){
  const c=inspect('container',container('collector')),networks=Object.keys(c.NetworkSettings.Networks);assert.equal(networks.length,1);
  const network=inspect('network',networks[0]);assert.ok(ownedLabel(network));assert.equal(network.Internal,true);
  const mounts=[['redis-monitor-runtime-probe','/probe'],['monitor','/run/secrets/monitor'],['app','/run/secrets/app']].flatMap(([file,target])=>['--mount',`type=bind,source=${file==='redis-monitor-runtime-probe'?path.join(owned,file):path.join(artifact,file)},target=${target},readonly`]);
  const x=docker(['create','--name',`ftw-monitor-probe-${id}-${phase}`,'--label','fasttowin.monitor-run='+id,'--network',network.Id,'--user','65532:65532','--cpus','0.1','--memory','32m','--pids-limit','32','--cap-drop','ALL','--security-opt','no-new-privileges:true','--read-only',...mounts,'--entrypoint','/probe','gcr.io/distroless/static-debian13@sha256:25ac8f0c3b2334a9eb05452266bd4697864a8c8ef0ef71e5e6480d7f92868329',phase,namespace]);
  assert.match(x,/^[a-f0-9]{64}$/);assert.ok(ownedLabel(inspect('container',x).Config));probeIds.push(x);
  const result=JSON.parse(docker(['start','-a',x]));assert.equal(inspect('container',x).State.ExitCode,0);return result;
}
let started=false,stage='prepare';const evidence={realRedis:false,acl:false,aof:false,restart:false,resourceCap:false,physicalDiskFailure:false};
try{
  fs.writeFileSync(path.join(artifact,'redis.env'),`FASTTOWIN_RATE_LIMIT_NAMESPACE=${namespace}\nFASTTOWIN_REDIS_APP_PASSWORD=${app}\nFASTTOWIN_REDIS_MONITOR_PASSWORD=${monitor}\nFASTTOWIN_REDIS_BACKUP_PASSWORD=${backup}\n`);
  fs.writeFileSync(path.join(artifact,'monitor'),monitor);fs.writeFileSync(path.join(artifact,'app'),app);fs.writeFileSync(path.join(artifact,'grafana'),secret());
  fs.mkdirSync(env.FTW_MONITOR_CONTEXT);fs.copyFileSync(path.join(repo,'deploy/monitoring/redis-monitor/Dockerfile'),path.join(env.FTW_MONITOR_CONTEXT,'Dockerfile'));fs.copyFileSync(path.join(repo,'.artifacts/redis-monitor-local-20261103/redis-monitor-linux-amd64'),path.join(env.FTW_MONITOR_CONTEXT,'redis-monitor'));
  // No daemon repair, credential helper or private registry operation.
  stage='compose_config';docker([...compose,'config','--quiet']);started=true;stage='compose_up';docker([...compose,'up','-d','--build']);
  stage='service_states';evidence.services={};for(const service of ['redis','collector','prometheus','alertmanager','grafana']){const state=inspect('container',container(service)).State;assert.equal(state.Running,true);evidence.services[service]='running';}
  stage='internal_healthy_probe';evidence.healthy=probe('healthy');evidence.realRedis=true;evidence.acl=true;evidence.aof=true;
  const redis=container('redis');stage='redis_down';docker(['stop',redis]);evidence.down=probe('down');stage='redis_recovery';docker(['start',redis]);evidence.recovery=probe('recovery');evidence.restart=true;
  evidence.evictedSourceCounterAfterRestart=evidence.recovery.metrics.evicted_keys_total;
  stage='resource_caps';const c=inspect('container',container('collector'));assert.equal(c.HostConfig.Memory,67108864);assert.equal(c.HostConfig.NanoCpus,100000000);assert.equal(c.State.OOMKilled,false);assert.equal(c.RestartCount,0);evidence.caps={memoryBytes:c.HostConfig.Memory,nanoCpus:c.HostConfig.NanoCpus,oomKilled:c.State.OOMKilled,restartCount:c.RestartCount};
  evidence.stats=docker(['stats','--no-stream','--format','{{.CPUPerc}} {{.MemUsage}}',container('collector')]);evidence.resourceCap=true;
}catch(error){
  evidence.error='local_acceptance_incomplete';evidence.failedStage=stage;process.exitCode=1;
  if(error.diagnostic)evidence.dockerDiagnostic=error.diagnostic;
  if(error.commandResult)evidence.commandResult=error.commandResult;
  if(started)for(const service of ['redis','collector'])try{
    const x=container(service),c=inspect('container',x);
    evidence[service+'State']={running:c.State.Running,exitCode:c.State.ExitCode,oomKilled:c.State.OOMKilled};
    if(service==='collector')evidence.collectorEvents=docker(['logs',x]).split(/\r?\n/).filter(x=>/^(configuration_failed|service_failed|collection_failed|collection_succeeded|server_started|server_stopped|shutdown_started|shutdown_complete)$/.test(x));
  }catch{}
}
finally{
  const cleanup={containers:[],networks:[],volumes:[],images:[],secretsRemoved:false};
  if(started){
    // Match exact owned IDs and labels before each removal. Never compose down/prune.
    try{for(const x of [...probeIds,...docker([...compose,'ps','-aq']).split(/\r?\n/).filter(Boolean)]){assert.match(x,/^[a-f0-9]{64}$/);const c=inspect('container',x);assert.ok(ownedLabel(c.Config));docker(['rm','-f',x]);cleanup.containers.push(x);}}catch{cleanup.containerCleanupIncomplete=true;}
    for(const type of ['network','volume','image'])try{
      const ids=docker([type,'ls','-q','--filter','label=fasttowin.monitor-run='+id]).split(/\r?\n/).filter(Boolean);
      for(const x of ids){const r=inspect(type,x);assert.ok(ownedLabel(type==='image'?r.Config:r));docker([type,'rm',x]);cleanup[type==='image'?'images':type+'s'].push(x);}
    }catch{cleanup[type+'CleanupIncomplete']=true;}
  }
  assert.ok(artifact.startsWith(owned)&&path.basename(artifact)==='run-'+id);
  for(const f of ['redis.env','monitor','app','grafana'])if(fs.existsSync(path.join(artifact,f)))fs.unlinkSync(path.join(artifact,f));cleanup.secretsRemoved=true;
  fs.writeFileSync(path.join(artifact,'docker-result.json'),JSON.stringify({evidence,cleanup},null,2)+'\n');console.log(JSON.stringify({artifact,evidence,cleanup}));
}
