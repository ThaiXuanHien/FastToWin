import test from 'node:test';
import assert from 'node:assert/strict';
import {diagnoseRestart} from './redis-cloud-wal-diagnostic.mjs';

test('diagnosis requires two outage WAL timestamps, no old outgoing timestamps and fresh control',()=>{
  const input={outage:10000,restart:60000,walTimes:[20000,35000],now:90000,wire:[{at:70000,status:204,memoryTimes:[65000]}]};
  const result=diagnoseRestart(input);
  assert.equal(result.diagnosticConfirmed,true);assert.equal(result.replayAcceptancePassed,false);
  assert.deepEqual(result.queuedOutageTimes,[20000,35000]);
  for(const amended of [{walTimes:[20000]},{wire:[]},{wire:[{at:70000,status:204,memoryTimes:[20000,65000]}]},{wire:[{at:70000,status:204,decodeFailed:true}]},{wire:[{at:70000,status:204,memoryTimes:[100000]}]}])assert.equal(diagnoseRestart({...input,...amended}).diagnosticConfirmed,false);
});

test('repeated WAL timestamp cannot pretend to be two distinct outage samples',()=>{
  assert.equal(diagnoseRestart({outage:10000,restart:60000,walTimes:[20000,20000],now:90000,wire:[{at:70000,status:204,memoryTimes:[65000]}]}).diagnosticConfirmed,false);
});
