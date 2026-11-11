import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { createHash, randomBytes, randomUUID } from 'node:crypto'
import fs from 'node:fs'
import path from 'node:path'
import { setTimeout as sleep } from 'node:timers/promises'
import { readProductionReceipt } from './read-room-rehearsal-receipt.mjs'

const root=path.resolve(import.meta.dirname,'..')
const [output,selected,runId,runnerImage,endpoint]=process.argv.slice(2)
assert(['Fault','Load'].includes(selected) && /^[a-f0-9-]{36}$/.test(runId))
assert(/^npipe:\/{4}\.\/pipe\/[A-Za-z0-9_.-]+$/.test(endpoint))
const suffix=runId.replaceAll('-','').slice(0,12), name='room-pilot-'+suffix
const directory=path.resolve(output)
assert.equal(directory,path.join(root,'.artifacts/room-production-local',name))
const label='fasttowin.production-rehearsal='+runId
const db='room_rehearsal_'+suffix, profile=selected==='Fault'?'fault':'two-backend'
const sha=file=>createHash('sha256').update(fs.readFileSync(file)).digest('hex')
const put=(file,value)=>fs.writeFileSync(path.join(directory,file),JSON.stringify(value,null,2)+'\n')
const hex=()=>randomBytes(32).toString('hex')
const dbPassword=hex(), redisPassword=hex(), token=hex(), peerSecret=randomBytes(32).toString('base64')
const deadline=Date.now()+3_600_000
const host=role=>name+'-'+role+'.railway.internal'
const control={ROOM_FAULT_FIXTURE:runId,ROOM_FAULT_TOKEN:token,ROOM_FAULT_DEADLINE_MS:String(deadline)}
function docker(args,env={},timeout=120_000) {
    try {
        return execFileSync('docker.exe',['--host',endpoint,...args],{
            cwd:root,env:{...process.env,...env},encoding:'utf8',timeout,maxBuffer:8*1024*1024,windowsHide:true,
            stdio:['ignore','pipe','pipe']
        }).trim()
    } catch { throw new Error('Docker '+args[0]+' failed') }
}
const contexts=path.join(directory,'contexts')
const accepted=path.join(root,'.artifacts/room-cloud-supervised-preflight/capability-v2')
const expectedSupervisor=JSON.parse(fs.readFileSync(path.join(root,
    '.artifacts/room-supervised-local/room-supervised-cb7e69f5e44e/run-manifest.json'),'utf8')).supervisorBinary
const images={}
for(const [role,recipe] of [['backend','backend'],['pg','postgres'],['redis','redis'],['proxy','proxy']]) {
    const context=path.join(contexts,role)
    fs.mkdirSync(context,{recursive:true})
    const supervisor=path.join(accepted,role,'supervisor')
    assert.equal(sha(supervisor),expectedSupervisor)
    fs.copyFileSync(supervisor,path.join(context,'supervisor'))
    fs.copyFileSync(path.join(root,'deploy/room-ownership/supervised',recipe+'.Dockerfile'),path.join(context,'Dockerfile'))
    if(role==='backend') fs.cpSync(path.join(root,'server/build/install/server'),path.join(context,'distribution'),{recursive:true})
    if(role==='redis') fs.copyFileSync(path.join(root,'deploy/room-ownership/supervised/redis-entrypoint.sh'),path.join(context,'redis-entrypoint.sh'))
    images[role]='ftw-production-'+role+':'+suffix
    docker(['build','--pull=false','--network','none','--label',label,'--tag',images[role],context],{},300_000)
}
assert.equal(sha(path.join(contexts,'backend/distribution/lib/server.jar')),sha(path.join(directory,'runner/lib/server.jar')))
images.runner=runnerImage
for(const role of Object.keys(images)) {
    const observed=JSON.parse(docker(['image','inspect',images[role]]))[0]
    assert.equal(observed.Config.Labels['fasttowin.production-rehearsal'],runId)
    assert.match(observed.Id,/^sha256:[a-f0-9]{64}$/)
    images[role]=observed.Id
}
put('images.json',images)
const files=dir=>fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>{
    const file=path.join(dir,e.name)
    assert(!e.isSymbolicLink())
    return e.isDirectory()?files(file):[{path:path.relative(contexts,file).replaceAll('\\','/'),sha256:sha(file),bytes:fs.statSync(file).size}]
})
put('contexts-manifest.json',files(contexts))
put('run-manifest.json',{runId,profile,runnerManifest:sha(path.join(directory,'runner-manifest.json')),
    contextsManifest:sha(path.join(directory,'contexts-manifest.json')),imagesManifest:sha(path.join(directory,'images.json')),supervisor:expectedSupervisor,
    jarSha256:sha(path.join(directory,'runner/lib/server.jar')),accounts:40,rooms:20,offerSeconds:600,
    policy:'production-defaults',sourceIps:'synthetic-per-account-forwarded-in-isolated-network'})
const manifest=sha(path.join(directory,'run-manifest.json'))
const jar=sha(path.join(directory,'runner/lib/server.jar'))
docker(['network','create','--internal','--label',label,name])
const containers={}
const limits={pg:['2','512000000'],redis:['0.25','250000000'],proxy:['2','128000000'],a:['3','1000000000'],b:['3','1000000000'],runner:['0.5','500000000']}
function launch(role,image,env,args=[],detached=true) {
    const selectedName=name+'-'+role
    containers[role]=selectedName
    const [cpu,memory]=limits[role]
    const flags=['run',...(detached?['-d']:[]),'--name',selectedName,'--label',label,'--network',name,
        '--network-alias',host(role),'--cap-drop','ALL','--security-opt','no-new-privileges:true',
        '--cpus',cpu,'--memory',memory,'--memory-swap',memory]
    if(role==='pg'||role==='redis') flags.push('--tmpfs',role==='pg'?'/var/lib/postgresql:rw,noexec,nosuid,size=64m':'/data:rw,noexec,nosuid,mode=0777,size=32m')
    for(const key of Object.keys(env)) flags.push('-e',key)
    return docker([...flags,image,...args],env)
}
launch('pg',images.pg,{...control,POSTGRES_DB:db,POSTGRES_USER:db,POSTGRES_PASSWORD:dbPassword,PGDATA:'/tmp/production-rehearsal'})
launch('redis',images.redis,{...control,FTW_FIXTURE_REDIS_PASSWORD:redisPassword})
launch('proxy',images.proxy,control)
let pgReady=false
for(let attempt=0;attempt<60;attempt++) {
    try {docker(['exec',containers.pg,'pg_isready','-h','127.0.0.1','-U',db]);pgReady=true;break} catch {}
    await sleep(1000)
}
assert(pgReady,'PostgreSQL did not initialize')
const accounts=Array.from({length:40},()=>randomUUID()).join(',')
const runnerEnv={
    RAILWAY_PROJECT_ID:'b5c5db71-2df8-46da-84fb-d9b9df6d7951',RAILWAY_ENVIRONMENT_ID:'8bcf4119-3a6b-49b3-b4ff-8ea20080f3b9',
    RAILWAY_ENVIRONMENT_NAME:'staging',RAILWAY_SERVICE_NAME:name+'-runner',FTW_REHEARSAL_ID:runId,FTW_REHEARSAL_PREFIX:'room-pilot',
    FTW_REHEARSAL_SCOPE:'room-ownership-production',FTW_REHEARSAL_MANIFEST_SHA256:manifest,
    FTW_REHEARSAL_PROFILE:profile,FTW_REHEARSAL_BACKEND_POOL_SIZE:'20',
    FTW_REHEARSAL_DEADLINE_UTC:new Date(deadline).toISOString(),FTW_REHEARSAL_JAR_SHA256:jar,
    FTW_REHEARSAL_BACKEND_A_HOST:host('a'),FTW_REHEARSAL_BACKEND_B_HOST:host('b'),FTW_REHEARSAL_REDIS_HOST:host('redis'),
    FTW_REHEARSAL_PROXY_HOST:host('proxy'),FTW_REHEARSAL_REDIS_PASSWORD:redisPassword,ROOM_FAULT_TOKEN:token,
    FASTTOWIN_ENV:'prod',FASTTOWIN_ROOM_OWNERSHIP_MODE:'production',FASTTOWIN_ROOM_OWNERSHIP_ACCOUNTS:accounts,
    FASTTOWIN_ROOM_OWNERSHIP_REHEARSAL_PASSWORD:'Rehearsal1!'+hex(),
    DATABASE_URL:'jdbc:postgresql://'+host('pg')+':5432/'+db,DATABASE_USER:db,DATABASE_PASSWORD:dbPassword
}
const seed=JSON.parse(launch('runner',images.runner,runnerEnv,['--seed-production'],false))
assert(seed.productionSeeded===true && seed.manifestSha256===manifest && seed.runId===runId)
put('seed-receipt.json',seed)
docker(['rm','-v',containers.runner])
const redisUrl='redis://:'+redisPassword+'@'+host('redis')+':6379'
for(const role of ['a','b']) {
    const peer=role==='a'?'b':'a'
    launch(role,images.backend,{
        ...control,ROOM_FAULT_ROLE:role,FASTTOWIN_ENV:'prod',SERVER_HOST:'::',PORT:'8080',
        FASTTOWIN_PUBLIC_URL:'https://fasttowin.up.railway.app',FASTTOWIN_WEB_ORIGINS:'https://fasttowin.up.railway.app',
        FASTTOWIN_SMTP_HOST:'mail.rehearsal.invalid',FASTTOWIN_SMTP_USERNAME:'rehearsal',
        FASTTOWIN_SMTP_FROM_EMAIL:'rehearsal@fasttowin.invalid',FASTTOWIN_SMTP_PASSWORD:hex(),FASTTOWIN_SMTP_STARTTLS:'true',
        DATABASE_URL:'jdbc:postgresql://'+host('proxy')+':'+(role==='a'?15432:25432)+'/'+db,
        DATABASE_USER:db,DATABASE_PASSWORD:dbPassword,DATABASE_POOL_SIZE:'20',
        FASTTOWIN_ROOM_OWNERSHIP_MODE:'production',FASTTOWIN_ROOM_OWNERSHIP_ACCOUNTS:accounts,
        FASTTOWIN_ROOM_OWNERSHIP_NODE_ID:'00000000-0000-4000-8000-00000000000'+role,
        FASTTOWIN_ROOM_OWNERSHIP_PEER_ID:'00000000-0000-4000-8000-00000000000'+peer,
        FASTTOWIN_ROOM_OWNERSHIP_PEER_URL:'http://'+host(peer)+':8080',FASTTOWIN_ROOM_OWNERSHIP_PEER_SECRET:peerSecret,
        FASTTOWIN_RATE_LIMIT_STORE:'redis',FASTTOWIN_RATE_LIMIT_REDIS_URL:redisUrl,FASTTOWIN_RATE_LIMIT_NAMESPACE:name+'-limits',
        FASTTOWIN_PRESENCE_STORE:'redis',FASTTOWIN_PRESENCE_REDIS_URL:redisUrl,FASTTOWIN_PRESENCE_NAMESPACE:name+'-presence'
    })
}
await sleep(12_000)
let capability
try { capability=launch('runner',images.runner,runnerEnv,['--capability-production'],false) }
catch {
    const line=docker(['logs',containers.runner]).split(/\r?\n/).find(l=>/^Production capability failed: [A-Z_]+$/.test(l))
    throw new Error(line??'Production private capability failed')
}
const proof=JSON.parse(capability)
assert(proof.productionCapabilityVerified===true && proof.jarSha256===jar && proof.manifestSha256===manifest)
put('capability.json',proof)
docker(['rm','-v',containers.runner])
console.log(profile+' production capability passed; starting full workload')
launch('runner',images.runner,runnerEnv,['--workload-production'])
const end=Date.now()+1_050_000
while(docker(['inspect','--format','{{.State.Running}}',containers.runner])==='true') {
    assert(Date.now()<end,'Workload exceeded its deadline')
    await sleep(3000)
}
const lines=docker(['logs',containers.runner]).split(/\r?\n/)
fs.writeFileSync(path.join(directory,'runner.log'),lines.join('\n')+'\n')
if(docker(['inspect','--format','{{.State.ExitCode}}',containers.runner])!=='0') {
    const diagnostic=path.join(directory,'failure-sites.json')
    try { docker(['cp',containers.runner+':/opt/runner/receipts/failure-sites.json',diagnostic]) } catch {}
    try {
        fs.writeFileSync(path.join(directory,'failure.json'),readProductionReceipt(lines,'failure',profile,manifest,jar))
    } catch {}
}
assert.equal(docker(['inspect','--format','{{.State.ExitCode}}',containers.runner]),'0','Production runner failed')
const kind=profile==='fault'?'fault':'load'
for(const selectedKind of [kind,'latency']) {
    const bytes=readProductionReceipt(lines,selectedKind,profile,manifest,jar)
    fs.writeFileSync(path.join(directory,selectedKind+'.json'),bytes)
}
for(const role of ['pg','redis','proxy','a','b']) {
    assert.equal(docker(['inspect','--format','{{.State.Running}}',containers[role]]),'true','Controlled runtime stopped')
    assert.equal(docker(['inspect','--format','{{.State.OOMKilled}}',containers[role]]),'false')
}
put('local-receipt.json',{productionProfileAccepted:true,profile,manifestSha256:manifest,jarSha256:jar})
console.log(profile+' production profile verified')
