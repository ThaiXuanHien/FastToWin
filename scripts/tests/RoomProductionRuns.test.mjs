import test from 'node:test'
import assert from 'node:assert/strict'
import { productionArtifactAgreement } from '../verify-room-production-runs.mjs'

function manifest(profile) {
    return {profile,runId:profile==='fault'?'00000000-0000-4000-8000-000000000001':'00000000-0000-4000-8000-000000000002',
        jarSha256:'1'.repeat(64),classpathSha256:'2'.repeat(64),candidateSha256:'3'.repeat(64),
        runnerManifest:'4'.repeat(64),sourceManifest:'5'.repeat(64),contextsManifest:'6'.repeat(64),
        migrationManifest:'7'.repeat(64),supervisor:'8'.repeat(64),
        backendClasspath:[{name:'server.jar',sha256:'1'.repeat(64)}],
        topology:{publicIngress:'a',privateWorker:'b',nodeIds:{a:'a',b:'b'}},
        limits:{a:['3','1000000000'],b:['3','1000000000'],runner:['0.5','500000000']},
        accounts:40,rooms:20,offerSeconds:600,policy:'production-defaults',
        sourceIps:'synthetic-per-account-forwarded-in-isolated-network'}
}
test('independent run IDs can share exactly one frozen candidate and harness',()=>{
    const first=manifest('fault'),second=manifest('two-backend')
    assert.match(productionArtifactAgreement(first,second,'3'.repeat(64)),/^[a-f0-9]{64}$/)
})
test('a matching JAR cannot hide runner source classpath migration or cap drift',()=>{
    for(const patch of [
        {runnerManifest:'a'.repeat(64)},{sourceManifest:'a'.repeat(64)},{classpathSha256:'a'.repeat(64)},
        {contextsManifest:'a'.repeat(64)},{migrationManifest:'a'.repeat(64)},{supervisor:'a'.repeat(64)},
        {limits:{a:['6','2000000000']}},{backendClasspath:[]},{topology:{publicIngress:'b'}},
        {offerSeconds:60},{rooms:10},{accounts:20},{policy:'relaxed'}
    ]) assert.throws(()=>productionArtifactAgreement(manifest('fault'),{...manifest('two-backend'),...patch},'3'.repeat(64)))
    assert.throws(()=>productionArtifactAgreement(manifest('fault'),manifest('two-backend'),'a'.repeat(64)))
    assert.throws(()=>productionArtifactAgreement(manifest('fault'),{...manifest('two-backend'),runId:manifest('fault').runId},'3'.repeat(64)))
})