import test from 'node:test';
import assert from 'node:assert/strict';
import {diagnoseRestart} from './redis-cloud-wal-diagnostic.mjs';

const watcher=(read,errors=0,component='prometheus.remote_write.grafana')=>[
  `prometheus_wal_watcher_records_read_total{component_id="${component}",component_path="/",consumer="test",type="samples"} ${read}`,
  `prometheus_wal_watcher_record_decode_failures_total{component_id="${component}",component_path="/",consumer="test"} ${errors}`
];
const input={outage:10000,restart:60000,walTimes:[20000,35000],now:90000,wire:[{at:70000,status:204,memoryTimes:[65000]}],beforeStop:watcher(2),after:watcher(3)};

test('diagnosis requires two outage WAL timestamps, no old outgoing timestamps and fresh control',()=>{
  const result=diagnoseRestart(input);
  assert.equal(result.diagnosticConfirmed,true);assert.equal(result.replayAcceptancePassed,false);
  assert.deepEqual(result.queuedOutageTimes,[20000,35000]);
  for(const amended of [{walTimes:[20000]},{wire:[]},{wire:[{at:70000,status:204,memoryTimes:[20000,65000]}]},{wire:[{at:70000,status:204,decodeFailed:true}]},{wire:[{at:70000,status:204,memoryTimes:[100000]}]}])assert.equal(diagnoseRestart({...input,...amended}).diagnosticConfirmed,false);
});

test('missing, invalid, insufficient or unhealthy watcher evidence is inconclusive',()=>{
  for(const amended of [
    {beforeStop:undefined},{after:undefined},{after:[]},
    {after:watcher('NaN')},{after:watcher('Infinity')},{after:watcher(-1)},{after:watcher(3.5)},
    {after:watcher('0x3')},{after:watcher(3,'NaN')},
    {after:watcher(2)},{beforeStop:watcher(1)},
    {beforeStop:watcher(2,1)},{after:watcher(3,1)},
    {after:watcher(3,0,'another.component')},
    {after:watcher(3).slice(0,1)},
    {after:[...watcher(3),watcher(3)[0]]},
    {after:watcher(3).map(s=>s.replace('consumer="test"','consumer="different"'))}
  ])assert.equal(diagnoseRestart({...input,...amended}).diagnosticConfirmed,false,JSON.stringify(amended));
});

test('repeated WAL timestamp cannot pretend to be two distinct outage samples',()=>{
  assert.equal(diagnoseRestart({...input,walTimes:[20000,20000]}).diagnosticConfirmed,false);
});
