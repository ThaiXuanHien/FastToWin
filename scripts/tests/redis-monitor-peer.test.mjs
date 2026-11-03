import {test} from 'node:test';
import assert from 'node:assert/strict';
import net from 'node:net';
import {createPeer} from './redis-monitor-fixture.mjs';
test('synthetic peer accepts only the collector fixed exchange and fault control',async()=>{
  let mode='healthy'; const p=await createPeer('a'.repeat(96),()=>mode);
  const exchange=async()=>{
    const s=net.connect(p.port,'127.0.0.1'); let b=''; s.on('data',x=>b+=x);
    const send=a=>s.write('*'+a.length+'\r\n'+a.map(v=>'$'+v.length+'\r\n'+v+'\r\n').join(''));
    await new Promise((r,reject)=>{s.once('connect',r);s.once('error',reject);});
    send(['AUTH','ftw_monitor','a'.repeat(96)]); send(['PING']); send(['INFO','memory','persistence','stats']);
    await new Promise(r=>s.once('close',r)); return b;
  };
  try { assert.match(await exchange(),/^\+OK\r\n\+PONG\r\n\$\d+\r\nused_memory:/); mode='fault'; assert.match(await exchange(),/-ERR synthetic unavailable/); assert.equal(p.attempts(),2); }
  finally {await p.close();}
});
