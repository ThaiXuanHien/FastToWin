// Pure local acceptance decisions. Missing positive evidence never means pass.
export function allocationEvidence({fill,snapshot}) {
  const mount=snapshot?.mount,filesystem=snapshot?.filesystem,filler=snapshot?.filler;
  const passed=mount?.target==='/var/lib/alloy'&&mount?.filesystem==='tmpfs'&&filesystem?.type===0x01021994&&
    filesystem.totalBytes===67108864&&filesystem.freeBytes===0&&filesystem.availableBytes===0&&
    fill?.code==='ENOSPC'&&Number.isSafeInteger(fill.bytes)&&fill.bytes>0&&filler?.bytes===fill.bytes&&filler.allocatedBytes>=fill.bytes;
  return {passed:!!passed,scope:'same64MiB tmpfs allocation precondition; not yet an Alloy failure'};
}

export function restartGapEvidence({restart,now,receivedTimes}) {
  const freshOriginalSampleTimes=receivedTimes.filter(t=>Number.isSafeInteger(t)&&t>=restart&&t<=now&&now-t<=60000);
  return {passed:freshOriginalSampleTimes.length>0,freshOriginalSampleTimes,
    historicalGapAllowed:true,durableReplayPromised:false,contractRevision:'Owner 2026-11-03: accept restart gap'};
}

export function diskRetentionEvidence({beforeTimes,afterTimes,older,newer,beforeComplete,afterComplete}) {
  const oldBefore=beforeTimes.filter(t=>t>=older[0]&&t<older[1]);
  const oldAfter=afterTimes.filter(t=>t>=older[0]&&t<older[1]);
  const recentAfter=afterTimes.filter(t=>t>=newer[0]&&t<newer[1]);
  return {passed:beforeComplete&&afterComplete&&oldBefore.length>0&&oldAfter.length===0&&recentAfter.length>0,
    beforeComplete,afterComplete,older,newer,oldBefore,oldAfter,recentAfter,
    scope:'disk WAL including all checkpoints; not wire TTL or memory-queue expiry'};
}

export function retentionEvidence({samples,older,newer,queuedOlder,queuedNewer}) {
  const times=samples.map(([t])=>t*1000);
  const olderTimes=times.filter(t=>t>=older[0]&&t<older[1]);
  const retainedTimes=times.filter(t=>t>=newer[0]&&t<newer[1]);
  return {passed:queuedOlder&&queuedNewer&&retainedTimes.length>0&&olderTimes.length===0,
    queuedOlder,queuedNewer,older, newer,expiredWindowSamples:olderTimes,retainedOriginalSampleTimes:retainedTimes};
}

export function filesystemEvidence({fullAt,freeAt,checkedAt,observations,recoveredSamples,diskFailureObserved,fill}) {
  // Each observation is the latest received original timestamp at a query time.
  const during=observations.filter(o=>o.at>=fullAt&&o.at<freeAt);
  const gap= during.some(o=>o.latest===null||o.at-o.latest>30000);
  const continued=during.length>0&&during.every(o=>o.latest!==null&&o.at-o.latest<=30000);
  const fresh=recoveredSamples.filter(([t])=>t*1000>=freeAt&&t*1000<=checkedAt&&checkedAt-t*1000<=60000).map(([t])=>t*1000);
  const duringTimes=recoveredSamples.filter(([t])=>t*1000>=fullAt&&t*1000<freeAt).map(([t])=>t*1000);
  return {passed:fill.code==='ENOSPC'&&diskFailureObserved&&gap&&fresh.length>0,
    fullAt,freeAt,checkedAt,observations,gapObserved:gap,continuedFreshDelivery:continued,
    freshRecoverySampleTimes:fresh,receivedFullIntervalSampleTimes:duringTimes,
    dataDisposition:gap?(duringTimes.length?'some full-interval samples retained; loss not established':'no full-interval samples received within bounded check; loss versus delayed replay unresolved'):'no delivery gap demonstrated'};
}

export function finalizeCleanup(receipt,{actions,checks,persistCleanup,persistAcceptance}) {
  const cleanup={at:new Date().toISOString(),attempts:[],checks:[],complete:false};
  for(const {name,run} of actions){try{run();cleanup.attempts.push({name,ok:true});}catch{cleanup.attempts.push({name,ok:false});}}
  for(const {name,empty} of checks){try{cleanup.checks.push({name,empty:empty()===true});}catch{cleanup.checks.push({name,empty:false});}}
  cleanup.complete=cleanup.attempts.every(a=>a.ok)&&cleanup.checks.every(c=>c.empty);
  try{persistCleanup(cleanup);}catch{cleanup.complete=false;}
  receipt.cleanupComplete=cleanup.complete;
  receipt.completed=cleanup.complete&&receipt.gatesPassed===true;
  if(!cleanup.complete){receipt.completed=false;receipt.cleanupError='owned_cleanup_incomplete';}
  persistAcceptance(receipt);
  if(!cleanup.complete)throw new Error('owned_cleanup_incomplete');
  return cleanup;
}
