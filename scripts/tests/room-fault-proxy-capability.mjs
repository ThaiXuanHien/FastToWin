// Local-only, cached immutable images; no host ports or extra privileges.
import assert from 'node:assert/strict';
import {execFileSync,spawn} from 'node:child_process';
import {randomBytes,randomUUID,createHash} from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createInterface} from 'node:readline';
import {setTimeout as delay} from 'node:timers/promises';

const root=fileURLToPath(new URL('../../',import.meta.url));
const recipes=path.join(root,'deploy/room-ownership/supervised');
const fixture=randomUUID(), run='room-pilot-'+fixture.replaceAll('-','').slice(0,12);
const pgHost=run+'-pg.railway.internal';
const endpoint='npipe:////./pipe/docker_engine';
function localArgs(args){
 if(process.env.DOCKER_HOST && !/^(npipe:\/\/\/\/\.\/pipe\/[a-zA-Z0-9_.-]+|unix:\/\/\/[a-zA-Z0-9_./-]+)$/.test(process.env.DOCKER_HOST))throw new Error('Remote Docker refused');
 return ['--host',endpoint,...args];
}
const id='room-proxy-'+randomBytes(7).toString('hex'), label='fasttowin.fixture='+id;
const name=role=>id+'-'+role, scratch=path.join(root,'.artifacts',id);
const token=randomBytes(32).toString('hex'), password=randomBytes(32).toString('hex');
const deadline=Date.now()+300000, clients=new Set();
const pg='postgres@sha256:123b17998ccaf47e53b502e8c6a1e79afd8f579c6f3eec83c563da026f957395';
const toxi='ghcr.io/shopify/toxiproxy@sha256:9378ed52a28bc50edc1350f936f518f31fa95f0d15917d6eb40b8e376d1a214e';
const receipt={sources:{},bases:{},images:{},checks:[]};
function docker(args,options={}){
 try{return execFileSync('docker',localArgs(args),{encoding:'utf8',stdio:['pipe','pipe','pipe'],timeout:120000,maxBuffer:4*1024*1024,...options}).trim();}
 catch(e){throw new Error('local docker '+args[0]+' failed (exit '+e.status+')'+(args[0]==='build'?'\n'+String(e.stderr).slice(-1800):''));}
}
const inside=(role,...args)=>docker(['exec',name(role),...args]);
async function until(probe,description,ms=20000){const end=Date.now()+ms;while(Date.now()<end){try{if(await probe())return;}catch{}await delay(100);}throw new Error('timed out: '+description);}
function build(role,context){docker(['build','--pull=false','--network','none','--label',label,'-t',name(role),'-f',path.join(recipes,role+'.Dockerfile'),context],{timeout:240000});receipt.images[role]=docker(['image','inspect','--format','{{.Id}}',name(role)]);console.log(role+' image '+receipt.images[role]);}
function start(role,image,env={},extra=[],end=deadline){
 const settings={ROOM_FAULT_FIXTURE:fixture,ROOM_FAULT_TOKEN:token,ROOM_FAULT_DEADLINE_MS:String(end),...env};
 docker(['run','-d','--pull','never','--cap-drop','ALL','--security-opt','no-new-privileges:true','--network',id,'--label',label,'--name',name(role),...extra,...Object.keys(settings).flatMap(k=>['-e',k]),image],{env:{...process.env,...settings}});
 const config=JSON.parse(docker(['inspect','--format','{{json .HostConfig}}',name(role)]));
 assert.equal(config.Privileged,false);assert.equal(config.PidMode,'');assert.equal(config.NetworkMode,id);assert.deepEqual(config.CapDrop,['ALL']);assert.equal(config.CapAdd,null);assert(config.SecurityOpt.includes('no-new-privileges:true'));assert.equal(Object.keys(config.PortBindings??{}).length,0);
}
class Lines {
 constructor(role,args){
  this.proc=spawn('docker',localArgs(['exec','-i',name(role),...args]),{stdio:['pipe','pipe','ignore']});this.proc.stdin.on('error',()=>{});
  this.queue=[];this.waiters=[];this.closed=false;clients.add(this);
  const lines=createInterface({input:this.proc.stdout,crlfDelay:Infinity});
  lines.on('line',value=>{const cb=this.waiters.shift();if(cb)cb(value);else this.queue.push(value);});
  lines.on('close',()=>{this.closed=true;for(const cb of this.waiters.splice(0))cb(undefined);});
 }
 send(value){this.proc.stdin.write(value);}
 async next(ms=4000){
  if(this.queue.length)return this.queue.shift();if(this.closed)throw new Error('connection closed');
  let callback,timer;try{return await new Promise((resolve,reject)=>{callback=value=>value===undefined?reject(new Error('connection closed')):resolve(value);this.waiters.push(callback);timer=setTimeout(()=>reject(new Error('response timeout')),ms);});}
  finally{clearTimeout(timer);this.waiters=this.waiters.filter(cb=>cb!==callback);}
 }
 close(){this.proc.stdin.end();this.proc.kill();clients.delete(this);}
}
async function control(operation,leaseMillis=0,role='proxy',extra={}){
 const req={fixtureId:fixture,requestId:randomUUID(),generation:operation==='status'?0:1,operation,leaseMillis,deadlineUnixMillis:Math.min(deadline,Date.now()+10000),...extra};
 const body=JSON.stringify(req),http=new Lines(role,['nc','-w','12','127.0.0.1','8181']);
 try{
  http.send('POST /control HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\nAuthorization: Bearer '+token+'\r\nContent-Type: application/json\r\nContent-Length: '+Buffer.byteLength(body)+'\r\n\r\n'+body);
  const status=await http.next();while(await http.next()!==''){}return {code:Number(status.split(' ')[1]),body:JSON.parse(await http.next())};
 }finally{http.close();}
}
async function operation(op,state,lease=0){const r=await control(op,lease);assert.equal(r.code,200);assert.deepEqual([r.body.verified,r.body.failed,r.body.errorCode,r.body.state,r.body.generation],[true,false,'',state,1]);return r;}
const sql=(port)=>new Lines('pg',['psql','-X','-qAt','-v','ON_ERROR_STOP=1','-h','proxy','-p',String(port),'-U','fixture','-d','fixture']);
const fresh=port=>inside('pg','psql','-X','-qAt','-v','ON_ERROR_STOP=1','-h','proxy','-p',String(port),'-U','fixture','-d','fixture','-c','SELECT 42');
function ownership(){
 const rows=inside('proxy','sh','-c',`for f in /proc/[0-9]*/status; do awk '/^Name:/ {n=$2} /^Tgid:/ {p=$2} /^PPid:/ {pp=$2} /^Uid:/ {u=$2} /^Gid:/ {g=$2} /^CapEff:/ {print p,pp,n,u,g,$2}' "$f" || :; done`).split('\n').map(l=>l.split(/\s+/));
 const init=rows.find(r=>r[0]==='1'),broker=rows.find(r=>r[1]==='1'&&r[2]==='exe'),child=rows.find(r=>r[1]===broker?.[0]&&r[2]==='toxiproxy');
 assert(broker&&child);for(const r of [init,broker,child]){assert.equal(r[3],'10001');assert.equal(r[4],'10001');assert.match(r[5],/^0+$/);}
 assert.equal(inside('proxy','readlink','/proc/'+broker[0]+'/exe'),inside('proxy','readlink','/proc/1/exe'));
 // Linux proc encodes 127.0.0.1 and port 8474 as 0100007F:211A.
 const sockets=inside('proxy','cat','/proc/net/tcp');assert.match(sockets,/0100007F:211A\s+00000000:0000\s+0A/);assert(!sockets.includes('00000000:211A'));
 receipt.ownership={uid:10001,gid:10001,effectiveCaps:init[5],brokerRssKiB:Number(inside('proxy','awk','/^VmRSS:/ {print $2}','/proc/'+broker[0]+'/status')),rawAPILoopback:true};
 console.log('Proxy PID1/broker/Toxiproxy UID:GID 10001:10001, caps=0, raw API loopback.');
}
try{
 fs.mkdirSync(scratch);
 receipt.jarSha256=createHash('sha256').update(fs.readFileSync(path.join(root,'.artifacts/room-ownership-db-latency-20261110/backend-d4b639e/distribution/lib/server.jar'))).digest('hex');
 assert.equal(receipt.jarSha256,'8fff8ae123902032d98a92190b0bff3df87e234f213b25a298f4768c7e40e818');
 for(const dir of ['tools/room-fault-supervisor','tools/room-fault-supervisor/cmd/supervisor','deploy/room-ownership/supervised'])for(const e of fs.readdirSync(path.join(root,dir),{withFileTypes:true}))if(e.isFile()){const relative=dir+'/'+e.name;receipt.sources[relative]=createHash('sha256').update(fs.readFileSync(path.join(root,relative))).digest('hex');}
 receipt.sources['scripts/tests/room-fault-proxy-capability.mjs']=createHash('sha256').update(fs.readFileSync(fileURLToPath(import.meta.url))).digest('hex');
 for(const ref of [pg,toxi,'redis@sha256:71885d8cc6e42f2bc0aa2eb853bfc4be200b5108a46ac50e117ae1df0531fb80','golang@sha256:e482b31b5d30c31f9e495f4b8cbdf41429b2d74955dfbcd9c0a904483d738390'])receipt.bases[ref]=docker(['image','inspect','--format','{{.Id}}',ref]);
 build('supervisor',path.join(root,'tools/room-fault-supervisor'));
 docker(['create','--network','none','--label',label,'--name',name('extract'),name('supervisor'),'/supervisor']);
 const context=path.join(scratch,'context');fs.mkdirSync(context);docker(['cp',name('extract')+':/supervisor',path.join(context,'supervisor')]);
 receipt.supervisorSha256=createHash('sha256').update(fs.readFileSync(path.join(context,'supervisor'))).digest('hex');
 build('proxy',context);
 // Reuse the existing unprivileged PG image recipe; no backend JAR is built.
 build('postgres',context);docker(['network','create','--internal','--label',label,id]);
 const volume=name('pg-data');docker(['volume','create','--label',label,volume]);
 start('pg',name('postgres'),{ROOM_FAULT_ROLE:'pg',POSTGRES_PASSWORD:password,POSTGRES_USER:'fixture',POSTGRES_DB:'fixture',PGPASSWORD:password,PGCONNECT_TIMEOUT:'1'},['--network-alias',pgHost,'--mount','type=volume,source='+volume+',target=/var/lib/postgresql']);
 await until(()=>inside('pg','pg_isready','-h','127.0.0.1','-U','fixture').includes('accepting connections'),'PostgreSQL');
 start('proxy',name('proxy'),{},['--network-alias','proxy']);
 let lastStatus;
 try{await until(async()=>{lastStatus=await control('status');return lastStatus.code===200&&lastStatus.body.verified;},'verified proxy');}
 catch(error){
  console.log('Proxy safe status',lastStatus);
  console.log('Proxy process names',inside('proxy','ps','-o','pid,ppid,comm'));
  console.log('Proxy fixed config',inside('proxy','wget','-T','1','-qO','-','http://127.0.0.1:8474/proxies').slice(0,1500));
  console.log('Proxy SQL path',fresh(15432));
  const log=docker(['logs',name('proxy')]);
  for(const line of log.split('\n')){try{const item=JSON.parse(line);if(item.level==='error')console.log('Proxy diagnostic',JSON.stringify({level:item.level,message:item.message,error:item.error}));}catch{}}
  throw error;
 }
 assert.throws(()=>inside('proxy','getent','hosts','postgres'));
 receipt.markedUpstream=pgHost+':5432';receipt.noShortPostgresAlias=true;
 ownership();receipt.kernel=inside('proxy','uname','-r');
 assert.throws(()=>inside('pg','wget','-T','1','-qO','/dev/null','http://proxy:8474/proxies'));
 // Preserve B's connection identity and row across the partition of A.
 const a=sql(15432),b=sql(25432);
 a.send('SELECT pg_backend_pid();\n');const aid=await a.next();assert.match(aid,/^\d+$/);
 b.send('CREATE TABLE marker(n integer); INSERT INTO marker VALUES(1); SELECT pg_backend_pid();\n');const bid=await b.next();assert.match(bid,/^\d+$/);
 await operation('block-a','blocked-a',12000);
 a.send('SELECT 99;\n');
 await until(()=>a.closed,'existing SQL A closed',3000);assert(!a.queue.includes('99'));assert.throws(()=>fresh(15432));assert.equal(fresh(25432),'42');
 b.send('SELECT pg_backend_pid() || \',\' || sum(n) FROM marker;\n');assert.equal(await b.next(),bid+',1');
 await operation('unblock-a','running');assert.equal(fresh(15432),'42');
 const a2=sql(15432);a2.send('SELECT pg_backend_pid();\n');const aid2=await a2.next();assert.notEqual(aid2,aid);
 await operation('block-b','blocked-b',12000);b.send('SELECT 99;\n');await until(()=>b.closed,'existing SQL B closed',3000);assert(!b.queue.includes('99'));assert.throws(()=>fresh(25432));assert.equal(fresh(15432),'42');
 a2.send('SELECT pg_backend_pid();\n');assert.equal(await a2.next(),aid2);await operation('unblock-b','running');assert.equal(fresh(25432),'42');
 receipt.checks.push({existingAClosed:true,freshAFailed:true,existingBIdentityPreserved:true,BMarker:1,freshBUsable:true,symmetricBProof:true});
 console.log('Real PostgreSQL: selected existing socket closed, fresh SQL refused; opposite existing/fresh paths and marker preserved; both directions restored.');
 const tooLong=await control('block-a',60001);assert.equal(tooLong.code,400);
 await operation('block-a','blocked-a',1800);await delay(2300);assert.equal(fresh(15432),'42');assert.equal(fresh(25432),'42');
 const expired=await control('status');assert.equal(expired.code,503);assert.deepEqual([expired.body.failed,expired.body.errorCode,expired.body.verified,expired.body.state],[true,'lease_expired',true,'running']);
 receipt.checks.push({maxLeaseEnforced:true,runnerAbsentLeaseRestored:true,stickyExpiryFailure:true});
 console.log('Runner-absent lease restored both listeners; permanent failed/lease_expired/running/verified status retained.');
 // A second instance proves immutable configuration rejection before any fault.
 start('tamper',name('proxy'));
 await until(async()=>{const r=await control('status',0,'tamper');return r.code===200;},'tamper fixture');
 inside('tamper','wget','-qO','/dev/null','--post-data='+JSON.stringify({upstream:pgHost+':5433'}),'http://127.0.0.1:8474/proxies/pg-a');
 const changed=await control('block-a',10000,'tamper');assert.equal(changed.code,503);assert.equal(changed.body.verified,false);
 const config=JSON.parse(inside('tamper','wget','-qO','-','http://127.0.0.1:8474/proxies'));assert.equal(config['pg-a'].enabled,true);assert.equal(config['pg-b'].enabled,true);
 receipt.checks.push({changedUpstreamRejected:true});
 docker(['stop','--time','12',name('proxy')]);assert.equal(docker(['inspect','--format','{{.State.Running}} {{.State.Pid}}',name('proxy')]),'false 0');assert.throws(()=>docker(['top',name('proxy')]));
 const shortEnd=Date.now()+7000;start('deadline',name('proxy'),{},[],shortEnd);
 await until(async()=>{const r=await control('status',0,'deadline',{deadlineUnixMillis:shortEnd-50});return r.code===200;},'deadline fixture');
 const blocked=await control('block-b',12000,'deadline',{deadlineUnixMillis:shortEnd-50});assert.equal(blocked.code,200);
 await until(()=>docker(['inspect','--format','{{.State.Running}} {{.State.Pid}}',name('deadline')])==='false 0','deadline cleanup',12000);assert.throws(()=>docker(['top',name('deadline')]));
 receipt.checks.push({gracefulCloseReaped:true,fixtureDeadlineStopped:true});receipt.passed=true;
}finally{
 for(const c of clients)c.close();
 const owned=args=>docker(args).split(/\r?\n/).filter(Boolean);
 for(const c of owned(['ps','-aq','--filter','label='+label]))docker(['rm','-fv',c]);
 for(const v of owned(['volume','ls','-q','--filter','label='+label]))docker(['volume','rm',v]);
 for(const n of owned(['network','ls','-q','--filter','label='+label]))docker(['network','rm',n]);
 for(const i of owned(['image','ls','--all','--format','{{.ID}}','--filter','label='+label]))docker(['image','rm',i]);
 for(const args of [['ps','-aq'],['volume','ls','-q'],['network','ls','-q'],['image','ls','--all','-q']])assert.equal(docker([...args,'--filter','label='+label]),'');
 assert.equal(path.dirname(scratch),path.join(root,'.artifacts'));assert.match(path.basename(scratch),/^room-proxy-[a-f0-9]{14}$/);fs.rmSync(scratch,{recursive:true,force:true});
 receipt.remaining={containers:0,networks:0,volumes:0,images:0};const evidence=path.join(root,'.artifacts/room-fault-proxy-evidence');fs.mkdirSync(evidence,{recursive:true});
 const out=path.join(evidence,id+'.json');fs.writeFileSync(out,JSON.stringify(receipt,null,2)+'\n');console.log('Owned containers/networks/volumes/images=0. Technical receipt '+out);
}
