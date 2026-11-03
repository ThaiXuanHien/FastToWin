import {test} from 'node:test';
import assert from 'node:assert/strict';
import net from 'node:net';
import {command} from './redis-monitor-probe.mjs';

// These tests exercise the actual fixture probe over uniquely owned loopback sockets.
// A watchdog reports pending probe promises; it cannot make the runner's finally run.
async function peer(action,run){
  const sockets=new Set(),timers=new Set();
  const later=(ms,fn)=>{const t=setTimeout(()=>{timers.delete(t);fn();},ms);timers.add(t);};
  const server=net.createServer(socket=>{sockets.add(socket);socket.on('error',()=>{});socket.on('close',()=>sockets.delete(socket));let phase=0;socket.on('data',()=>action(socket,phase++,later));});
  await new Promise((resolve,reject)=>{server.once('error',reject);server.listen(0,'127.0.0.1',resolve);});
  try{await run(server.address().port,sockets);}finally{for(const t of timers)clearTimeout(t);for(const s of sockets)s.destroy();await new Promise(r=>server.close(r));}
}
async function bounded(promise){
  let t;try{return await Promise.race([promise,new Promise((_,reject)=>{t=setTimeout(()=>reject(new Error('watchdog_pending_probe')),800);})]);}finally{clearTimeout(t);}
}
const invoke=(port,deadline=180)=>command(port,'ftw_monitor','a'.repeat(96),['INFO','memory','persistence','stats'],deadline);

test('fragmented AUTH then fragmented bulk response succeeds',async()=>{
  await peer((s,phase,later)=>{
    if(phase===0){s.write('+O');later(15,()=>s.write('K\r\n'));}
    else{s.write('$8\r\nfo');later(15,()=>s.write('o:1\r\n!\r'));later(30,()=>s.end('\n'));}
  },async port=>assert.equal(await bounded(invoke(port)),'$8\r\nfoo:1\r\n!\r\n'));
});
test('fragmented simple and integer command replies require complete CRLF',async()=>{
  for(const response of ['+PONG\r\n',':1\r\n','-NOPERM synthetic denied\r\n'])await peer((s,phase,later)=>{
    if(!phase)s.write('+OK\r\n');else{s.write(response.slice(0,-1));later(20,()=>s.end(response.slice(-1)));}
  },async port=>assert.equal(await bounded(invoke(port)),response));
});

for(const [name,action,expected] of [
  ['partial AUTH then close',(s)=>s.end('+O'),'probe_closed'],
  ['partial bulk then close',(s,p)=>p?s.end('$100\r\npartial'):s.write('+OK\r\n'),'probe_closed'],
  ['partial AUTH then timeout',(s)=>s.write('+O'),'probe_timeout'],
  ['partial bulk then timeout',(s,p)=>p?s.write('$100\r\npartial'):s.write('+OK\r\n'),'probe_timeout'],
  ['malformed bulk length',(s,p)=>p?s.end('$bad\r\n'):s.write('+OK\r\n'),'probe_protocol'],
  ['malformed bulk terminator',(s,p)=>p?s.end('$1\r\nx!!'):s.write('+OK\r\n'),'probe_protocol'],
  ['oversized declared bulk',(s,p)=>p?s.end('$1048577\r\n'):s.write('+OK\r\n'),'probe_oversized'],
  ['AUTH error sanitized',(s)=>s.end('-ERR private-marker\r\n'),'probe_auth_failed'],
])test(name+' settles and reaches owner cleanup',async()=>{
  await peer(action,async (port,sockets)=>{
    let cleaned=false;
    const owner=(async()=>{try{await invoke(port);}finally{cleaned=true;}})();
    await assert.rejects(bounded(owner),e=>e.message===expected&&!e.message.includes('private-marker'));
    assert.equal(cleaned,true);
    for(let i=0;i<20&&sockets.size;i++)await new Promise(r=>setTimeout(r,10));
    assert.equal(sockets.size,0,'probe left a connected socket after failure');
  });
});

test('trickled reply cannot extend the absolute AUTH plus response deadline',async()=>{
  await peer((s,p,later)=>{
    if(!p){later(90,()=>s.write('+OK\r\n'));}
    else{for(let i=0;i<8;i++)later(i*50,()=>{if(!s.destroyed)s.write(i?'x':'$100\r\n');});}
  },async port=>{
    const start=performance.now();let cleaned=false;
    const owner=(async()=>{try{await invoke(port,180);}finally{cleaned=true;}})();
    await assert.rejects(bounded(owner),{message:'probe_timeout'});assert.equal(cleaned,true);
    assert.ok(performance.now()-start<600,'absolute deadline reset by activity or AUTH completion');
  });
});
