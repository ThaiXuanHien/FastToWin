import test from 'node:test';
import assert from 'node:assert/strict';
import cp from 'node:child_process';
import {retentionEvidence,filesystemEvidence,finalizeCleanup,restartGapEvidence,diskRetentionEvidence} from './redis-cloud-evidence.mjs';

test('disk expiry requires old on complete before snapshot, absent after and recent positive control',()=>{
  const input={beforeTimes:[20000,35000],afterTimes:[80000],older:[10000,40000],newer:[70000,90000],beforeComplete:true,afterComplete:true};
  assert.equal(diskRetentionEvidence(input).passed,true);
  for(const amendment of [{beforeTimes:[]},{afterTimes:[]},{afterTimes:[20000,80000]},{beforeComplete:false},{afterComplete:false}])assert.equal(diskRetentionEvidence({...input,...amendment}).passed,false);
});

test('Owner restart-gap contract accepts fresh original timestamps with or without historical backfill',()=>{
  const input={restart:60000,now:90000,receivedTimes:[20000,65000]};
  assert.equal(restartGapEvidence(input).passed,true);
  assert.equal(restartGapEvidence({...input,receivedTimes:[65000]}).passed,true);
  for(const receivedTimes of [[],[20000],[100000]])assert.equal(restartGapEvidence({...input,receivedTimes}).passed,false);
  assert.equal(restartGapEvidence(input).durableReplayPromised,false);
});

test('expiry requires queued older/newer controls and original retained timestamps',()=>{
  const input={older:[10000,40000],newer:[60000,85000],queuedOlder:true,queuedNewer:true};
  assert.equal(retentionEvidence({...input,samples:[]}).passed,false);
  assert.equal(retentionEvidence({...input,samples:[[70,'1']]}).passed,true);
  assert.equal(retentionEvidence({...input,samples:[[20,'1'],[70,'1']]}).passed,false);
  assert.equal(retentionEvidence({...input,queuedOlder:false,samples:[[70,'1']]}).passed,false);
  assert.equal(retentionEvidence({...input,queuedNewer:false,samples:[[70,'1']]}).passed,false);
  assert.equal(retentionEvidence({...input,samples:[[90,'1']]}).passed,false);
});

test('filesystem evidence distinguishes delivery gap, continued delivery and fresh recovery',()=>{
  const input={fullAt:10000,freeAt:60000,checkedAt:70000,fill:{code:'ENOSPC'},diskFailureObserved:true,
    observations:[{at:15000,latest:12000},{at:55000,latest:12000}],recoveredSamples:[[12,'1'],[65,'1']]};
  const result=filesystemEvidence(input);
  assert.equal(result.passed,true);assert.equal(result.gapObserved,true);
  assert.deepEqual(result.freshRecoverySampleTimes,[65000]);
  assert.equal(filesystemEvidence({...input,recoveredSamples:[[12,'1']]}).passed,false);
  assert.equal(filesystemEvidence({...input,recoveredSamples:[[90,'1']]}).passed,false);
  const continuous=filesystemEvidence({...input,observations:[{at:55000,latest:52000}]});
  assert.equal(continuous.passed,false);assert.equal(continuous.gapObserved,false);
  assert.equal(continuous.continuedFreshDelivery,true);
  assert.equal(filesystemEvidence({...input,diskFailureObserved:false}).passed,false);
});

test('cleanup attempts every resource and secret, saves both failed receipts and throws',()=>{
  const tried=[],saved={},receipt={gatesPassed:true,completed:false};
  assert.throws(()=>finalizeCleanup(receipt,{
    actions:['container','volume','network','image','secret'].map(name=>({name,run:()=>{tried.push(name);if(name==='image')throw new Error('injected');}})),
    checks:[{name:'image',empty:()=>false}],
    persistCleanup:r=>saved.cleanup=r,persistAcceptance:r=>saved.acceptance={...r}
  }),/owned_cleanup_incomplete/);
  assert.deepEqual(tried,['container','volume','network','image','secret']);
  assert.equal(saved.cleanup.complete,false);assert.equal(saved.acceptance.completed,false);
  assert.equal(saved.acceptance.cleanupComplete,false);
});

test('cleanup success preserves gate decision; leftover or failed discovery cannot pass',()=>{
  for(const gatesPassed of [true,false]){
    const receipt={gatesPassed};
    const r=finalizeCleanup(receipt,{actions:[],checks:[{name:'all',empty:()=>true}],persistCleanup:()=>{},persistAcceptance:()=>{}});
    assert.equal(r.complete,true);assert.equal(receipt.completed,gatesPassed);
  }
  assert.throws(()=>finalizeCleanup({gatesPassed:true},{actions:[],checks:[{name:'discovery',empty:()=>{throw Error();}}],persistCleanup:()=>{},persistAcceptance:()=>{}}),/owned_cleanup_incomplete/);
});

test('local runner exits nonzero on cleanup failure after persisting both outcomes',()=>{
  const url=new URL('./redis-cloud-evidence.mjs',import.meta.url).href;
  const source=`import {finalizeCleanup} from ${JSON.stringify(url)};
    const saved={};try{finalizeCleanup({gatesPassed:true},{actions:[{name:'image',run:()=>{throw Error();}},{name:'secret',run:()=>{saved.secretAttempted=true;}}],checks:[{name:'image',empty:()=>false}],persistCleanup:r=>saved.cleanup=r,persistAcceptance:r=>saved.acceptance=r});}catch{console.log(JSON.stringify(saved));process.exitCode=1;}`;
  const child=cp.spawnSync(process.execPath,['--input-type=module','-e',source],{encoding:'utf8',windowsHide:true});
  assert.equal(child.status,1);const saved=JSON.parse(child.stdout);
  assert.equal(saved.secretAttempted,true);assert.equal(saved.cleanup.complete,false);assert.equal(saved.acceptance.completed,false);
});
