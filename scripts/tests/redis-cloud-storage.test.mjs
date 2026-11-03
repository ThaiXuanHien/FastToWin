import test from 'node:test';
import assert from 'node:assert/strict';
import {runStoragePhases} from './redis-cloud-storage.mjs';

test('a failed retention phase records failure and still attempts the independent filesystem phase',async()=>{
  const phases=[],gates=[],modes=[];
  await runStoragePhases({artifact:'owned-fixture',containers:{sender:'owned-sender'},
    docker:()=>{throw new Error('injected_owned_operation_failure');},
    write:()=>{},localAlloy:()=>'',setStage:s=>phases.push(s),
    gate:(name,result)=>gates.push({name,...result}),receiverMode:mode=>modes.push(mode)});
  assert.deepEqual(phases,['accelerated-retention','full-filesystem']);
  assert.equal(gates.length,2);assert.ok(gates.every(g=>g.passed===false&&g.inconclusive===true));
  assert.deepEqual(modes,['accept']);
});
