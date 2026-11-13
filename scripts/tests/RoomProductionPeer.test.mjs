import test from 'node:test'
import assert from 'node:assert/strict'
import { replacePrivateWorker } from '../lib/room-production-peer.mjs'

function fixture(guard='10.0.0.4',replacement='10.0.0.8') {
    const calls=[]
    let replaced=false
    const docker=args=>{calls.push(args)}
    const address=role=>role==='address-guard'?guard:(replaced?replacement:'10.0.0.4')
    const launchWorker=()=>{calls.push(['launch-worker']);replaced=true}
    return {calls,run:()=>replacePrivateWorker({docker,address,launchWorker,
        worker:'isolated-b',retired:'isolated-b-retired',launchGuard:()=>calls.push(['launch-guard'])})}
}
test('replacement reserves the old address before starting the new worker',()=>{
    const f=fixture()
    assert.deepEqual(f.run(),{addressChanged:true,previous:'10.0.0.4',replacement:'10.0.0.8'})
    assert.deepEqual(f.calls.map(c=>c[0]),['stop','rename','launch-guard','launch-worker'])
})
test('a missing reservation or same worker address never earns replacement proof',()=>{
    assert.throws(()=>fixture('10.0.0.9').run())
    assert.throws(()=>fixture('10.0.0.4','10.0.0.4').run())
})