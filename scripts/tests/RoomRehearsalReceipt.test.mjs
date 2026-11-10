import test from 'node:test'
import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { gate, readReceipt } from '../read-room-rehearsal-receipt.mjs'

const manifest = 'c'.repeat(64)
const jar = '8fff8ae123902032d98a92190b0bff3df87e234f213b25a298f4768c7e40e818'
function chunks(json, kind) {
    const data = Buffer.from(json)
    const hash = createHash('sha256').update(data).digest('hex')
    const parts = data.toString('base64').match(/.{1,2048}/g)
    return parts.map((part,i) => `FTW_REHEARSAL_${kind.toUpperCase()} ${i+1}/${parts.length} ${hash} ${part}`)
}
const envelope = (kind, profile, payload) => ({schema:1,manifestSha256:manifest,jarSha256:jar,kind,profile,payload})
const failure = envelope('failure','fault',{phase:'fault',category:'assertion',completedCases:2,progress:null})
const read = (receipt, kind=receipt.kind, profile=receipt.profile) => readReceipt(chunks(JSON.stringify(receipt),kind), kind,profile,manifest)
const load = envelope('load','two-backend',{
    pools:[20,20], seed:73,backendCount:2,concurrentRooms:20,configuredDurationSeconds:600,configuredSelectionsPerRoomPerSecond:5,
    roomSetupSeconds:4,elapsedSeconds:600,selectionCount:60000,selectionsByRoomSlot:Array(20).fill(3000),
    actualSelectionsPerSecond:100,completedMatches:1200,commandP95Millis:12,commandMaxMillis:300,
    commandAttempts:60001,commandRetries:1,minimumSelectionSpacingMillis:55,maxScheduledLatenessMillis:300,scheduledToAckP95Millis:50,
    recoveryMillis:[9000,9500,9800], recoveryP95Millis:9800,maxQueuedEventSeconds:1,maxPostgresPoolSaturation:0.8,
    queuedEventP95Seconds:1,postgresPoolP95Saturation:0.8,resourceSamples:Array.from({length:70},(_,i)=>({elapsedSeconds:i*600/69,queuedEventSeconds:1,postgresPoolSaturation:0.8})),
    resourceSampleCount:70,fullAcceptanceProfile:true,finalBackendMetrics:[{fasttowin_room_ownership_pool_max_connections:20},{fasttowin_room_ownership_pool_max_connections:20}]
})

test('valid failure and multi-chunk load decode identical bytes, including JSON provider lines',()=>{
    assert.equal(read(failure).toString(),JSON.stringify(failure))
    const lines = chunks(JSON.stringify(load),'load').reverse().map(message=>JSON.stringify({message}))
    assert.equal(readReceipt(lines,'load','two-backend',manifest).toString(),JSON.stringify(load))
})

test('wrong manifest, incomplete, mixed, corrupt, or conflicting chunks cannot pass',()=>{
    const lines = chunks(JSON.stringify(load),'load')
    const check = (xs,m=manifest) => assert.throws(()=>readReceipt(xs,'load','two-backend',m))
    check(lines.slice(1)); check([...lines,lines[0].replace(/ [0-9a-f]{64} /,' '+'a'.repeat(64)+' ')])
    check([...lines,lines[0].slice(0,-2)+'AA']); check(lines.map(l=>l.replace(/ [0-9a-f]{64} /,' '+'0'.repeat(64)+' ')))
    check(lines,'b'.repeat(64))
})
test('forged fields, duplicates, raw identities, tokens, SQL, error strings, or wrong number types fail',()=>{
    for (const payload of [{...failure.payload,rawSql:'SELECT 1'}, {...failure.payload,category:'database secret: connection reset'},
        {...failure.payload,phase:'11111111-1111-4111-8111-111111111111'}, {...failure.payload,category:'a'.repeat(64)},
        {...failure.payload,completedCases:'2'}, {...failure.payload,progress:{rawToken:'secret'}}]) {
        assert.throws(()=>read({...failure,payload}))
    }
    const duplicate = JSON.stringify(failure).replace('"phase":"fault"','"phase":"SECRET","phase":"fault"')
    assert.throws(()=>readReceipt(chunks(duplicate,'failure'),'failure','fault',manifest))
})
test('load gate rejects shortened runs, false totals, wrong topology, pool sizes, skipped outage and late recovery',()=>{
    for(const fields of [{pools:[20,10]},{backendCount:1},{configuredDurationSeconds:60},{selectionCount:6000},
        {selectionsByRoomSlot:Array(20).fill(2999)}, {elapsedSeconds:40}, {recoveryMillis:[10,20]},{recoveryMillis:[1,2,25001]},
        {fullAcceptanceProfile:false},{finalBackendMetrics:[{},{}]}, {finalBackendMetrics:[{rawSql:'SELECT 1'},{}]}])
        assert.throws(()=>read({...load,payload:{...load.payload,...fields}}))
})

const cases = [
    ['kill-after-committed-selection','backend-kill',{acknowledgedSelectionPreserved:true,quotaDebits:2}],
    ['selective-owner-postgres-partition','proxy-partition',{staleRequestPersisted:false,postTakeoverEpochRegressions:0}],
    ['kill-before-selection-commit','backend-kill',{controllerRowLockHeld:true,databaseBlockedSessions:1,requestCountAfterRetry:1}],
    ['kill-after-commit-before-reading-ack','backend-kill',{durableCommitObservedBeforeKill:true,requestCountAfterRetry:1}],
    ['start-response-loss','backend-kill',{sameDurableMatchAndBoard:true,quotaDebitsAfterReadyRetry:2}],
    ['settlement-response-loss','backend-kill',{selectionCount:50,quotaDebits:2,matchRows:1,settlementRows:1,walletRows:2}],
    ['redis-process-pause-fails-closed','redis-process-pause',{newConnectionAdmittedDuringOutage:false,idleAccountConnectedAfterRecovery:true,existingMatchQuotaDebits:2}],
    ['postgres-process-pause-no-optimistic-ack','postgres-process-pause',{outageAcceptedAck:false,requestAbsentBeforeRecoveryRetry:true,requestCountAfterRecovery:1,outageObservationMillis:1200}]
].map(([name,mechanism,proof],i)=>({name,mechanism,proof,passed:true,beforeEpoch:1,afterEpoch:2,beforeRevision:1,afterRevision:2,recoveryMillis:9000}))
const fault = envelope('fault','fault',{backendCount:2,pools:[20,20],cases})
test('eight exact cases and mechanisms, numeric proof and recovery bounds',()=>{
    assert.equal(read(fault).toString(),JSON.stringify(fault))
    for (const bad of [cases.slice(1),[...cases.slice(1),cases[1]],cases.map((c,i)=>i===6?{...c,name:'redis-stop-fails-closed'}:c),
        cases.map((c,i)=>i===1?{...c,recoveryMillis:25001}:c),cases.map((c,i)=>i===2?{...c,proof:{...c.proof,databaseBlockedSessions:0}}:c)])
        assert.throws(()=>read({...fault,payload:{...fault.payload,cases:bad}}))
})
const summary = {count:12,failures:0,meanMillis:1,maxMillis:2,p95Millis:2}
const latency = envelope('latency','fault',{nodes:[0,1].map(()=>({poolAcquire:summary,selectRoundTrip:summary,
    markerUpdateRoundTrip:summary,commitRoundTrip:summary,readinessRoundTrip:summary,complete:true,expectedIterations:12,
    scope:'preflight-fixture-probes-and-workload-timings',backendBeforeLoadMetrics:{fasttowin_room_ownership_pool_max_connections:20},fixtureDatabaseMetrics:{}}))})
test('both latency probes must have complete numeric distributions and safe labels',()=>{
    assert.equal(read(latency).toString(),JSON.stringify(latency))
    const bad = structuredClone(latency); bad.payload.nodes[1].poolAcquire.meanMillis = 'secret'
    assert.throws(()=>read(bad))
    const other = structuredClone(latency); other.payload.nodes[1].backendBeforeLoadMetrics.rawSql = 2
    assert.throws(()=>read(other))
})

test('combined gate binds both successes and both complete latency receipts to one expected manifest',()=>{
    const lines = receipt=>chunks(JSON.stringify(receipt),receipt.kind)
    const faults = [...lines(fault), ...lines(latency)]
    const loadLatency = {...latency,profile:'two-backend'}
    const loads = [...lines(load), ...lines(loadLatency)]
    assert.deepEqual([...gate(manifest,faults,loads).keys()], ['fault','fault-latency','load','two-backend-latency'])
    assert.throws(()=>gate(manifest,[],loads))
    assert.throws(()=>gate(manifest,faults,lines(load)))
    assert.throws(()=>gate(manifest,[...faults,...lines(failure)],loads))
    assert.throws(()=>gate(manifest,faults,[...loads,...lines({...load,manifestSha256:'d'.repeat(64)})]))
    const incomplete=structuredClone(loadLatency); incomplete.payload.nodes[0].complete=false
    assert.throws(()=>gate(manifest,faults,[...lines(load),...lines(incomplete)]))
})

test('identical retransmission of any chunk preserves the exact receipt bytes',()=>{
    const lines=chunks(JSON.stringify(load),'load')
    assert.deepEqual(readReceipt([...lines, ...lines.slice().reverse()],'load','two-backend',manifest),Buffer.from(JSON.stringify(load)))
})

test('load resource samples cover the measured workload at its head and tail',()=>{
    function sampled(times) {
        const samples=times.map(elapsedSeconds=>({elapsedSeconds,queuedEventSeconds:1,postgresPoolSaturation:0.8}))
        return {...load,payload:{...load.payload,resourceSamples:samples,resourceSampleCount:samples.length}}
    }
    assert.equal(read(sampled([0,599.8])).toString(), JSON.stringify(sampled([0,599.8])))
    for (const times of [[0,0],[10,600],[0,300],[0,599.7]]) assert.throws(()=>read(sampled(times)))
})
for (const [description,key,stream] of [
    ['plain duplicate hides failure','message','failure'],
    ['escaped duplicate hides failure','mess\\u0061ge','failure'],
    ['escaped duplicate hides corrupt chunk','\\u006dessage','corrupt']
]) {
    test(description,()=>{
        const hidden = stream==='failure' ? chunks(JSON.stringify(failure),'failure')[0] : 'FTW_REHEARSAL_FAULT corrupt'
        const duplicate = '{"message":'+JSON.stringify(hidden)+',"'+key+'":"ordinary text","meta":{"level":"info"}}'
        const faults = [duplicate,...chunks(JSON.stringify(fault),'fault'),...chunks(JSON.stringify(latency),'latency')]
        const loads = [...chunks(JSON.stringify(load),'load'),...chunks(JSON.stringify({...latency,profile:'two-backend'}),'latency')]
        assert.throws(()=>gate(manifest,faults,loads))
    })
}
test('unambiguous escaped provider message and nested metadata preserve exact bytes',()=>{
    const lines=chunks(JSON.stringify(load),'load').map(message=>
        JSON.stringify({meta:{message:'metadata',labels:['a"b','c\\d','}']}, message})
            .replace('"message":"FTW_','"mess\\u0061ge":"\\u0046TW_'))
    assert.deepEqual(readReceipt(lines,'load','two-backend',manifest),Buffer.from(JSON.stringify(load)))
})
