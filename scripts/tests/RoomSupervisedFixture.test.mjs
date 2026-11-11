import test from 'node:test';
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
const root=fileURLToPath(new URL('../../',import.meta.url));
const fixture='12345678-1234-5678-9012-345678901234', run='room-pilot-123456781234';
const env={...process.env,FTW_PILOT_NAME:run,ROOM_FAULT_FIXTURE:fixture,ROOM_FAULT_TOKEN:'a'.repeat(64),
 ROOM_FAULT_DEADLINE_MS:'1900000000000',FTW_PILOT_DEADLINE_UTC:'2030-03-17T17:46:40Z',
 FTW_PILOT_SUITE:'fault',FTW_REHEARSAL_MANIFEST_SHA256:'b'.repeat(64),
 FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_ID:fixture,FASTTOWIN_ROOM_OWNERSHIP_ACCOUNTS:fixture,
 FASTTOWIN_ROOM_OWNERSHIP_DATABASE_PASSWORD:'c'.repeat(64),FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_PASSWORD:'Pilot1!'+'d'.repeat(64),
 FASTTOWIN_ROOM_OWNERSHIP_PEER_SECRET:'e'.repeat(44),FTW_FIXTURE_REDIS_PASSWORD:'f'.repeat(64),
 FTW_SUPERVISED_PACKAGE:'room-supervised-123456789abc',
 FTW_BACKEND_IMAGE:'sha256:'+'1'.repeat(64),FTW_PG_IMAGE:'sha256:'+'2'.repeat(64),
 FTW_REDIS_IMAGE:'sha256:'+'3'.repeat(64),FTW_PROXY_IMAGE:'sha256:'+'4'.repeat(64),FTW_RUNNER_IMAGE:'sha256:'+'5'.repeat(64)};
test('rendered fixture isolates six services with fixed paths, users and resource limits',()=>{
 const c=JSON.parse(execFileSync('docker',['--host','npipe:////./pipe/docker_engine','compose','-p',run,'-f','deploy/room-ownership/compose.supervised.yaml','config','--format','json'],{cwd:root,env,encoding:'utf8'}));
 assert.deepEqual(Object.keys(c.services).sort(),['backend-a','backend-b','pg','proxy','redis','runner']);
 const expected={'backend-a':['fasttowin',3,1000000000],'backend-b':['fasttowin',3,1000000000],pg:['postgres',2,512000000],redis:['redis',0.25,250000000],proxy:['10001:10001',2,128000000],runner:['runner',0.5,500000000]};
 for(const [name,s] of Object.entries(c.services)){
  const [user,cpu,mem]=expected[name];
  assert.equal(s.user,user);assert.equal(Number(s.cpus),cpu);assert.equal(Number(s.mem_limit),mem);assert.equal(Number(s.memswap_limit),mem);
  assert.equal(s.privileged,undefined);assert.equal(s.pid,undefined);assert.equal(s.ports,undefined);assert.equal(s.network_mode,undefined);
  assert.deepEqual(s.cap_drop,['ALL']);assert.equal(s.cap_add,undefined);assert(s.security_opt.includes('no-new-privileges:true'));
  assert.deepEqual(Object.keys(s.networks),['fixture']);assert.match(s.image,/^sha256:[a-f0-9]{64}$/);
  assert.equal(s.labels['fasttowin.supervised'],env.FTW_SUPERVISED_PACKAGE);
  assert(!s.volumes?.some(v=>v.type==='bind'));assert.equal(s.build,undefined);
  if(name!=='runner'){assert.equal(s.environment.ROOM_FAULT_FIXTURE,fixture);assert.equal(s.environment.ROOM_FAULT_TOKEN,env.ROOM_FAULT_TOKEN)}
 }
 for(const [role,port,peer] of [['a',15432,'b'],['b',25432,'a']]){
  const e=c.services['backend-'+role].environment;
  assert.equal(e.DATABASE_URL,'jdbc:postgresql://'+run+'-proxy.railway.internal:'+port+'/fasttowin_room_pilot?connectTimeout=3&socketTimeout=3');
  assert.equal(e.DATABASE_POOL_SIZE,'20');
  assert.equal(e.FASTTOWIN_ROOM_OWNERSHIP_PEER_URL,'http://'+run+'-'+peer+'.railway.internal:8080');
 }
 assert.deepEqual(c.services.pg.networks.fixture.aliases,[run+'-pg.railway.internal']);
 assert.equal(c.services.pg.environment.POSTGRES_INITDB_ARGS,'-c shared_buffers=64MB -c work_mem=1MB -c maintenance_work_mem=32MB');
 assert.equal(c.services.runner.environment.FTW_PILOT_DATABASE_HOST,run+'-pg.railway.internal');
 assert.equal(c.services.runner.environment.ROOM_FAULT_TOKEN,undefined); // host adds control token and other targets after seed
 assert.equal(c.services.runner.environment.DATABASE_URL,undefined);
 assert.equal(c.networks.fixture.internal,true);
 assert.equal(Object.keys(c.networks).length,1);
 assert.deepEqual(Object.keys(c.volumes).sort(),['pg-data','redis-data']);
});
