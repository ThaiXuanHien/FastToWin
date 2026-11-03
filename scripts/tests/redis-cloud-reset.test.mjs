import test from 'node:test';
import assert from 'node:assert/strict';
import {RESET_ACK,WAL_TARGET,validateResetScope,resetFreshEvidence,resetProgram} from './redis-cloud-reset.mjs';
import {fixtureModeOptions,provisionDecoders} from './redis-cloud-fixture.mjs';

const id='a'.repeat(32),senderId='b'.repeat(64),holderId='c'.repeat(64),name='ftw-cloud-held-'+id;
const labels={'fasttowin.redis-cloud-run':id};
const mount={Destination:'/var/lib/alloy',Name:name,Type:'volume'};
const input={id,target:'/var/lib/alloy/prometheus.remote_write.grafana/wal',ack:'monitoring-only unsent history deliberately discarded; historical backfill not promised',full:senderId,holderId,
  volume:{Name:name,Labels:labels,Driver:'local',Options:{device:'tmpfs',type:'tmpfs',o:'size=64m,uid=65532,gid=65532,mode=0700'}},
  sender:{Id:senderId,Config:{Labels:labels},State:{Running:false,ExitCode:137,OOMKilled:false},HostConfig:{Memory:268435456,NanoCpus:100000000},Mounts:[mount]},
  holder:{Id:holderId,Config:{Labels:labels,User:'65532:65532'},State:{Running:true},HostConfig:{ReadonlyRootfs:true,NetworkMode:'none'},Mounts:[{...mount,RW:false}]},
  before:{complete:true,senderContainerId:senderId,volumeName:name,files:[{name:'00000001',bytes:100,sha256:'d'.repeat(64)}]},
  fault:{diskFailureObserved:true,gapObserved:true,availableBytes:4096}};

test('reset refuses wrong path, ownership, acknowledgement, capacity, stop or prior WAL',()=>{
  assert.equal(RESET_ACK,input.ack);assert.equal(WAL_TARGET,input.target);
  assert.equal(validateResetScope(input).passed,true);
  for(const change of [
    {target:'/var/lib/alloy'}, {target:'/var/lib/alloy/prometheus.remote_write.grafana/../wal'}, {target:'/data/appendonlydir'}, {ack:''},
    {volume:{...input.volume,Name:'redis-data'}}, {volume:{...input.volume,Labels:{}}}, {holder:{...input.holder,Id:'e'.repeat(64)}},
    {holder:{...input.holder,Mounts:[{...mount,RW:true}]}}, {sender:{...input.sender,Config:{Labels:{}}}},
    {sender:{...input.sender,State:{Running:false,ExitCode:0,OOMKilled:false}}}, {sender:{...input.sender,State:{Running:true,ExitCode:137,OOMKilled:false}}},
    {sender:{...input.sender,State:{Running:false,ExitCode:137,OOMKilled:true}}}, {before:{...input.before,files:[]}},
    {fault:{...input.fault,availableBytes:0}}, {fault:{...input.fault,gapObserved:false}}, {fault:{...input.fault,diskFailureObserved:false}},
  ])assert.throws(()=>validateResetScope({...input,...change}),/reset_scope_refused/);
});

test('FullReset routes to only held full-filesystem phase and keeps decoder dependency',()=>{
  assert.deepEqual(fixtureModeOptions('fullreset'),{diagnostic:false,storage:true,fullOnly:true,manualRecovery:true,resetWal:true});
  const copies=[];provisionDecoders({repo:'repo',artifact:'owned',mode:'fullreset',copyFile:(...args)=>copies.push(args)});assert.equal(copies.length,2);
  assert.match(resetProgram,/realpathSync/);assert.match(resetProgram,/rmSync\(target,/);assert.doesNotMatch(resetProgram,/rmSync\(root,/);
});

test('reset receipt requires deliberate loss acknowledgement, empty recreated WAL and two distinct fresh original samples',()=>{
  const good={ack:input.ack,emptyVerified:true,restart:60000,now:90000,times:[65000,80000],gapObserved:true,configUnchanged:true,sameVolume:true,stopQualified:true,memoryCap:268435456,nanoCPUs:100000000,oomKilled:false,repairOrDiscard:false};
  assert.equal(resetFreshEvidence(good).passed,true);
  for(const change of [{ack:''},{emptyVerified:false},{times:[65000,65000]},{times:[50000,80000]},{times:[65000,100000]},{gapObserved:false},{sameVolume:false},{stopQualified:false},{configUnchanged:false},{repairOrDiscard:true},{oomKilled:true},{memoryCap:536870912}])assert.equal(resetFreshEvidence({...good,...change}).passed,false);
});
