// Opt-in real Redis acceptance. Never repairs/launches Docker Desktop or touches shared resources.
import fs from 'node:fs';
import path from 'node:path';
import cp from 'node:child_process';
import net from 'node:net';
import {randomBytes,randomUUID} from 'node:crypto';
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
fs.mkdirSync(env.DOCKER_CONFIG);fs.writeFileSync(path.join(env.DOCKER_CONFIG,'config.json'),'{"auths":{}}\n');
const compose=['compose','-f',path.join(repo,'deploy/monitoring/redis-monitor/compose.local.yaml')];
function docker(args){const r=cp.spawnSync('docker',args,{env,encoding:'utf8',windowsHide:true});if(r.status!==0)throw new Error('docker_fixture_command_failed');return r.stdout.trim();}
const ownedLabel=x=>x?.Labels?.['fasttowin.monitor-run']===id;
const inspect=(type,x)=>JSON.parse(docker([type,'inspect',x]))[0];
const container=s=>{const x=docker([...compose,'ps','-aq',s]);assert.match(x,/^[a-f0-9]{64}$/);const c=inspect('container',x);assert.ok(ownedLabel(c.Config));return x;};
const port=(s,p)=>{const v=docker(['port',container(s),String(p)]);assert.match(v,/^127\.0\.0\.1:\d+$/);return Number(v.split(':')[1]);};
const delay=ms=>new Promise(r=>setTimeout(r,ms));
async function waitFor(fn){for(let i=0;i<90;i++){try{if(await fn())return;}catch{}await delay(500);}throw new Error('fixture_deadline');}
const frame=a=>'*'+a.length+'\r\n'+a.map(x=>'$'+Buffer.byteLength(x)+'\r\n'+x+'\r\n').join('');
async function command(p,user,password,args){
  const socket=net.connect(p,'127.0.0.1');socket.setTimeout(3000,()=>socket.destroy());let b='',step=0;
  return await new Promise((resolve,reject)=>{
    socket.on('error',()=>reject(new Error('probe_connection_failed')));socket.on('connect',()=>socket.write(frame(['AUTH',user,password])));
    socket.on('data',chunk=>{b+=chunk;if(b.length>1048576){socket.destroy();reject(new Error('probe_oversized'));return;}
      if(step===0){if(!b.startsWith('+OK\r\n')){socket.destroy();reject(new Error('probe_auth_failed'));return;}b=b.slice(5);step=1;socket.write(frame(args));}
      if(step===1&&b.includes('\r\n')){if(b.startsWith('$')){const n=Number(b.slice(1,b.indexOf('\r\n')));if(b.length<b.indexOf('\r\n')+2+n+2)return;}socket.end();resolve(b);}
    });socket.on('close',()=>{if(!b)reject(new Error('probe_closed'));});
  });
}
let started=false;const evidence={realRedis:false,acl:false,aof:false,restart:false,resourceCap:false,physicalDiskFailure:false};
try{
  fs.writeFileSync(path.join(artifact,'redis.env'),`FASTTOWIN_RATE_LIMIT_NAMESPACE=${namespace}\nFASTTOWIN_REDIS_APP_PASSWORD=${app}\nFASTTOWIN_REDIS_MONITOR_PASSWORD=${monitor}\nFASTTOWIN_REDIS_BACKUP_PASSWORD=${backup}\n`);
  fs.writeFileSync(path.join(artifact,'monitor'),monitor);fs.writeFileSync(path.join(artifact,'grafana'),secret());
  fs.mkdirSync(env.FTW_MONITOR_CONTEXT);fs.copyFileSync(path.join(repo,'deploy/monitoring/redis-monitor/Dockerfile'),path.join(env.FTW_MONITOR_CONTEXT,'Dockerfile'));fs.copyFileSync(path.join(repo,'.artifacts/redis-monitor-local-20261103/redis-monitor-linux-amd64'),path.join(env.FTW_MONITOR_CONTEXT,'redis-monitor'));
  // No daemon repair, credential helper or private registry operation.
  docker([...compose,'config','--quiet']);started=true;docker([...compose,'up','-d','--build']);
  const collector='http://127.0.0.1:'+port('collector',19121),rp=port('redis',6379);
  await waitFor(async()=> (await fetch(collector+'/ready')).ok);evidence.realRedis=true;
  assert.equal(await command(rp,'ftw_monitor',monitor,['PING']),'+PONG\r\n');
  assert.match(await command(rp,'ftw_monitor',monitor,['INFO','memory','persistence','stats']),/aof_enabled:1/);
  for(const args of [['GET','fixture-denied'],['HSET','fixture-denied','field','value'],['SCAN','0'],['CONFIG','GET','maxmemory'],['FLUSHDB']])assert.match(await command(rp,'ftw_monitor',monitor,args),/^-NOPERM/);
  evidence.acl=true;
  const before=await (await fetch(collector+'/metrics')).text();assert.match(before,/fasttowin_redis_monitor_aof_enabled 1/);assert.match(before,/fasttowin_redis_monitor_aof_current_size_bytes \d+/);
  const key=`ftw:${namespace}:rl:v1:fixture-${randomUUID()}`;
  for(let i=0;i<100;i++)assert.match(await command(rp,'ftw_app',app,['HSET',key,String(i),'f'.repeat(2048)]),/^:1/);
  await waitFor(async()=>{const m=await(await fetch(collector+'/metrics')).text();return Number(/aof_current_size_bytes (\d+)/.exec(m)?.[1])>Number(/aof_current_size_bytes (\d+)/.exec(before)?.[1]);});
  evidence.aof=true;
  const redis=container('redis');docker(['stop',redis]);await waitFor(async()=> (await fetch(collector+'/ready')).status===503);docker(['start',redis]);await waitFor(async()=> (await fetch(collector+'/ready')).ok);evidence.restart=true;
  const m=await(await fetch(collector+'/metrics')).text();evidence.evictedSourceCounterAfterRestart=Number(/evicted_keys_total (\d+)/.exec(m)?.[1]);
  const c=inspect('container',container('collector'));assert.equal(c.HostConfig.Memory,67108864);assert.equal(c.HostConfig.NanoCpus,100000000);
  evidence.stats=docker(['stats','--no-stream','--format','{{.CPUPerc}} {{.MemUsage}}',container('collector')]);evidence.resourceCap=true;
}catch{evidence.error='local_acceptance_incomplete';process.exitCode=1;}
finally{
  const cleanup={containers:[],networks:[],volumes:[],images:[],secretsRemoved:false};
  if(started){
    // Match exact owned IDs and labels before each removal. Never compose down/prune.
    try{for(const x of docker([...compose,'ps','-aq']).split(/\r?\n/).filter(Boolean)){assert.match(x,/^[a-f0-9]{64}$/);const c=inspect('container',x);assert.ok(ownedLabel(c.Config));docker(['rm','-f',x]);cleanup.containers.push(x);}}catch{cleanup.containerCleanupIncomplete=true;}
    for(const type of ['network','volume','image'])try{
      const ids=docker([type,'ls','-q','--filter','label=fasttowin.monitor-run='+id]).split(/\r?\n/).filter(Boolean);
      for(const x of ids){const r=inspect(type,x);assert.ok(ownedLabel(type==='image'?r.Config:r));docker([type,'rm',x]);cleanup[type==='image'?'images':type+'s'].push(x);}
    }catch{cleanup[type+'CleanupIncomplete']=true;}
  }
  assert.ok(artifact.startsWith(owned)&&path.basename(artifact)==='run-'+id);
  for(const f of ['redis.env','monitor','grafana'])if(fs.existsSync(path.join(artifact,f)))fs.unlinkSync(path.join(artifact,f));cleanup.secretsRemoved=true;
  fs.writeFileSync(path.join(artifact,'docker-result.json'),JSON.stringify({evidence,cleanup},null,2)+'\n');console.log(JSON.stringify({artifact,evidence,cleanup}));
}
