import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import fs from 'node:fs'
import path from 'node:path'
import { pathToFileURL } from 'node:url'
import { readProductionReceipt } from './read-room-rehearsal-receipt.mjs'
import { candidateIdentity } from './lib/room-production-manifest.mjs'

const sha = bytes => createHash('sha256').update(bytes).digest('hex')
const hex = /^[a-f0-9]{64}$/
const sharedKeys = ['jarSha256','classpathSha256','candidateSha256','runnerManifest','sourceManifest',
    'contextsManifest','migrationManifest','supervisor','backendClasspath','topology','limits',
    'accounts','rooms','offerSeconds','policy','sourceIps']

/** Compare identities only; verifyProductionRun below independently checks the retained artifact bytes. */
export function productionArtifactAgreement(fault, load, expectedCandidate) {
    assert.match(expectedCandidate,hex)
    assert.equal(fault.profile,'fault')
    assert(['two-backend','topology'].includes(load.profile))
    assert.notEqual(fault.runId,load.runId,'Two independent workload runs are required')
    assert.equal(fault.candidateSha256,expectedCandidate)
    assert.equal(load.candidateSha256,expectedCandidate)
    const frozen={}
    for (const key of sharedKeys) {
        assert.notEqual(fault[key],undefined,'Missing frozen field: '+key)
        assert.deepEqual(fault[key],load[key],'Frozen artifact drift: '+key)
        frozen[key]=fault[key]
    }
    assert(fault.accounts===40 && fault.rooms===20 && fault.offerSeconds===600 &&
        fault.policy==='production-defaults' && fault.topology.publicIngress==='a' &&
        fault.topology.privateWorker==='b')
    return sha(JSON.stringify(frozen))
}

function readFile(root,relative) {
    assert(typeof relative==='string' && relative.length>0 && !path.isAbsolute(relative))
    const target=path.resolve(root,relative)
    assert(target.startsWith(root+path.sep),'Artifact path escapes run directory')
    const real=fs.realpathSync(target)
    assert(real.startsWith(root+path.sep) && fs.lstatSync(target).isFile(),'Artifact must be a regular local file')
    return fs.readFileSync(target)
}
function inventory(root,relative='') {
    return fs.readdirSync(path.join(root,relative),{withFileTypes:true}).flatMap(entry=>{
        assert(!entry.isSymbolicLink(),'Artifact tree contains a link')
        const child=path.join(relative,entry.name)
        if(entry.isDirectory()) return inventory(root,child)
        assert(entry.isFile(),'Unexpected artifact entry')
        return [child.split(path.sep).join('/')]
    }).sort()
}
function verifyEntries(root,prefix,entries,allowedExtra=[]) {
    assert(Array.isArray(entries) && entries.length>0)
    const seen=new Set()
    for(const entry of entries) {
        assert(!seen.has(entry.path)); seen.add(entry.path)
        assert.match(entry.sha256,hex)
        const bytes=readFile(root,path.join(prefix,entry.path))
        assert.equal(sha(bytes),entry.sha256,'Artifact content changed')
        if(entry.bytes!==undefined) assert.equal(bytes.length,entry.bytes)
    }
    assert.deepEqual(inventory(path.join(root,prefix)),[...seen,...allowedExtra].sort(),'Unlisted or missing artifact files')
}
export function verifyProductionRun(directory,expectedProfile,expectedCandidate) {
    const root=fs.realpathSync(directory)
    const read=name=>readFile(root,name)
    const json=name=>JSON.parse(read(name).toString('utf8'))
    const manifest=json('run-manifest.json'), manifestSha256=sha(read('run-manifest.json'))
    assert.equal(manifest.profile,expectedProfile)
    assert.equal(manifest.candidateSha256,expectedCandidate)
    assert.match(manifest.runId,/^[a-f0-9-]{36}$/)
    const bound=(file,key)=>{
        assert.equal(sha(read(file)),manifest[key],'Bound manifest changed: '+key)
        return json(file)
    }
    verifyEntries(root,'runner',bound('runner-manifest.json','runnerManifest'))
    verifyEntries(root,'tested-source',bound('source-manifest.json','sourceManifest'))
    verifyEntries(root,'contexts',bound('contexts-manifest.json','contextsManifest'))
    const images=bound('images.json','imagesManifest')
    for(const role of ['backend','pg','redis','proxy','runner']) assert.match(images[role],/^sha256:[a-f0-9]{64}$/)
    const supervisor=bound('supervisor-manifest.json','supervisorManifest')
    verifyEntries(root,'supervisor-build',supervisor.sources,['Dockerfile'])
    assert.equal(sha(read('supervisor-build/Dockerfile')),supervisor.dockerfileSha256)
    assert.equal(sha(read('supervisor')),manifest.supervisor)
    assert.equal(supervisor.binarySha256,manifest.supervisor)
    assert.match(supervisor.compiler,/^golang@sha256:[a-f0-9]{64}$/)
    assert.match(supervisor.compilerImage,/^sha256:[a-f0-9]{64}$/)
    const migrations=bound('migration-manifest.json','migrationManifest')
    assert.equal(migrations.jarSha256,manifest.jarSha256)
    assert(Array.isArray(migrations.migrations) && migrations.migrations.length>0)
    const identity=candidateIdentity(read('contexts/backend/distribution/bin/server').toString('utf8'),
        name=>sha(read('contexts/backend/distribution/lib/'+name)))
    assert.equal(identity.serverJarSha256,manifest.jarSha256)
    assert.equal(identity.classpathSha256,manifest.classpathSha256)
    assert.equal(identity.candidateSha256,manifest.candidateSha256)
    assert.deepEqual(identity.jarFiles,manifest.backendClasspath)
    const seed=json('seed-receipt.json'), committed=json('admission-observation.json')
    assert(seed.productionSeeded===true && seed.runId===manifest.runId &&
        seed.manifestSha256===manifestSha256 && committed.replayRefused===true && committed.state==='ACTIVE')
    for(const key of ['topologySha256','policySha256']) {
        assert.match(seed[key],hex)
        assert.equal(seed[key],committed[key])
    }
    assert(seed.admissionGeneration>0 && seed.admissionGeneration===committed.generation)
    assert.deepEqual(json('cleanup.json'),{containers:0,networks:0,volumes:0,images:0})
    const local=json('local-receipt.json')
    const topologyKinds=['topology-preflight','topology-presence','topology-outages','topology-lanes','topology-drain']
    if(expectedProfile==='topology') assert.deepEqual(local.verifiedCases,topologyKinds)
    else assert.equal(local.productionProfileAccepted,true)
    assert(local.profile===expectedProfile &&
        local.manifestSha256===manifestSha256 && local.jarSha256===manifest.jarSha256)
    const replacement=json('worker-address-replacement.json')
    assert(replacement.addressChanged===true && replacement.previous!==replacement.replacement &&
        replacement.sameIngressContainer==='room-pilot-'+manifest.runId.replaceAll('-','').slice(0,12)+'-a')
    const lines=read('runner.log').toString('utf8').split(/\r?\n/)
    const kinds=expectedProfile==='topology'?[...topologyKinds,'latency']:[expectedProfile==='fault'?'fault':'load','latency','topology-preflight']
    const prefixes=kinds.map(kind=>'FTW_PRODUCTION_REHEARSAL_'+kind.toUpperCase()+' ')
    assert(lines.every(line=>!line.startsWith('FTW_PRODUCTION_REHEARSAL_') || prefixes.some(prefix=>line.startsWith(prefix))),'Unexpected failure or receipt kind')
    const receipts={}
    for(const kind of kinds) {
        const bytes=readProductionReceipt(lines,kind,expectedProfile,manifestSha256,manifest.jarSha256)
        assert(bytes.equals(read(kind+'.json')),'Retained receipt differs from frozen runner export')
        receipts[kind]=JSON.parse(bytes)
    }
    if(expectedProfile==='topology') {
        const drain=receipts['topology-drain'].payload
        assert.equal(drain.active,committed.generation)
        assert.equal(drain.candidate,manifest.candidateSha256)
        assert.equal(drain.classpath,manifest.classpathSha256)
        assert.equal(drain.topology,committed.topologySha256)
        assert.equal(drain.policy,committed.policySha256)
        assert.deepEqual(drain.nodes.map(node=>node.node),['a','b'].map(role=>manifest.topology.nodeIds[role].replaceAll('-','')))
    } else assert(receipts.latency.payload.nodes.every(node=>node.complete===true),'Incomplete latency probes')
    assert.equal(receipts['topology-preflight'].payload.admissionGeneration,committed.generation)
    return {manifest,manifestSha256,supervisor,root}
}

export function verifyProductionRuns(faultDirectory,loadDirectory,expectedCandidate,topologyDirectory) {
    const fault=verifyProductionRun(faultDirectory,'fault',expectedCandidate)
    const load=verifyProductionRun(loadDirectory,'two-backend',expectedCandidate)
    const frozenArtifactsSha256=productionArtifactAgreement(fault.manifest,load.manifest,expectedCandidate)
    const runs=[fault,load]
    if(topologyDirectory) {
        const topology=verifyProductionRun(topologyDirectory,'topology',expectedCandidate)
        assert.equal(productionArtifactAgreement(fault.manifest,topology.manifest,expectedCandidate),frozenArtifactsSha256)
        assert.notEqual(load.manifest.runId,topology.manifest.runId)
        runs.push(topology)
    }
    for(const run of runs.slice(1)) {
        for(const key of ['compiler','compilerImage','dockerfileSha256','sources'])
            assert.deepEqual(fault.supervisor[key],run.supervisor[key],'Supervisor build drift')
    }
    const scope=topologyDirectory?'local-fault-load-topology':'local-fault-load-with-golden-topology-preflight'
    return {schema:1,scope,candidateSha256:expectedCandidate,frozenArtifactsSha256,
        profiles:runs.map(({manifest,manifestSha256})=>({profile:manifest.profile,runId:manifest.runId,manifestSha256}))}
}

if(process.argv[1] && import.meta.url===pathToFileURL(process.argv[1]).href) {
    try {
        const [candidate,fault,load,output,topology,...extra]=process.argv.slice(2)
        assert(candidate && fault && load && output && extra.length===0)
        const proof=verifyProductionRuns(fault,load,candidate,topology)
        fs.writeFileSync(output,JSON.stringify(proof,null,2)+'\n',{flag:'wx'})
        console.log('Verified frozen local fault and load evidence')
    } catch { console.error('Incomplete or mismatched frozen production rehearsal evidence'); process.exitCode=1 }
}