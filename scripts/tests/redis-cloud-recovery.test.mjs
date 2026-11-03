import test from 'node:test';
import assert from 'node:assert/strict';
import {provisionDecoders} from './redis-cloud-fixture.mjs';
import {preservedWalEvidence,manualRecoveryEvidence,classifyRecoveryLogs,forceStopHeldSender,forcedStopEvidence} from './redis-cloud-recovery.mjs';

test('forced stop requires observed exit137, stopped state and no OOM; command targets exact sender',()=>{
  const observed={exitCode:137,oomKilled:false,running:false};
  assert.equal(forcedStopEvidence(observed).passed,true);
  for(const change of [{exitCode:0},{oomKilled:true},{running:true}])assert.equal(forcedStopEvidence({...observed,...change}).passed,false);
  const calls=[];forceStopHeldSender((...args)=>calls.push(args),'owned-sender');
  assert.deepEqual(calls,[[['kill','--signal','KILL','owned-sender'],10]]);
});

test('all shipping/storage/recovery modes provision both decoder consumers; UI stays independent',()=>{
  for(const mode of ['shipping','waldiagnostic','storage','fullonly','fullrecovery']){
    const copies=[];provisionDecoders({repo:'repo',artifact:'owned',mode,copyFile:(...args)=>copies.push(args)});
    assert.equal(copies.length,2,mode);assert.ok(copies.some(([,to])=>to.endsWith('decoder.exe')));
  }
  provisionDecoders({repo:'repo',artifact:'owned',mode:'ui',copyFile:()=>assert.fail('UI decoder coupling')});
});

const state={senderContainerId:'a'.repeat(64),volumeName:'owned',holderVolumeName:'owned',senderVolumeName:'owned',holderRunning:true,driver:'local',options:{type:'tmpfs',device:'tmpfs',o:'size=64m,uid=65532,gid=65532,mode=0700'},complete:true,files:[{name:'00000001',bytes:100,sha256:'a'.repeat(64)}]};
test('preserved WAL guard rejects empty/replaced/changed or unheld buffers',()=>{
  assert.equal(preservedWalEvidence({before:state,after:{...state,senderStopped:true}}).passed,true);
  for(const amendment of [{files:[]},{files:[{...state.files[0],sha256:'b'.repeat(64)}]},{volumeName:'new'},{senderContainerId:'b'.repeat(64)},{holderRunning:false},{senderStopped:false},{complete:false}])assert.equal(preservedWalEvidence({before:state,after:{...state,senderStopped:true,...amendment}}).passed,false);
});

test('startup repair is a stop condition while ordinary replay is not a repair claim',()=>{
  assert.equal(classifyRecoveryLogs('replaying WAL; WAL segment loaded; Done replaying WAL').repairOrDiscard,false);
  for(const logs of ['encountered WAL read error, attempting repair','WAL repair failed, truncating!','corruption detected','found sample referencing non-existing series skipped_series=3'])assert.equal(classifyRecoveryLogs(logs).repairOrDiscard,true);
});

test('manual recovery requires preserved buffer, fresh original receipt, same config/caps and no repair',()=>{
  const input={stopQualified:true,preserved:true,restart:60000,now:90000,receivedTimes:[65000],configUnchanged:true,repairOrDiscard:false,memoryCap:268435456,nanoCPUs:100000000,oomKilled:false};
  assert.equal(manualRecoveryEvidence(input).passed,true);
  for(const change of [{stopQualified:false},{preserved:false},{receivedTimes:[]},{receivedTimes:[20000]},{receivedTimes:[100000]},{repairOrDiscard:true},{configUnchanged:false},{oomKilled:true},{memoryCap:536870912}])assert.equal(manualRecoveryEvidence({...input,...change}).passed,false);
});
