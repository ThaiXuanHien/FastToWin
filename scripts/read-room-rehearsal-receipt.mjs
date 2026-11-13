import { createHash } from 'node:crypto'
import { readFileSync, statSync, mkdirSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { pathToFileURL } from 'node:url'

const jar = '8fff8ae123902032d98a92190b0bff3df87e234f213b25a298f4768c7e40e818'
const kinds = ['load','fault','failure','latency','topology-preflight','topology-presence','topology-outages','topology-lanes','topology-drain']
const poolLabel = 'fasttowin_room_ownership_pool_max_connections'
const metrics = new Set([
    'fasttowin_uptime_seconds','fasttowin_websocket_connections','fasttowin_websocket_connections_total',
    'fasttowin_websocket_messages_total','fasttowin_websocket_rate_limited_total','fasttowin_jvm_memory_used_bytes',
    'fasttowin_jvm_memory_max_bytes','fasttowin_room_ownership_available_processors','fasttowin_room_ownership_takeovers_total',
    'fasttowin_room_ownership_fence_rejections_total','fasttowin_room_ownership_resyncs_total',
    'fasttowin_room_ownership_settlement_failures_total','fasttowin_room_ownership_oldest_queued_event_seconds',
    'fasttowin_room_ownership_pool_active_connections',poolLabel
])
for (const stage of ['pool_acquire','transaction_setup','transaction_body','statement_execute','row_decode','commit','rollback','setup_and_first_statement','read_transaction'])
    for (const suffix of ['count','sum','max','p95_upper_bound','failures_total'])
        metrics.add(`fasttowin_room_ownership_db_${stage}_seconds_${suffix}`)

function check(value) { if (!value) throw new Error('Invalid rehearsal receipt') }
const isObject = value => value !== null && typeof value === 'object' && !Array.isArray(value)
function keys(value, expected) {
    check(isObject(value) && Object.keys(value).length === expected.length && expected.every(key => Object.hasOwn(value,key)))
}
const num = (v,min=0,max=Number.MAX_SAFE_INTEGER) => Number.isFinite(v) && v >= min && v <= max
const integer = (v,min=0,max=Number.MAX_SAFE_INTEGER) => Number.isSafeInteger(v) && num(v,min,max)
const list = (v,size) => Array.isArray(v) && v.length === size
const pools = v => list(v,2) && v.every(x=>x===20)
function checkedMetrics(obj) { check(isObject(obj) && Object.entries(obj).every(([k,v])=>metrics.has(k) && num(v))) }
const sha = bytes => createHash('sha256').update(bytes).digest('hex')

// Visit every object key before using the decoded object. JSON.parse alone keeps only the last duplicate.
// Provider metadata may contain escaped strings; JSON-decode key tokens so escaped-equivalent names collide.
function strictJson(raw, providerWrapper=false) {
    if (!providerWrapper) check(!raw.includes('\\'))
    let parsed
    try { parsed = JSON.parse(raw) } catch { throw new Error('Malformed rehearsal JSON') }
    const stack = []
    for (const match of raw.matchAll(/("(?:\\[\s\S]|[^"\\])*")\s*(:)?|([{}\[\]])/g)) {
        const [,quotedKey,colon,delimiter] = match
        if (delimiter === '{' || delimiter === '[') {
            check(stack.length < (providerWrapper ? 64 : 16))
            stack.push(new Set())
        } else if (delimiter === '}' || delimiter === ']') {
            stack.pop()
        } else if (colon) {
            const key = JSON.parse(quotedKey)
            const seen = stack.at(-1)
            check(!seen.has(key))
            seen.add(key)
        }
    }
    return parsed
}

function validateLoad(p) {
    const counts = ['seed','backendCount','concurrentRooms','configuredDurationSeconds','configuredSelectionsPerRoomPerSecond',
        'selectionCount','completedMatches','commandAttempts','commandRetries','minimumSelectionSpacingMillis','resourceSampleCount']
    const numbers = ['roomSetupSeconds','elapsedSeconds','actualSelectionsPerSecond','commandP95Millis','commandMaxMillis',
        'maxScheduledLatenessMillis','scheduledToAckP95Millis','recoveryP95Millis','maxQueuedEventSeconds',
        'maxPostgresPoolSaturation','queuedEventP95Seconds','postgresPoolP95Saturation']
    keys(p,[...counts,...numbers,'pools','selectionsByRoomSlot','recoveryMillis','resourceSamples','fullAcceptanceProfile','finalBackendMetrics'])
    check(counts.every(k=>integer(p[k])) && numbers.every(k=>num(p[k])))
    check(pools(p.pools) && p.seed===73 && p.backendCount===2 && p.concurrentRooms===20 && p.configuredDurationSeconds===600 &&
        p.configuredSelectionsPerRoomPerSecond===5 && p.selectionCount===60000 && p.fullAcceptanceProfile===true &&
        list(p.selectionsByRoomSlot,20) && p.selectionsByRoomSlot.every(n=>n===3000) &&
        p.completedMatches===1200 && p.commandAttempts===60000+p.commandRetries && p.minimumSelectionSpacingMillis===55 &&
        num(p.elapsedSeconds,599.8,631) && p.actualSelectionsPerSecond>=95 &&
        Math.abs(p.actualSelectionsPerSecond - 60000/p.elapsedSeconds)<1e-6 &&
        list(p.recoveryMillis,3) && p.recoveryMillis.every(n=>integer(n,1,25000)) &&
        p.recoveryP95Millis===Math.max(...p.recoveryMillis) && p.commandP95Millis<=p.commandMaxMillis)
    check(integer(p.resourceSampleCount,1,1000) && list(p.resourceSamples,p.resourceSampleCount))
    let previous = 0
    for (const s of p.resourceSamples) {
        keys(s,['elapsedSeconds','queuedEventSeconds','postgresPoolSaturation'])
        check(num(s.elapsedSeconds,previous,p.elapsedSeconds) && num(s.queuedEventSeconds) && num(s.postgresPoolSaturation,0,1))
        previous = s.elapsedSeconds
    }
    // The fixture scrapes once before measurement (timestamp zero), then again after the final ACK.
    // Five scheduled selections per second put the last offer within one 0.2-second interval of 600s.
    // elapsedSeconds also includes subsequent economy checks; the scrape need not include those.
    check(p.resourceSamples[0].elapsedSeconds === 0 && previous >= p.configuredDurationSeconds - 1/p.configuredSelectionsPerRoomPerSecond)
    for (const [sample,p95,max] of [
        ['queuedEventSeconds','queuedEventP95Seconds','maxQueuedEventSeconds'],
        ['postgresPoolSaturation','postgresPoolP95Saturation','maxPostgresPoolSaturation']
    ]) {
        const sorted = p.resourceSamples.map(s=>s[sample]).sort((a,b)=>a-b)
        check(p[p95]===sorted[Math.ceil(sorted.length*0.95)-1] && p[max]===sorted.at(-1))
    }
    check(list(p.finalBackendMetrics,2))
    for (const m of p.finalBackendMetrics) { checkedMetrics(m); check(m[poolLabel]===20) }
}

const cases = new Map([
    ['kill-after-committed-selection',['backend-kill',{acknowledgedSelectionPreserved:true,quotaDebits:2}]],
    ['selective-owner-postgres-partition',['proxy-partition',{staleRequestPersisted:false,postTakeoverEpochRegressions:0}]],
    ['kill-before-selection-commit',['backend-kill',{controllerRowLockHeld:true,databaseBlockedSessions:n=>integer(n,1,1000),requestCountAfterRetry:1}]],
    ['kill-after-commit-before-reading-ack',['backend-kill',{durableCommitObservedBeforeKill:true,requestCountAfterRetry:1}]],
    ['start-response-loss',['backend-kill',{sameDurableMatchAndBoard:true,quotaDebitsAfterReadyRetry:2}]],
    ['settlement-response-loss',['backend-kill',{selectionCount:50,quotaDebits:2,matchRows:1,settlementRows:1,walletRows:2}]],
    ['redis-process-pause-fails-closed',['redis-process-pause',{newConnectionAdmittedDuringOutage:false,idleAccountConnectedAfterRecovery:true,existingMatchQuotaDebits:2}]],
    ['postgres-process-pause-no-optimistic-ack',['postgres-process-pause',{outageAcceptedAck:false,requestAbsentBeforeRecoveryRetry:true,requestCountAfterRecovery:1,outageObservationMillis:n=>integer(n,0,25000)}]]
])
function validateFault(p) {
    keys(p,['backendCount','pools','cases'])
    check(p.backendCount===2 && pools(p.pools) && list(p.cases,8))
    const seen = new Set()
    for (const c of p.cases) {
        keys(c,['name','passed','mechanism','beforeEpoch','afterEpoch','beforeRevision','afterRevision','recoveryMillis','proof'])
        check(c.passed===true && cases.has(c.name) && !seen.has(c.name)); seen.add(c.name)
        const [mechanism,proof] = cases.get(c.name)
        check(c.mechanism===mechanism && integer(c.beforeEpoch,1) && integer(c.afterEpoch,c.beforeEpoch) &&
            integer(c.beforeRevision) && integer(c.afterRevision,c.beforeRevision) && integer(c.recoveryMillis,1,25000))
        if (mechanism==='backend-kill' || mechanism==='proxy-partition') check(c.afterEpoch>c.beforeEpoch)
        keys(c.proof,Object.keys(proof))
        for (const [key,expected] of Object.entries(proof)) {
            check(typeof expected==='function' ? expected(c.proof[key]) : c.proof[key]===expected)
        }
    }
}

const workerPublicPaths = ['/game','/auth/login','/auth/register','/api/content-terms',
    '/api/announcements','/admin','/admin/api/maintenance','/gifts','/status']
function validateTopologyPreflight(p) {
    keys(p,['admissionGeneration','publicIngress','privateWorker','signedProcessesVerified',
        'publicSocketsByBackend','goldenOwners','workerRoutes'])
    check(integer(p.admissionGeneration,1) && p.publicIngress==='a' && p.privateWorker==='b' &&
        p.signedProcessesVerified===2 && list(p.publicSocketsByBackend,2) &&
        integer(p.publicSocketsByBackend[0],4) && p.publicSocketsByBackend[1]===0 &&
        list(p.goldenOwners,2) && p.goldenOwners[0]===0 && p.goldenOwners[1]===1 &&
        list(p.workerRoutes,workerPublicPaths.length))
    for (const [index,route] of p.workerRoutes.entries()) {
        keys(route,['path','get','post'])
        check(route.path===workerPublicPaths[index] && route.get===404 && route.post===404)
    }
}
function validateTopologyPresence(p) {
    keys(p,['accounts','friendEdges','clanMembers','cases'])
    check(p.accounts===40 && p.friendEdges===4 && p.clanMembers===5 && list(p.cases,2))
    const names=['created','joined','started','replaced','finished','host-left','guest-left']
    for (const [owner,c] of p.cases.entries()) {
        keys(c,['owner','stages'])
        check(c.owner===owner && list(c.stages,names.length))
        for (const [index,s] of c.stages.entries()) {
            keys(s,['name','friendPushMillis','clanPushMillis','readbacks'])
            check(s.name===names[index] && s.readbacks===2)
            for (const millis of [s.friendPushMillis,s.clanPushMillis])
                check(index===3 || index===4 ? millis===null : integer(millis,0,19999))
        }
    }
}

function validateTopologyOutages(p) {
    keys(p,['cases','workerPublicMetrics']); check(list(p.cases,2) && p.workerPublicMetrics===0)
    for(const [index,c] of p.cases.entries()) {
        const inside=index===0
        keys(c,['outcome','ownerBefore','ownerAfter','epochBefore','epochAfter','oldProcess','newProcess',
            'interruptedSockets','refusedConnections','claimDbMillis','recoveryDeadlineMillis','reconnectDbMillis',
            'closedAtMillis','returningGames','matchRows','originalSelections','quota','settlements','legacyRestoredOnA'])
        check(c.outcome===(inside?'inside':'outside') && c.ownerBefore===0 && c.ownerAfter===1 &&
            integer(c.epochBefore,1) && integer(c.epochAfter,c.epochBefore+1) &&
            [c.oldProcess,c.newProcess].every(v=>typeof v==='string' && /^[0-9a-f]{32}$/.test(v)) &&
            c.oldProcess!==c.newProcess && c.interruptedSockets===2 && c.refusedConnections===1 &&
            integer(c.claimDbMillis,1) && integer(c.recoveryDeadlineMillis,c.claimDbMillis+1,c.claimDbMillis+30000) &&
            integer(c.reconnectDbMillis,c.claimDbMillis) && c.quota===2 && c.originalSelections===1 && c.legacyRestoredOnA===(inside?1:0) &&
            c.settlements===(inside?1:0) && c.matchRows===(inside?1:0) && c.returningGames===(inside?2:0))
        if(inside) check(c.reconnectDbMillis<c.recoveryDeadlineMillis && c.closedAtMillis===null)
        else check(integer(c.closedAtMillis,c.recoveryDeadlineMillis,c.reconnectDbMillis))
    }
}

function validateTopologyLanes(p) {
    keys(p,['cases']); check(list(p.cases,4))
    for(const [index,c] of p.cases.entries()) {
        const commit=index%2===0
        keys(c,['operation','transaction','blockedTransactions','replyWithheld','generationBefore','generationAfter',
            'resumedOwnedRoom','legacyAdmitted','obsoleteSocketRejected','lateWrites'])
        check(c.operation===(index<2?'create':'join') && c.transaction===(commit?'commit':'rollback') &&
            c.blockedTransactions===2 && c.replyWithheld===true && integer(c.generationBefore,1) && c.generationAfter===c.generationBefore+1 &&
            c.resumedOwnedRoom===commit && c.legacyAdmitted===!commit && c.obsoleteSocketRejected===true && c.lateWrites===0)
    }
}

function validateTopologyDrain(p, jar) {
    keys(p,['active','stopped','acknowledged','disabled','playingDuringStop','waitingClosed','concurrentSelections','staleAdmissionsRejected','stopWaitedForAdmission','lateAdmissions','pendingOpen','pendingClosed',
        'closedOpenRooms','closedLiveLeases','settled','restartChanged','dependency','timedAwait','legacySelections','mode',
        'candidate','serverJar','classpath','topology','policy','nodes','openRooms','liveLeases','pendingSettlements'])
    check(integer(p.active,1) && p.stopped===p.active+1 && p.acknowledged===p.stopped+1 && p.disabled===p.acknowledged+1 &&
        p.playingDuringStop===1 && p.waitingClosed===3 && p.concurrentSelections===1 && p.staleAdmissionsRejected===3 && p.stopWaitedForAdmission===true && p.lateAdmissions===0 && p.pendingOpen===1 && p.pendingClosed===1 && p.settled===1 &&
        p.closedOpenRooms===0 && p.closedLiveLeases===0 && p.restartChanged===true && p.dependency==='unavailable' &&
        p.timedAwait==='deadline' && p.legacySelections===4 && p.mode==='INITIAL_DRAIN' && p.serverJar===jar &&
        [p.candidate,p.classpath,p.topology,p.policy].every(v=>typeof v ==='string' && /^[a-f0-9]{64}$/.test(v)) &&
        p.openRooms===0 && p.liveLeases===0 && p.pendingSettlements===0 && list(p.nodes,2))
    for(const n of p.nodes) {
        keys(n,['node','process','generation','quiesced'])
        check([n.node,n.process].every(v=>typeof v==='string' && /^[a-f0-9]{32}$/.test(v)) &&
            n.quiesced===true && n.generation===p.acknowledged)
    }
    check(p.nodes[0].node!==p.nodes[1].node && p.nodes[0].process!==p.nodes[1].process)
}

function validateLatency(p) {
    keys(p,['nodes']); check(list(p.nodes,2))
    const stages = ['poolAcquire','selectRoundTrip','markerUpdateRoundTrip','commitRoundTrip','readinessRoundTrip']
    for (const n of p.nodes) {
        keys(n,[...stages,'scope','complete','expectedIterations','backendBeforeLoadMetrics','fixtureDatabaseMetrics'])
        check(n.scope==='preflight-fixture-probes-and-workload-timings' && n.expectedIterations===12 && typeof n.complete==='boolean')
        for (const key of stages) {
            const v = n[key]
            keys(v,['count','meanMillis','maxMillis','p95Millis','failures'])
            check(integer(v.count,0,12) && integer(v.failures,0,v.count) && num(v.maxMillis) && num(v.meanMillis,0,v.maxMillis))
            if (v.count===0) check(v.p95Millis===null && v.meanMillis===0 && v.maxMillis===0)
            else check(num(v.p95Millis,0,v.maxMillis))
            if (n.complete) check(v.count===12 && v.failures===0)
        }
        checkedMetrics(n.fixtureDatabaseMetrics); checkedMetrics(n.backendBeforeLoadMetrics)
        if (n.complete) check(n.backendBeforeLoadMetrics[poolLabel]===20)
    }
}
function validateFailure(p) {
    keys(p,['phase','category','completedCases','progress'])
    check(['private-dns','fixture-marker-and-readiness','topology-preflight','topology-presence','topology-outages','topology-lanes','topology-drain','latency','load','fault','receipt'].includes(p.phase) &&
        ['timeout','database-connection','database-error','network','protocol-rejected','configuration','assertion','other'].includes(p.category) &&
        integer(p.completedCases,0,8))
    if (p.progress!==null) {
        const v=p.progress
        keys(v,['elapsedSeconds','selectionsByRoomSlot','completedMatches','commandAttempts'])
        check(num(v.elapsedSeconds) && list(v.selectionsByRoomSlot,20) && v.selectionsByRoomSlot.every(n=>integer(n,0,3000)) &&
            integer(v.completedMatches,0,1200) && integer(v.commandAttempts))
    }
}

function message(line) {
    if (line.trimStart().startsWith('{')) {
        const row = strictJson(line,true)
        if (Object.hasOwn(row,'message')) {
            check(typeof row.message==='string')
            return row.message
        }
    }
    return line
}
export function readReceipt(lines, kind, profile, manifest) {
    return readExpectedReceipt(lines, kind, profile, manifest, jar)
}

export function readProductionReceipt(lines, kind, profile, manifest, expectedJar) {
    check(/^[a-f0-9]{64}$/.test(expectedJar))
    const normalized = lines.map(message).map(line => {
        check(!line.startsWith('FTW_REHEARSAL_'))
        return line.replace(/^FTW_PRODUCTION_REHEARSAL_/, 'FTW_REHEARSAL_')
    })
    return readExpectedReceipt(normalized, kind, profile, manifest, expectedJar, true)
}

function readExpectedReceipt(lines, kind, profile, manifest, expectedJar, production=false) {
    check(kinds.includes(kind) && (['fault','two-backend'].includes(profile) || (production && profile==='topology')) && /^[0-9a-f]{64}$/.test(manifest))
    const prefix = 'FTW_REHEARSAL_'+kind.toUpperCase()+' '
    const parts = new Map()
    let digest, total
    for (const line of lines) {
        const value = message(line)
        if (!value.startsWith(prefix)) continue
        const match = /^([1-9][0-9]{0,2})\/([1-9][0-9]{0,2}) ([a-f0-9]{64}) ([A-Za-z0-9+/=]{1,2048})$/.exec(value.slice(prefix.length))
        check(match)
        const index=Number(match[1]), count=Number(match[2]), hash=match[3], part=match[4]
        check(index<=count && count<=171 && (digest===undefined || (digest===hash && total===count)) &&
            (!parts.has(index) || parts.get(index)===part))
        digest=hash; total=count; parts.set(index,part)
    }
    check(total>0 && parts.size===total)
    const encoded=Array.from({length:total},(_,i)=>parts.get(i+1)).join('')
    const bytes=Buffer.from(encoded,'base64')
    check(bytes.length<=262144 && bytes.toString('base64')===encoded && sha(bytes)===digest)
    const receipt=strictJson(new TextDecoder('utf-8',{fatal:true}).decode(bytes))
    keys(receipt,['schema','kind','profile','jarSha256','manifestSha256','payload'])
    check(receipt.schema===1 && receipt.kind===kind && receipt.profile===profile && receipt.jarSha256===expectedJar && receipt.manifestSha256===manifest)
    if (kind==='load') { check(profile==='two-backend'); validateLoad(receipt.payload) }
    if (kind==='fault') { check(profile==='fault'); validateFault(receipt.payload) }
    if (kind==='topology-presence') { check(production && profile==='topology'); validateTopologyPresence(receipt.payload) }
    if (kind==='topology-outages') { check(production && profile==='topology'); validateTopologyOutages(receipt.payload) }
    if (kind==='topology-lanes') { check(production && profile==='topology'); validateTopologyLanes(receipt.payload) }
    if (kind==='topology-drain') { check(production && profile==='topology'); validateTopologyDrain(receipt.payload,expectedJar) }
    if (kind==='latency') validateLatency(receipt.payload)
    if (kind==='topology-preflight') validateTopologyPreflight(receipt.payload)
    if (kind==='failure') validateFailure(receipt.payload)
    return bytes
}
function logLines(path) {
    check(statSync(path).size<=16*1024*1024)
    return readFileSync(path,'utf8').split(/\r?\n/)
}

// Both profiles must come from this host run's independently frozen manifest.
export function gate(manifest, faultLines, loadLines) {
    const decoded = new Map()
    for (const [profile, kind, lines] of [['fault','fault',faultLines],['two-backend','load',loadLines]]) {
        const allowed = [kind,'latency'].map(k=>'FTW_REHEARSAL_'+k.toUpperCase()+' ')
        check(lines.map(message).every(v=>!v.startsWith('FTW_REHEARSAL_') || allowed.some(prefix=>v.startsWith(prefix))))
        decoded.set(kind,readReceipt(lines,kind,profile,manifest))
        const latency=readReceipt(lines,'latency',profile,manifest)
        check(JSON.parse(latency).payload.nodes.every(n=>n.complete))
        decoded.set(profile+'-latency',latency)
    }
    return decoded
}

if (process.argv[1] && import.meta.url===pathToFileURL(process.argv[1]).href) {
    try {
        const [mode,...args]=process.argv.slice(2)
        if (mode==='gate' && args.length===4) {
            const [manifest,fault,load,out]=args
            const receipts=gate(manifest,logLines(fault),logLines(load))
            mkdirSync(out,{recursive:true})
            for(const [kind,bytes] of receipts) writeFileSync(join(out,kind+'.json'),bytes)
            console.log('Verified both rehearsal profiles and four checksums')
        } else {
            check(mode==='read' && args.length===5)
            const [manifest,profile,kind,log,out]=args
            writeFileSync(out,readReceipt(logLines(log),kind,profile,manifest))
            console.log('Verified rehearsal export')
        }
    } catch { console.error('Invalid or incomplete rehearsal evidence'); process.exitCode=1 }
}
