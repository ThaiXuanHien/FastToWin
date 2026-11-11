// Host-owned local rehearsal. Secrets enter Docker only by environment name.
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
import {randomBytes,randomUUID,createHash} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {setTimeout as delay} from 'node:timers/promises';
import {localDocker,ownedPath} from './lib/room-supervised-local.mjs';
import {fixtureProbes,until} from './lib/room-supervised-probes.mjs';

const root=fileURLToPath(new URL('../',import.meta.url));
const output=ownedPath(path.join(root,'.artifacts/room-supervised-local'),process.argv[2]||'');
const packageName=path.basename(output);
assert.match(packageName,/^room-supervised-[a-f0-9]{12}$/);
const label='fasttowin.supervised='+packageName;
const docker=localDocker(root,path.join(output,'commands.ndjson'));
const recipes=path.join(root,'deploy/room-ownership/supervised');
const frozen=path.join(root,'.artifacts/room-ownership-db-latency-20261110/backend-d4b639e');
const expectedJar='8fff8ae123902032d98a92190b0bff3df87e234f213b25a298f4768c7e40e818';
const expectedBackendManifest='2a7236b02f37a321b3f4450111a89dd8c30a4765dc36d2b876cf9e56659f7506';
const hash=file=>createHash('sha256').update(fs.readFileSync(file)).digest('hex');
const hex=()=>randomBytes(32).toString('hex');
const write=(name,value)=>fs.writeFileSync(ownedPath(output,path.join(output,name)),JSON.stringify(value,null,2)+'\n');
const read=name=>JSON.parse(fs.readFileSync(path.join(output,name),'utf8'));
function files(dir) {
  return fs.readdirSync(dir,{withFileTypes:true}).sort((a,b)=>a.name.localeCompare(b.name)).flatMap(entry=>{
    const file=path.join(dir,entry.name);
    assert(!entry.isSymbolicLink(),'Source reparse points refused');
    return entry.isDirectory()?files(file):[{path:file,bytes:fs.statSync(file).size,sha256:hash(file)}];
  });
}
function fileManifest(dir) {return files(dir).map(entry=>({...entry,path:path.relative(dir,entry.path).replaceAll('\\','/')}))}
function inventory(owned=false) {
  const filter=owned?['--filter','label='+label]:[];
  const rows=args=>docker.run([...args,...filter]).split(/\r?\n/).filter(Boolean).sort();
  return {containers:rows(['ps','-aq','--no-trunc']),networks:rows(['network','ls','-q','--no-trunc']),
    volumes:rows(['volume','ls','-q']),images:rows(['image','ls','--all','--no-trunc','--format','{{.ID}} {{.Repository}}:{{.Tag}}'])};
}
function emptyResources(snapshot,images=false) {
  for(const kind of images?['containers','networks','volumes','images']:['containers','networks','volumes'])assert.equal(snapshot[kind].length,0,'Owned '+kind+' remain');
}
function cleanup(images=false) {
  let failure=false;
  for(const [list,command] of [
    [['ps','-aq'],['rm','-fv']],
    [['network','ls','-q'],['network','rm']],
    [['volume','ls','-q'],['volume','rm']],
    ...(images?[[['image','ls','--all','-q'],['image','rm']]]:[])
  ]) {
    const ids=[...new Set(docker.run([...list,'--filter','label='+label]).split(/\r?\n/).filter(Boolean))];
    for(const id of ids){try{docker.run([...command,id])}catch{failure=true}}
  }
  const after=inventory(true);
  write(images?'cleanup.json':'phase-cleanup.json',{at:new Date().toISOString(),success:!failure,remaining:after});
  emptyResources(after,images);
  assert(!failure,'Cleanup command failed');
  return after;
}
function build(role,context) {
  const tag=packageName+'-'+role;
  docker.run(['build','--pull=false','--network','none','--label',label,'-t',tag,'-f',
    role==='runner'?path.join(context,'Dockerfile'):path.join(recipes,role+'.Dockerfile'),context],{timeout:300000});
  return docker.run(['image','inspect','--format','{{.Id}}',tag]);
}
function sourceManifest() {
  const paths=['tools/room-fault-supervisor','deploy/room-ownership/supervised','server/src/test/kotlin/com/hienthai/fastowin/server/ownership',
    'scripts/lib/room-supervised-local.mjs','scripts/lib/room-supervised-probes.mjs','scripts/room-ownership-supervised-local.mjs',
    'scripts/room-ownership-supervised-local.ps1','scripts/read-room-rehearsal-receipt.mjs','scripts/tests/RoomSupervisedFixture.test.mjs',
    'scripts/tests/RoomSupervisedGuards.test.mjs','deploy/room-ownership/compose.supervised.yaml','deploy/room-ownership/staging-runner.Dockerfile'];
  return paths.flatMap(relative=>{
    const file=path.join(root,relative);
    const entries=fs.statSync(file).isDirectory()?files(file):[{path:file,bytes:fs.statSync(file).size,sha256:hash(file)}];
    return entries.map(entry=>({...entry,path:path.relative(root,entry.path).replaceAll('\\','/')}));
  });
}
function prepare() {
  assert.equal(hash(frozen+'-manifest.json'),expectedBackendManifest);
  const backend=JSON.parse(fs.readFileSync(frozen+'-manifest.json','utf8'));
  assert.equal(backend.sourceRevision,'d4b639e91dc08c95a0af3528024c3235f199860f');
  const actual=fileManifest(frozen);
  assert.equal(actual.length,backend.files.length);
  for(const entry of backend.files)assert.deepEqual(actual.find(item=>item.path===entry.path),entry);
  assert.equal(hash(path.join(frozen,'distribution/lib/server.jar')),expectedJar);
  assert.equal(hash(path.join(output,'runner/lib/server.jar')),expectedJar);
  const sources=sourceManifest();
  if(fs.existsSync(path.join(output,'source-manifest.json')))assert.deepEqual(read('source-manifest.json'),sources,'Resume source changed');
  else {
    write('source-manifest.json',sources);
    for(const entry of sources) {
      const target=ownedPath(output,path.join(output,'tested-source',entry.path));
      fs.mkdirSync(path.dirname(target),{recursive:true});fs.copyFileSync(path.join(root,entry.path),target);
    }
  }
  const runner=fileManifest(path.join(output,'runner'));
  if(fs.existsSync(path.join(output,'runner-manifest.json')))assert.deepEqual(read('runner-manifest.json'),runner);
  else write('runner-manifest.json',runner);
  const supervisor=build('supervisor',path.join(root,'tools/room-fault-supervisor'));
  const extract=packageName+'-extract';
  docker.run(['create','--network','none','--label',label,'--name',extract,supervisor,'/supervisor']);
  const context=ownedPath(output,path.join(output,'context'));fs.mkdirSync(context,{recursive:true});
  docker.run(['cp',extract+':/supervisor',path.join(context,'supervisor')]);
  docker.run(['rm','-v',extract]);
  fs.cpSync(path.join(frozen,'distribution'),path.join(context,'distribution'),{recursive:true});
  fs.copyFileSync(path.join(recipes,'redis-entrypoint.sh'),path.join(context,'redis-entrypoint.sh'));
  const images={supervisor,backend:build('backend',context),pg:build('postgres',context),redis:build('redis',context),proxy:build('proxy',context),runner:build('runner',path.join(output,'runner'))};
  const runtimes={};
  for(const file of ['supervisor','backend','postgres','redis','proxy']) {
    for(const line of fs.readFileSync(path.join(recipes,file+'.Dockerfile'),'utf8').split('\n')) {
      const ref=line.match(/^FROM (\S+@sha256:[a-f0-9]{64})/);
      if(ref)runtimes[ref[1]]=docker.run(['image','inspect','--format','{{.Id}}',ref[1]]);
    }
  }
  const manifest={acceptedSource:backend.sourceRevision,acceptedJar:expectedJar,backendManifest:expectedBackendManifest,
    backendJdbcTimeoutSeconds:{connect:3,socket:3},
    postgresMemoryMiB:{sharedBuffers:64,workMem:1,maintenanceWorkMem:32},
    sourceManifest:hash(path.join(output,'source-manifest.json')),runnerManifest:hash(path.join(output,'runner-manifest.json')),
    supervisorBinary:hash(path.join(context,'supervisor')),images,runtimes};
  if(fs.existsSync(path.join(output,'run-manifest.json'))) {
    const saved=read('run-manifest.json');
    assert.deepEqual({...saved,phases:undefined},{...manifest,phases:undefined},'Resume image or source changed');
    return saved;
  }
  manifest.phases={fault:randomUUID(),'two-backend':randomUUID()};
  write('run-manifest.json',manifest);
  return manifest;
}
function readReceipt(manifest,profile,kind,log,out) {
  execFileSync('node',[path.join(root,'scripts/read-room-rehearsal-receipt.mjs'),'read',manifest,profile,kind,log,out],{encoding:'utf8',windowsHide:true,stdio:['ignore','pipe','pipe']});
}
async function phase(profile,manifest,digest) {
  const dir=ownedPath(output,path.join(output,profile));fs.mkdirSync(dir,{recursive:true});
  if(fs.existsSync(path.join(dir,'verified.json'))) {
    const checkpoint=JSON.parse(fs.readFileSync(path.join(dir,'verified.json'),'utf8'));
    assert.equal(checkpoint.manifest,digest);
    for(const [file,sha] of Object.entries(checkpoint.files))assert.equal(hash(path.join(dir,file)),sha);
    console.log(profile+' verified checkpoint reused');return;
  }
  assert(!fs.existsSync(path.join(dir,'started.json')),'Previous incomplete phase requires a fresh package and new phase IDs');
  const fixture=manifest.phases[profile],run='room-pilot-'+fixture.replaceAll('-','').slice(0,12);
  const end=Date.now()+7100000;
  const env={...process.env,FTW_PILOT_NAME:run,FTW_SUPERVISED_PACKAGE:packageName,
    ROOM_FAULT_FIXTURE:fixture,ROOM_FAULT_TOKEN:hex(),ROOM_FAULT_DEADLINE_MS:String(end),
    FTW_PILOT_DEADLINE_UTC:new Date(end).toISOString(),FTW_PILOT_SUITE:profile,FTW_REHEARSAL_MANIFEST_SHA256:digest,
    FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_ID:fixture,FASTTOWIN_ROOM_OWNERSHIP_ACCOUNTS:Array.from({length:40},()=>randomUUID()).join(','),
    FASTTOWIN_ROOM_OWNERSHIP_DATABASE_PASSWORD:hex(),FASTTOWIN_ROOM_OWNERSHIP_FIXTURE_PASSWORD:'Pilot1!'+hex(),
    FASTTOWIN_ROOM_OWNERSHIP_PEER_SECRET:randomBytes(32).toString('base64'),FTW_FIXTURE_REDIS_PASSWORD:hex(),
    FTW_BACKEND_IMAGE:manifest.images.backend,FTW_PG_IMAGE:manifest.images.pg,FTW_REDIS_IMAGE:manifest.images.redis,
    FTW_PROXY_IMAGE:manifest.images.proxy,FTW_RUNNER_IMAGE:manifest.images.runner};
  for(const [role,key] of [['a','BACKEND_A'],['b','BACKEND_B'],['redis','REDIS'],['proxy','PROXY']])env['FTW_PILOT_'+key+'_HOST']=run+'-'+role+'.railway.internal';
  const compose=['compose','-p',run,'-f',path.join(root,'deploy/room-ownership/compose.supervised.yaml')];
  const c=args=>docker.run([...compose,...args],{env});
  const containers={};
  const phaseWrite=(file,value)=>write(profile+'/'+file,value);
  const log=path.join(dir,'runner.log');
  let success=false;
  try {
    emptyResources(inventory(true));
    phaseWrite('started.json',{fixture,profile,manifest:digest,deadline:new Date(end).toISOString(),started:new Date().toISOString()});
    const rendered=JSON.parse(c(['config','--format','json']));
    // Exports retain limits, aliases and users. All environment values stay private.
    for(const service of Object.values(rendered.services))service.environment=Object.keys(service.environment||{}).sort();
    phaseWrite('compose-safe.json',rendered);
    c(['up','-d','pg','redis','proxy']);
    for(const role of ['pg','redis','proxy'])containers[role]=c(['ps','-aq',role]);
    await until(()=>docker.run(['exec',containers.pg,'pg_isready','-h','127.0.0.1','-U','fasttowin_room_pilot']).includes('accepting connections'),'PostgreSQL initialization');
    const sql=args=>docker.run(['exec',containers.pg,'psql','-X','-At','-U','fasttowin_room_pilot','-d','fasttowin_room_pilot','-c',args]);
    const pgMemory=JSON.parse(sql("SELECT json_build_object('sharedBuffers',pg_size_bytes(current_setting('shared_buffers'))/1048576,'workMem',pg_size_bytes(current_setting('work_mem'))/1048576,'maintenanceWorkMem',pg_size_bytes(current_setting('maintenance_work_mem'))/1048576)"));
    assert.deepEqual(pgMemory,manifest.postgresMemoryMiB);
    phaseWrite('postgres-settings.json',pgMemory);
    const seed=c(['run','--no-deps','--name',run+'-runner','runner','--seed']);
    assert.equal(seed,'{"fixtureSeeded":true}');
    phaseWrite('seed.json',{fixtureSeeded:true});
    docker.run(['rm','-v',run+'-runner']);
    c(['up','-d','backend-a','backend-b']);
    for(const role of ['a','b'])containers[role]=c(['ps','-aq','backend-'+role]);
    const probes=fixtureProbes(docker,containers,env);
    phaseWrite('capability.json',await probes.preflight());
    console.log(profile+' capability preflight passed');
    for(const role of ['a','b'])assert.equal(probes.inside(role,'sha256sum','/opt/fasttowin/lib/server.jar').split(' ')[0],expectedJar);
    const extra=['ROOM_FAULT_TOKEN','FTW_PILOT_BACKEND_A_HOST','FTW_PILOT_BACKEND_B_HOST','FTW_PILOT_REDIS_HOST','FTW_PILOT_PROXY_HOST'].flatMap(key=>['-e',key]);
    c(['run','-d','--no-deps','--name',run+'-runner',...extra,'runner']);
    containers.runner=run+'-runner';
    const runtime={};
    const limits={pg:[2,512000000,'postgres'],redis:[0.25,250000000,'redis'],proxy:[2,128000000,'10001:10001'],
      a:[3,1000000000,'fasttowin'],b:[3,1000000000,'fasttowin'],runner:[0.5,500000000,'runner']};
    for(const [role,id] of Object.entries(containers)){
      const config=JSON.parse(docker.run(['inspect','--format','{{json .HostConfig}}',id]));
      assert.equal(config.Privileged,false);assert.equal(config.PidMode,'');assert.equal(config.CapAdd,null);
      assert.deepEqual(config.CapDrop,['ALL']);assert.equal(Object.keys(config.PortBindings||{}).length,0);
      assert(config.SecurityOpt.includes('no-new-privileges:true'));
      runtime[role]={image:docker.run(['inspect','--format','{{.Image}}',id]),nanoCpus:config.NanoCpus,memory:config.Memory,memorySwap:config.MemorySwap,
        user:docker.run(['inspect','--format','{{.Config.User}}',id])};
      assert.deepEqual([config.NanoCpus,config.Memory,config.MemorySwap,runtime[role].user],
        [limits[role][0]*1e9,limits[role][1],limits[role][1],limits[role][2]]);
      assert.equal(runtime[role].image,manifest.images[role==='a'||role==='b'?'backend':role]);
      if(role==='a'||role==='b') {
        const jdbcUrl=probes.inside(role,'printenv','DATABASE_URL');
        assert.equal(jdbcUrl,'jdbc:postgresql://'+run+'-proxy.railway.internal:'+(role==='a'?15432:25432)+'/fasttowin_room_pilot?connectTimeout=3&socketTimeout=3');
        runtime[role].jdbcUrl=jdbcUrl;
        runtime[role].configuredPool=Number(probes.inside(role,'printenv','DATABASE_POOL_SIZE'));
        assert.equal(runtime[role].configuredPool,20);
      }
    }
    phaseWrite('runtime.json',runtime);
    const overhead=[],start=Date.now();
    const memory=[];
    function sampleMemory(){
      const roles={};
      for(const role of ['a','b','pg','redis','proxy']){
        const result=probes.inside(role,'sh','-c',"awk '/^(oom|oom_kill|max) /{print}' /sys/fs/cgroup/memory.events; awk '/^(anon|file|shmem|kernel|slab|pagetables) /{print}' /sys/fs/cgroup/memory.stat; cat /sys/fs/cgroup/memory.current /sys/fs/cgroup/memory.peak").split('\n');
        const peakBytes=Number(result.pop()),currentBytes=Number(result.pop());
        const counters=Object.fromEntries(result.map(line=>{const [key,value]=line.split(' ');return [key,Number(value)]}));
        assert(Object.values(counters).every(Number.isSafeInteger));
        roles[role]={...counters,currentBytes,peakBytes};
      }
      memory.push({elapsedSeconds:(Date.now()-start)/1000,roles});
      phaseWrite('cgroup-memory.json',memory);
      return roles;
    }
    let sampleAt=0,announceAt=0;
    console.log(profile+' started, fixed 600-second/20-room/40-account profile');
    while(docker.run(['inspect','--format','{{.State.Running}}',containers.runner])==='true'){
      assert(Date.now()-start<1850000,'Runner exceeded host timeout');
      if(Date.now()>=sampleAt){
        const samples={elapsedSeconds:(Date.now()-start)/1000,roles:{}};
        for(const role of ['pg','redis','proxy','a','b']){
          try {samples.roles[role]=probes.processes(role)}
          catch {samples.roles[role]={transitionObserved:true}}
        }
        overhead.push(samples);phaseWrite('overhead.json',overhead);sampleMemory();sampleAt=Date.now()+60000;
      }
      if(Date.now()>=announceAt){console.log(profile+' running '+Math.floor((Date.now()-start)/1000)+'s');announceAt=Date.now()+120000}
      await delay(3000);
    }
    const exit=Number(docker.run(['inspect','--format','{{.State.ExitCode}}',containers.runner]));
    const finalMemory=sampleMemory();
    phaseWrite('exit.json',{code:exit,elapsedSeconds:(Date.now()-start)/1000});
    const logs=docker.run(['logs',containers.runner]);
    fs.writeFileSync(log,logs+'\n');
    for(const kind of ['fault','load','latency','failure']){
      try{docker.run(['cp',containers.runner+':/opt/runner/receipts/rehearsal-'+kind+'.json',path.join(dir,'original-'+kind+'.json')])}catch{}
    }
    for(const file of ['load-outages.ndjson','load-phase-summary.json','failure-sites.json']){
      try{docker.run(['cp',containers.runner+':/opt/runner/receipts/'+file,path.join(dir,file)])}catch{}
    }
    if(exit!==0){
      const counters={};
      for(const role of ['a','b']){
        // Docker logs may include SQL and identities. Persist only counts of fixed exception names.
        const tail=docker.run(['logs','--tail','1000',containers[role]]);
        counters[role]={};
        for(const type of ['SocketTimeoutException','SQLTransientConnectionException','PSQLException','RateLimitUnavailableException']){
          counters[role][type]=tail.split(type).length-1;
        }
      }
      phaseWrite('backend-failure-counts.json',counters);
      const health={};
      for(const role of ['a','b','pg','redis','proxy']){
        try{const state=await probes.control(role,'status');health[role]={verified:state.verified,state:state.state,generation:state.generation};}
        catch{health[role]={verified:false};}
      }
      phaseWrite('final-control.json',health);
      try{docker.run(['cp',containers.runner+':/opt/runner/receipts/postgres-pause-diagnostic.json',path.join(dir,'postgres-pause-diagnostic.json')])}catch{}
      if(fs.existsSync(path.join(dir,'original-failure.json')))readReceipt(digest,profile,'failure',log,path.join(dir,'failure.json'));
      throw new Error(profile+' runner failed; classified receipt retained');
    }
    for(const m of Object.values(finalMemory))assert.equal(m.oom_kill,0,'Kernel OOM kill during rehearsal');
    const sums={};
    for(const kind of [profile==='fault'?'fault':'load','latency']){
      const decoded=path.join(dir,kind+'.json'),original=path.join(dir,'original-'+kind+'.json');
      readReceipt(digest,profile,kind,log,decoded);
      assert(fs.readFileSync(decoded).equals(fs.readFileSync(original)),'Receipt decoded bytes differ from original');
      sums[kind+'.json']=hash(decoded);sums['original-'+kind+'.json']=hash(original);
    }
    sums['runner.log']=hash(log);
    for(const role of ['pg','redis','proxy','a','b'])assert.equal((await probes.control(role,'status')).state,'running');
    await until(()=>probes.ready('a')&&probes.ready('b'),'final readiness');
    phaseWrite('verified.json',{manifest:digest,files:sums});
    success=true;
    console.log(profile+' passed exact-byte independent receipt verification');
  }finally{
    const remaining=cleanup();
    phaseWrite('cleanup.json',{success:true,remaining});
    console.log(profile+' cleanup verified');
  }
  assert(success);
}

let baseline,owned=false,acceptedDigest;
try {
  baseline=inventory();write('before.json',baseline);emptyResources(inventory(true),true);
  write('bridge-before.json',builtinBridge());
  owned=true;
  const manifest=prepare(),digest=hash(path.join(output,'run-manifest.json'));
  write('run-identity.json',{manifestSha256:digest,head:execFileSync('git',['rev-parse','HEAD'],{cwd:root,encoding:'utf8'}).trim(),dirty:execFileSync('git',['status','--porcelain'],{cwd:root,encoding:'utf8'}).trim()});
  fs.writeFileSync(path.join(output,'tested.patch'),execFileSync('git',['diff','--binary','HEAD'],{cwd:root}));
  for(const profile of ['fault','two-backend'])await phase(profile,manifest,digest);
  fs.mkdirSync(path.join(output,'decoded'),{recursive:true});
  execFileSync('node',[path.join(root,'scripts/read-room-rehearsal-receipt.mjs'),'gate',digest,path.join(output,'fault/runner.log'),path.join(output,'two-backend/runner.log'),path.join(output,'decoded')],{stdio:['ignore','pipe','pipe'],windowsHide:true});
  assert.deepEqual(sourceManifest(),read('source-manifest.json'),'Source changed during run');
  acceptedDigest=digest;
}catch(error){
  write('host-failure.json',{error:String(error.message),at:new Date().toISOString()});
  process.exitCode=1;
  console.error(error.message);
}finally{
  if(owned)cleanup(true);
  const after=inventory();write('after.json',after);
  if(baseline) {
    const previous=read('bridge-before.json'),current=builtinBridge();
    write('bridge-after.json',current);
    assert.deepEqual({...previous,id:null},{...current,id:null},'Builtin bridge metadata changed');
    for(const kind of ['containers','networks','volumes','images']) {
      for(const item of baseline[kind]) {
        if(kind==='networks' && item===previous.id && current.id!==previous.id) {
          assert(after.networks.includes(current.id));
          write('builtin-bridge-replacement.json',{before:previous.id,after:current.id});
        }else assert(after[kind].includes(item),'Unrelated '+kind+' changed');
      }
    }
  }
  if(acceptedDigest) {
    write('acceptance.json',{localAccepted:true,cloudTwoBackendAccepted:false,cloudFaultAccepted:false,productionReady:false,manifestSha256:acceptedDigest});
    console.log('Local combined gate accepted '+acceptedDigest);
  }
  console.log('Owned containers, networks, volumes and all images zero; unrelated inventory checked');
}

function builtinBridge() {
  const [network]=JSON.parse(docker.run(['network','inspect','bridge']));
  assert.equal(network.Name,'bridge');
  assert.equal(network.Driver,'bridge');
  assert.equal(network.Options['com.docker.network.bridge.default_bridge'],'true');
  return {id:network.Id,name:network.Name,driver:network.Driver,scope:network.Scope,
    options:network.Options,internal:network.Internal,ipam:network.IPAM,containers:Object.keys(network.Containers||{}).sort()};
}
