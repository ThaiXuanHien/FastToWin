// Test-only local acceptance probe: AUTH then one fixture command, no retries.
// Only the simple/error/integer/bulk RESP2 frames needed by this fixture exist here.
import net from 'node:net';
const MAX_BULK=1048576,MAX_LINE=8192;
const frame=a=>'*'+a.length+'\r\n'+a.map(x=>'$'+Buffer.byteLength(x)+'\r\n'+x+'\r\n').join('');
function completeFrame(b){
  if(!b.length)return null;
  if(![43,45,58,36].includes(b[0]))throw new Error('probe_protocol');
  const end=b.indexOf('\r\n');
  if(end<0){
    if(b.includes(10))throw new Error('probe_protocol');
    if(b.length>MAX_LINE)throw new Error('probe_oversized');
    return null;
  }
  if(end>MAX_LINE)throw new Error('probe_oversized');
  if(b.subarray(1,end).includes(13)||b.subarray(1,end).includes(10))throw new Error('probe_protocol');
  if(b[0]!==36){
    if(b[0]===58&&!/^(0|[1-9][0-9]*)$/.test(b.subarray(1,end).toString('utf8')))throw new Error('probe_protocol');
    return end+2;
  }
  const length=b.subarray(1,end).toString('utf8');
  if(!/^(0|[1-9][0-9]*)$/.test(length))throw new Error('probe_protocol');
  if(length.length>7||Number(length)>MAX_BULK)throw new Error('probe_oversized');
  const n=end+2+Number(length);
  if(b.length<n+2)return null;
  if(b[n]!==13||b[n+1]!==10)throw new Error('probe_protocol');
  return n+2;
}
export function command(port,user,password,args,deadlineMs=3000){
  // Tests may shorten the fixed overall deadline, never extend it.
  if(!Number.isFinite(deadlineMs)||deadlineMs<=0||deadlineMs>3000)return Promise.reject(new Error('probe_deadline_invalid'));
  const started=performance.now();
  return new Promise((resolve,reject)=>{
    let socket,settled=false,b=Buffer.alloc(0),authenticated=false;
    const finish=(reason,value)=>{
      if(settled)return;settled=true;clearTimeout(timer);socket?.destroy();
      if(reason)reject(new Error(reason));else resolve(value);
    };
    const timer=setTimeout(()=>finish('probe_timeout'),deadlineMs);
    try{socket=net.connect(port,'127.0.0.1');}catch{finish('probe_connection_failed');return;}
    socket.on('error',()=>finish('probe_connection_failed'));
    socket.on('end',()=>finish('probe_closed'));
    socket.on('close',()=>finish('probe_closed'));
    socket.on('connect',()=>{
      if(performance.now()-started>=deadlineMs){finish('probe_timeout');return;}
      socket.write(frame(['AUTH',user,password]));
    });
    socket.on('data',chunk=>{
      if(settled)return;
      if(performance.now()-started>=deadlineMs){finish('probe_timeout');return;}
      if(b.length+chunk.length>MAX_BULK+MAX_LINE+4){finish('probe_oversized');return;}
      b=Buffer.concat([b,chunk]);
      let length;try{length=completeFrame(b);}catch(e){finish(e.message);return;}
      if(length===null)return;
      if(length!==b.length){finish('probe_protocol');return;}
      if(!authenticated){
        if(!b.equals(Buffer.from('+OK\r\n'))){finish('probe_auth_failed');return;}
        authenticated=true;b=Buffer.alloc(0);socket.write(frame(args));
      }else finish(null,b.toString('utf8'));
    });
  });
}
