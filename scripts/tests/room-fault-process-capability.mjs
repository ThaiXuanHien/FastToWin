// Local Docker only. The peer probes run over loopback inside isolated fixtures.
import assert from 'node:assert/strict';
import { execFileSync, spawn, spawnSync } from 'node:child_process';
import { randomBytes, randomUUID, createHash } from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { createInterface } from 'node:readline';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';

const root = fileURLToPath(new URL('../../', import.meta.url));
const recipes = path.join(root, 'deploy/room-ownership/supervised');
const id = 'room-proc-' + randomBytes(7).toString('hex');
const label = 'fasttowin.fixture=' + id;
const scratch = path.join(root, '.artifacts', id);
const secret = () => randomBytes(32).toString('hex');
const token = secret(), password = secret();
const expectedJar = '8fff8ae123902032d98a92190b0bff3df87e234f213b25a298f4768c7e40e818';
const frozen = path.join(root,'.artifacts/room-ownership-db-latency-20261110/backend-d4b639e/distribution');
const clients = new Set();
const pilotDB='fasttowin_room_pilot';
const database={FASTTOWIN_ENV:'dev',DATABASE_URL:'jdbc:postgresql://postgres:5432/'+pilotDB,
  DATABASE_PASSWORD:password,DATABASE_USER:pilotDB,DATABASE_POOL_SIZE:'20',
  FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_ID:randomUUID(),FASTTOWIN_ROOM_OWNERSHIP_ACCOUNTS:[randomUUID(),randomUUID()].join(',')};
const receipt={bases:{},images:{},ownership:{},checks:[],sources:{}};
const deadline = Date.now() + 600_000;
const name = role => id + '-' + role;

function docker(args, options = {}) {
  try {
    return execFileSync('docker',args,{encoding:'utf8',stdio:['pipe','pipe','pipe'],timeout:120_000,maxBuffer:8*1024*1024,...options}).trim();
  } catch (e) {
    // Commands may carry synthetic credentials. Never include their argv or stdout.
    throw new Error('local docker ' + args[0] + ' failed (exit ' + e.status + ')' +
      (args[0]==='build' ? '\n' + String(e.stderr).slice(-1600) : ''));
  }
}
function build(role, context) {
  const tag = name(role);
  docker(['build','--pull=false','--network','none','--label',label,'-t',tag,
    '-f',path.join(recipes,role+'.Dockerfile'),context],{timeout:240_000});
  receipt.images[role]=docker(['image','inspect','--format','{{.Id}}',tag]);
  console.log(role+' image '+receipt.images[role]);
}
function start(role, image, env = {}, mount, end = deadline) {
  const n = name(role);
  const settings = {ROOM_FAULT_TOKEN:token,ROOM_FAULT_DEADLINE_MS:String(end),ROOM_FAULT_FIXTURE:id,...env};
  const flags = [];
  if(mount){
    const volume = n+'-data'; docker(['volume','create','--label',label,volume]);
    flags.push('--mount','type=volume,source='+volume+',target='+mount);
  }
  docker(['run','-d','--pull','never','--security-opt','no-new-privileges:true','--cap-drop','ALL',
    '--network',id,'--network-alias',role,'--network-alias',image,'--label',label,'--name',n,...flags,
    ...Object.keys(settings).flatMap(k=>['-e',k]),name(image)],{env:{...process.env,...settings}});
  const config=JSON.parse(docker(['inspect','--format','{{json .HostConfig}}',n]));
  assert.equal(config.Privileged,false);
  assert.equal(config.PidMode,'');
  assert.equal(config.NetworkMode,id);
  assert.deepEqual(config.CapDrop,['ALL']);
  assert.equal(config.CapAdd,null);
  assert(config.SecurityOpt.includes('no-new-privileges:true'));
}
const inside = (role, ...args) => docker(['exec',name(role),...args]);
async function until(probe, description, ms=35_000){
  const end = Date.now()+ms;
  while(Date.now()<end){try {if(await probe())return;}catch{} await delay(100);}
  throw new Error('timed out waiting for '+description);
}
async function control(role,operation,generation=1,leaseMillis=0,request=null){
  const req=request??{fixtureId:id,requestId:randomUUID(),generation,operation,deadlineUnixMillis:Math.min(deadline,Date.now()+45000),leaseMillis};
  // BusyBox nc doesn't interpret HTTP errors; decode the fixed response even for 503.
  const body=JSON.stringify(req);
  const http=new Lines(role,['nc','-w','8','127.0.0.1','8181']);
  try {
    http.send('POST /control HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\nAuthorization: Bearer '+token+
      '\r\nContent-Type: application/json\r\nContent-Length: '+Buffer.byteLength(body)+'\r\n\r\n'+body);
    assert.match(await http.next(),/^HTTP\/1\.1 [0-9]{3}/);
    while((await http.next())!==''){}
    return {result:JSON.parse(await http.next()),req};
  } finally {http.close();}
}
async function operation(role,op,gen,state,lease=0){
  const r=(await control(role,op,gen,lease)).result;
  assert.deepEqual([r.verified,r.failed,r.errorCode,r.state,r.generation],[true,false,'',state,op==='start'?gen+1:gen]);
  return r;
}
class Lines {
  constructor(role,args) {
    this.proc=spawn('docker',['exec','-i',name(role),...args],{stdio:['pipe','pipe','ignore']});
    this.proc.stdin.on('error',()=>{});
    this.queue=[];this.waiters=[];this.closed=false;clients.add(this);
    const lines=createInterface({input:this.proc.stdout,crlfDelay:Infinity});
    lines.on('line',value=>{const waiter=this.waiters.shift();if(waiter)waiter(value);else this.queue.push(value);});
    lines.on('close',()=>{this.closed=true;for(const waiter of this.waiters.splice(0))waiter(undefined);});
  }
  send(str){this.proc.stdin.write(str);}
  async next(ms=4000){
    if(this.queue.length)return this.queue.shift();
    if(this.closed)throw new Error('probe connection closed');
    let callback,timer;
    try{return await new Promise((resolve,reject)=>{
      callback=line=>line===undefined?reject(new Error('probe connection closed')):resolve(line);
      this.waiters.push(callback);timer=setTimeout(()=>reject(new Error('probe response timeout')),ms);
    });}finally{clearTimeout(timer);this.waiters=this.waiters.filter(w=>w!==callback);}
  }
  async blocked(){
    try{await this.next(300);}catch(e){if(e.message==='probe response timeout')return;throw e;}
    throw new Error('operation completed during verified pause');
  }
  close(){this.proc.stdin.end();this.proc.kill();clients.delete(this);}
}
const sql = () => new Lines('pg',['psql','-X','-qAt','-v','ON_ERROR_STOP=1','-U',pilotDB,'-d',pilotDB]);
function ownership(role) {
  const text=inside(role,'sh','-c',`set -eu
    id
    for f in /proc/[0-9]*/status; do
      awk '/^Name:/ {n=$2} /^Tgid:/ {p=$2} /^PPid:/ {pp=$2} /^Uid:/ {u=$2} /^Gid:/ {g=$2} /^State:/ {s=$2} /^CapEff:/ {print p,pp,n,u,g,s,$2}' "$f" || :
    done`);
  const rows=text.split('\n').slice(1).map(l=>l.split(/\s+/));
  const pid1=rows.find(r=>r[0]==='1'), broker=rows.find(r=>r[1]==='1'&&r[2]==='exe');
  assert(broker,'missing generation subreaper');
  assert.equal(inside(role,'readlink','/proc/'+broker[0]+'/exe'),inside(role,'readlink','/proc/1/exe'));
  const workload=rows.find(r=>r[1]===broker[0]);
  assert(workload,'missing workload');
  for(const row of [pid1,broker,workload]){
    assert.notEqual(row[3],'0');assert.equal(row[3],pid1[3]);assert.equal(row[4],pid1[4]);assert.match(row[6],/^0+$/);
  }
  console.log(role+' supervisor/broker/child UID:GID '+pid1[3]+':'+pid1[4]+' effective caps 0; workload '+workload[2]);
  receipt.ownership[role]={uid:Number(pid1[3]),gid:Number(pid1[4]),effectiveCaps:pid1[6],workload:workload[2],
    brokerRssKiB:Number(inside(role,'awk','/^VmRSS:/ {print $2}','/proc/'+broker[0]+'/status'))};
  return workload[0];
}
async function postgres(){
  const conn=sql();
  conn.send('CREATE TABLE cap_marker(id integer PRIMARY KEY,n integer NOT NULL); INSERT INTO cap_marker VALUES(1,0); SELECT pg_backend_pid();\n');
  const pid=await conn.next();
  assert.match(pid,/^[1-9][0-9]*$/);
  const postmaster=ownership('pg');
  const native=inside('pg','sh','-c','cat /proc/'+pid+'/stat /proc/'+postmaster+'/stat').split('\n').map(l=>l.slice(l.lastIndexOf(')')+2).split(' '));
  assert.notEqual(native[0][3],native[1][3],'existing backend must have a separate session');
  await operation('pg','pause',1,'paused',12000);
  const stopped=inside('pg','sh','-c','cat /proc/'+pid+'/stat /proc/'+postmaster+'/stat').split('\n').map(l=>l.slice(l.lastIndexOf(')')+2)[0]);
  assert.deepEqual(stopped,['T','T']);
  conn.send('\\echo dispatch\nUPDATE cap_marker SET n=n+1 WHERE id=1 RETURNING n;\n');
  assert.equal(await conn.next(),'dispatch');
  const fresh=sql();fresh.send('SELECT 42;\n');
  await Promise.all([conn.blocked(),fresh.blocked()]);
  await operation('pg','resume',1,'running');
  assert.equal(await conn.next(),'1');assert.equal(await fresh.next(),'42');
  conn.send("SELECT pg_backend_pid() || ',' || count(*) || ',' || sum(n) FROM cap_marker;\n");
  assert.equal(await conn.next(),pid+',1,1');
  await operation('pg','pause',1,'paused',1800);
  conn.send('SELECT pg_backend_pid();\n');await conn.blocked();assert.equal(await conn.next(5000),pid);
  const expired=(await control('pg','status',0)).result;assert.deepEqual([expired.failed,expired.errorCode,expired.verified,expired.state],[true,'lease_expired',true,'running']);
  console.log('PostgreSQL separate-session worker and root T; established write and new connection blocked 300ms; same session resumed; rows=1 writes=1; lease restored.');
  receipt.checks.push({role:'pg',pausedStates:stopped,separateSessions:true,existingWriteBlockedMs:300,newConnectionBlockedMs:300,sameSession:true,markerRows:1,writes:1,leaseRestored:true,expiryFailed:true});
  conn.close();fresh.close();
}
async function redis(){
  ownership('redis');
  const conn=new Lines('redis',['nc','127.0.0.1','6379']);
  conn.send('AUTH '+password+'\r\nCLIENT ID\r\nSET cap-marker 0\r\n');
  assert.equal(await conn.next(),'+OK');const cid=await conn.next();assert.match(cid,/^:\d+$/);assert.equal(await conn.next(),'+OK');
  await operation('redis','pause',1,'paused',12000);conn.send('INCR cap-marker\r\n');await conn.blocked();
  await operation('redis','resume',1,'running');assert.equal(await conn.next(),':1');
  conn.send('CLIENT ID\r\nGET cap-marker\r\n');assert.equal(await conn.next(),cid);assert.equal(await conn.next(),'$1');assert.equal(await conn.next(),'1');
  await operation('redis','pause',1,'paused',1800);conn.send('CLIENT ID\r\n');await conn.blocked();assert.equal(await conn.next(5000),cid);
  const expired=(await control('redis','status',0)).result;assert.deepEqual([expired.failed,expired.errorCode,expired.state],[true,'lease_expired','running']);
  console.log('Redis established connection blocked 300ms, same client ID and marker=1 on resume; lease restored.');
  receipt.checks.push({role:'redis',blockedMs:300,sameConnection:true,marker:1,leaseRestored:true,expiryFailed:true});
  conn.close();
}
const ready=role=>inside(role,'wget','-T','1','-qO','/dev/null','http://127.0.0.1:8080/ready')==='';
async function backend(){
  const nodes={a:randomUUID(),b:randomUUID()},peerSecret=randomBytes(32).toString('base64');
  for(const [role,peer] of [['a','b'],['b','a']]){
    start(role,'backend',{...database,ROOM_FAULT_ROLE:role,FASTTOWIN_ROOM_OWNERSHIP_MODE:'pilot',
      FASTTOWIN_ROOM_OWNERSHIP_NODE_ID:nodes[role],FASTTOWIN_ROOM_OWNERSHIP_PEER_ID:nodes[peer],
      FASTTOWIN_ROOM_OWNERSHIP_PEER_URL:'http://'+peer+':8080',FASTTOWIN_ROOM_OWNERSHIP_PEER_SECRET:peerSecret});
  }
  await until(()=>ready('a')&&ready('b'),'two pilot JVMs ready');
  const before=ownership('a');assert.match(inside('a','sha256sum','/opt/fasttowin/lib/server.jar'),new RegExp('^'+expectedJar));
  await operation('a','pause',1,'paused',12000);assert.throws(()=>ready('a'));assert(ready('b'));await operation('a','resume',1,'running');await until(()=>ready('a'),'resumed backend');
  const kill=await control('a','kill',1,12000);assert.deepEqual([kill.result.state,kill.result.verified,kill.result.failed],['stopped',true,false]);
  assert.throws(()=>ready('a'));await operation('a','start',1,'running');await until(()=>ready('a'),'restarted backend');
  const replay=await control('a','kill',1,12000,kill.req);assert.deepEqual(replay.result,kill.result);
  const after=ownership('a');assert.notEqual(after,before);await operation('a','status',2,'running');assert(ready('a'));
  await operation('a','pause',2,'paused',1800);await until(()=>ready('a'),'lease-restored JVM');
  const expired=(await control('a','status',0)).result;assert.deepEqual([expired.failed,expired.errorCode,expired.state],[true,'lease_expired','running']);
  console.log('Backend frozen JAR, pause/resume readiness, kill/start, old request replay, generation 1->2, lease restoration verified.');
  receipt.checks.push({role:'a',jarSha256:expectedJar,pausedReadinessBlocked:true,resumedReadiness:true,killStopped:true,replayedKillSafe:true,generations:[1,2],leaseRestored:true});
  ownership('b');await operation('b','kill',1,'stopped',1800);assert.throws(()=>ready('b'));assert(ready('a'));
  await until(()=>ready('b'),'lease-restarted backend');
  const b=(await control('b','status',0)).result;assert.deepEqual([b.failed,b.verified,b.errorCode,b.state,b.generation],[true,true,'lease_expired','running',2]);
  receipt.checks.push({role:'b',leasedKillRestart:true,generations:[1,2],peerStayedReady:true,expiryFailed:true});
}
async function lifecycle(){
  const end=Date.now()+7000;
  start('deadline','redis',{FTW_FIXTURE_REDIS_PASSWORD:password},'/data',end);
  await until(()=>inside('deadline','redis-cli','ping').includes('NOAUTH'),'deadline fixture readiness');
  const req={fixtureId:id,requestId:randomUUID(),operation:'pause',generation:1,leaseMillis:15000,deadlineUnixMillis:end-100};
  assert.equal((await control('deadline','pause',1,15000,req)).result.state,'paused');
  await until(()=>docker(['inspect','--format','{{.State.Running}} {{.State.Pid}}',name('deadline')])==='false 0','hard deadline',10000);
  docker(['kill','--signal=KILL',name('pg')]);
  await until(()=>docker(['inspect','--format','{{.State.Running}} {{.State.Pid}}',name('pg')])==='false 0','PID 1 death');
  assert.throws(()=>docker(['top',name('pg')]));assert.throws(()=>docker(['top',name('deadline')]));
  console.log('Hard deadline and abrupt PID 1 death: private namespaces stopped, init PID=0, no live process table.');
  receipt.checks.push({hardDeadlineStopped:true,abruptPID1DeathStopped:true,liveProcessTables:0});
}

try{
  fs.mkdirSync(scratch);
  for(const directory of ['tools/room-fault-supervisor','tools/room-fault-supervisor/cmd/supervisor','deploy/room-ownership/supervised']){
    for(const entry of fs.readdirSync(path.join(root,directory),{withFileTypes:true})){
      if(entry.isFile()){
        const relative=directory+'/'+entry.name;
        receipt.sources[relative]=createHash('sha256').update(fs.readFileSync(path.join(root,relative))).digest('hex');
      }
    }
  }
  receipt.sources['scripts/tests/room-fault-process-capability.mjs']=createHash('sha256').update(fs.readFileSync(fileURLToPath(import.meta.url))).digest('hex');
  assert.equal(createHash('sha256').update(fs.readFileSync(path.join(frozen,'lib/server.jar'))).digest('hex'),expectedJar);
  build('supervisor',path.join(root,'tools/room-fault-supervisor'));
  const extract=name('extract');docker(['create','--network','none','--label',label,'--name',extract,name('supervisor'),'/supervisor']);
  const context=path.join(scratch,'context');fs.mkdirSync(context);
  docker(['cp',extract+':/supervisor',path.join(context,'supervisor')]);
  fs.copyFileSync(path.join(recipes,'redis-entrypoint.sh'),path.join(context,'redis-entrypoint.sh'));
  build('postgres',context);build('redis',context);
  fs.cpSync(frozen,path.join(context,'distribution'),{recursive:true});
  build('backend',context);
  docker(['network','create','--internal','--label',label,id]);
  start('pg','postgres',{POSTGRES_PASSWORD:password,POSTGRES_USER:pilotDB,POSTGRES_DB:pilotDB},'/var/lib/postgresql');
  start('redis','redis',{FTW_FIXTURE_REDIS_PASSWORD:password},'/data');
  await until(()=>inside('pg','pg_isready','-h','127.0.0.1','-U',pilotDB).includes('accepting connections'),'PostgreSQL TCP readiness');
  await until(()=>inside('redis','redis-cli','ping').includes('NOAUTH'),'Redis readiness');
  const seedEnv={...database,FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_PASSWORD:'Aa1!'+secret()};
  docker(['run','--rm','--pull','never','--cap-drop','ALL','--security-opt','no-new-privileges:true','--network',id,'--label',label,
    ...Object.keys(seedEnv).flatMap(k=>['-e',k]),'--entrypoint','java',name('backend'),'-cp','/opt/fasttowin/lib/*',
    'com.hienthai.fastowin.server.ownership.RoomOwnershipFixtureSeedKt'],{env:{...process.env,...seedEnv}});
  receipt.kernel=inside('pg','uname','-r');
  for(const role of ['supervisor','postgres','redis','backend']){
    const source=fs.readFileSync(path.join(recipes,role+'.Dockerfile'),'utf8').match(/^FROM (\S+@sha256:[a-f0-9]{64})/m)[1];
    receipt.bases[role]={ref:source,config:docker(['image','inspect','--format','{{.Id}}',source])};
  }
  receipt.supervisorSha256=createHash('sha256').update(fs.readFileSync(path.join(context,'supervisor'))).digest('hex');
  console.log('kernel '+receipt.kernel);
  await postgres();await redis();await backend();await lifecycle();
  receipt.passed=true;
}catch(error){
  // Only allowlisted diagnostics; raw app logs can contain fixture data.
  if(docker(['ps','-q','--filter','name=^/'+name('a')+'$'])){
    const log=spawnSync('docker',['logs',name('a')],{encoding:'utf8'});
    const classes=[...new Set((log.stdout+log.stderr).match(/\b[A-Za-z.]+(?:Exception|Error)\b|\b[A-Za-z]+\.kt:[0-9]+/g))];
    console.log('Backend exception classes and source locations',classes.slice(0,28));
    console.log('Backend processes',inside('a','ps','-o','pid,ppid,comm'));
    for(const route of ['health','ready']){
      const probe=spawnSync('docker',['exec',name('a'),'wget','-S','-T','1','-qO','/dev/null','http://127.0.0.1:8080/'+route],{encoding:'utf8'});
      console.log('Backend '+route+' probe',probe.status,(probe.stderr.match(/HTTP\/1\.[01] [0-9]{3}/g)||[]));
    }
  }
  throw error;
}finally{
  for(const c of clients)c.close();
  // Recover even after a CLI timeout following a successful create/build.
  const owned=(args)=>docker(args).split(/\r?\n/).filter(Boolean);
  for(const c of owned(['ps','-aq','--filter','label='+label]))docker(['rm','-fv',c]);
  for(const v of owned(['volume','ls','-q','--filter','label='+label]))docker(['volume','rm',v]);
  for(const n of owned(['network','ls','-q','--filter','label='+label]))docker(['network','rm',n]);
  for(const i of owned(['image','ls','--format','{{.Repository}}:{{.Tag}}','--filter','label='+label]))docker(['image','rm',i]);
  for(const args of [['ps','-aq'],['volume','ls','-q'],['network','ls','-q'],['image','ls','-q']])assert.equal(docker([...args,'--filter','label='+label]),'');
  assert.equal(path.dirname(scratch),path.join(root,'.artifacts'));assert.match(path.basename(scratch),/^room-proc-[0-9a-f]{14}$/);
  fs.rmSync(scratch,{recursive:true,force:true});
  console.log('Owned containers, networks, volumes and tagged images remaining: 0.');
  receipt.remaining={containers:0,networks:0,volumes:0,images:0};
  const evidence=path.join(root,'.artifacts/room-fault-process-evidence');
  fs.mkdirSync(evidence,{recursive:true});
  const out=path.join(evidence,id+'.json');fs.writeFileSync(out,JSON.stringify(receipt,null,2)+'\n');
  console.log('Technical receipt '+out);
}
