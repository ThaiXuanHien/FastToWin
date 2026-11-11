import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {createInterface} from 'node:readline';
import {setTimeout as delay} from 'node:timers/promises';

export async function until(probe, description, ms=90000) {
  const end=Date.now()+ms;
  while(Date.now()<end) {
    try {if(await probe())return;} catch {}
    await delay(250);
  }
  throw new Error('Timed out '+description);
}

class Lines {
  constructor(docker,container,args) {
    this.proc=docker.spawn(['exec','-i',container,...args],{stdio:['pipe','pipe','ignore']});
    this.proc.stdin.on('error',()=>{});
    this.queue=[];this.waiters=[];this.closed=false;
    createInterface({input:this.proc.stdout,crlfDelay:Infinity}).on('line',line=>{
      const callback=this.waiters.shift();
      if(callback)callback(line);else this.queue.push(line);
    }).on('close',()=>{this.closed=true;for(const callback of this.waiters.splice(0))callback(undefined)});
  }
  send(value){this.proc.stdin.write(value)}
  async next(ms=5000) {
    if(this.queue.length)return this.queue.shift();
    if(this.closed)throw new Error('Probe connection closed');
    let callback,timer;
    try{return await new Promise((resolve,reject)=>{
      callback=value=>value===undefined?reject(new Error('Probe closed')):resolve(value);
      this.waiters.push(callback);
      timer=setTimeout(()=>reject(new Error('Probe timeout')),ms);
    })}finally{clearTimeout(timer);this.waiters=this.waiters.filter(item=>item!==callback)}
  }
  close(){this.proc.stdin.end();this.proc.kill()}
}

export function fixtureProbes(docker, containers, env) {
  const inside=(role,...args)=>docker.run(['exec',containers[role],...args]);
  const ready=role=>inside(role,'wget','-T','2','-qO','/dev/null','http://127.0.0.1:8080/ready')==='';
  async function control(role,operation,leaseMillis=0) {
    const call=async (op,generation,lease)=>{
      const request={fixtureId:env.ROOM_FAULT_FIXTURE,requestId:randomUUID(),generation,operation:op,
        deadlineUnixMillis:Math.min(Number(env.ROOM_FAULT_DEADLINE_MS),Date.now()+5000),leaseMillis:lease};
      const body=JSON.stringify(request);
      const connection=new Lines(docker,containers.proxy,['nc','-w','6',env.FTW_PILOT_NAME+'-'+role+'.railway.internal','8181']);
      try {
        connection.send('POST /control HTTP/1.1\r\nHost: fixture\r\nConnection: close\r\nAuthorization: Bearer '+env.ROOM_FAULT_TOKEN+'\r\nContent-Type: application/json\r\nContent-Length: '+Buffer.byteLength(body)+'\r\n\r\n'+body);
        const status=await connection.next();
        while(await connection.next()!==''){}
        const result=JSON.parse(await connection.next());
        assert.equal(Number(status.split(' ')[1]),200);
        assert.equal(result.requestId,request.requestId);assert.equal(result.role,role);
        assert.equal(result.verified,true);assert.equal(result.failed,false);assert.equal(result.errorCode,'');
        if(op!=='status')assert.equal(result.generation,op==='start'?generation+1:generation);
        return result;
      }finally{connection.close()}
    };
    const before=await call('status',0,0);
    if(operation==='status')return before;
    const result=await call(operation,before.generation,leaseMillis);
    const state={pause:'paused',resume:'running',kill:'stopped',start:'running','block-a':'blocked-a','block-b':'blocked-b','unblock-a':'running','unblock-b':'running'}[operation];
    assert.equal(result.state,state);
    return result;
  }
  function processes(role) {
    const text=inside(role,'sh','-c',`for f in /proc/[0-9]*/status; do awk '/^Name:/ {n=$2} /^Tgid:/ {p=$2} /^PPid:/ {pp=$2} /^Uid:/ {u=$2} /^Gid:/ {g=$2} /^CapEff:/ {print p,pp,n,u,g,$2}' "$f" || :; done`);
    const rows=text.split('\n').map(line=>line.split(/\s+/));
    const init=rows.find(row=>row[0]==='1'),broker=rows.find(row=>row[1]==='1'&&row[2]==='exe');
    assert(init&&broker);
    const children=rows.filter(row=>row[1]===broker[0]);
    assert(children.length>0);
    for(const row of [init,broker,...children]) {
      assert.notEqual(row[3],'0');assert.equal(row[3],init[3]);assert.equal(row[4],init[4]);assert.match(row[5],/^0+$/);
    }
    assert.equal(inside(role,'readlink','/proc/'+broker[0]+'/exe'),inside(role,'readlink','/proc/1/exe'));
    const measure=pid=>{
      const stat=inside(role,'cat','/proc/'+pid+'/stat').split(') ')[1].split(' ');
      return {pid:Number(pid),cpuTicks:Number(stat[11])+Number(stat[12]),rssKiB:Number(inside(role,'awk','/^VmRSS:/ {print $2}','/proc/'+pid+'/status'))};
    };
    return {uid:Number(init[3]),gid:Number(init[4]),supervisor:measure('1'),broker:measure(broker[0]),workloadPid:Number(children[0][0])};
  }
  const sql=port=>new Lines(docker,containers.pg,['psql','-X','-qAt','-v','ON_ERROR_STOP=1','-h',port?env.FTW_PILOT_NAME+'-proxy.railway.internal':'127.0.0.1','-p',String(port||5432),'-U','fasttowin_room_pilot','-d','fasttowin_room_pilot']);
  async function preflight() {
    const result={roles:{},checks:[]},connections=[];
    try {
      for(const role of ['pg','redis','proxy','a','b']){
        await until(async()=>{const r=await control(role,'status');return r.state==='running'},role+' verified');
        result.roles[role]=processes(role);
      }
      await until(()=>ready('a')&&ready('b'),'both backends ready');
      for(const role of ['a','b']){
        const before=await control(role,'status');
        await control(role,'pause',60000);assert.throws(()=>ready(role));assert(ready(role==='a'?'b':'a'));
        await control(role,'resume');await until(()=>ready(role),'backend resume');
        assert.equal((await control(role,'status')).generation,before.generation);
      }
      await control('a','kill',60000);assert.throws(()=>ready('a'));
      await control('a','start');await until(()=>ready('a'),'backend generation restart');
      result.checks.push('both-backend-pause-resume','backend-kill-start-generation');
      const pg=sql();connections.push(pg);
      pg.send('SELECT pg_backend_pid();\n');const pid=await pg.next();assert.match(pid,/^\d+$/);
      await control('pg','pause',60000);
      assert.equal(inside('pg','awk','{print $3}','/proc/'+pid+'/stat'),'T');
      pg.send('SELECT 42;\n');await delay(350);assert.equal(pg.queue.length,0);
      await control('pg','resume');assert.equal(await pg.next(),'42');
      pg.send('SELECT pg_backend_pid();\n');assert.equal(await pg.next(),pid);
      result.checks.push('postgres-existing-session-stopped-and-resumed');
      const redis=new Lines(docker,containers.redis,['nc','127.0.0.1','6379']);connections.push(redis);
      redis.send('AUTH '+env.FTW_FIXTURE_REDIS_PASSWORD+'\r\nCLIENT ID\r\n');
      assert.equal(await redis.next(),'+OK');const client=await redis.next();
      await control('redis','pause',60000);redis.send('PING\r\n');await delay(350);assert.equal(redis.queue.length,0);
      await control('redis','resume');assert.equal(await redis.next(),'+PONG');
      redis.send('CLIENT ID\r\n');assert.equal(await redis.next(),client);
      result.checks.push('redis-existing-client-stopped-and-resumed');
      assert.throws(()=>inside('proxy','getent','hosts','postgres'));
      assert.match(inside('proxy','cat','/proc/net/tcp'),/0100007F:211A\s+00000000:0000\s+0A/);
      for(const [role,port,opposite] of [['a',15432,25432],['b',25432,15432]]) {
        const selected=sql(port),other=sql(opposite);connections.push(selected,other);
        selected.send('SELECT 42;\n');assert.equal(await selected.next(),'42');
        other.send('SELECT pg_backend_pid();\n');const otherPid=await other.next();
        await control('proxy','block-'+role,60000);
        selected.send('SELECT 99;\n');await until(()=>selected.closed,'selected SQL closed',3000);assert(!selected.queue.includes('99'));
        const fresh=sql(port);connections.push(fresh);fresh.send('SELECT 99;\n');await until(()=>fresh.closed,'fresh SQL refused',3000);assert(!fresh.queue.includes('99'));
        other.send('SELECT pg_backend_pid();\n');assert.equal(await other.next(),otherPid);
        await control('proxy','unblock-'+role);
      }
      result.checks.push('both-selective-proxy-paths','marked-dns-no-postgres-alias','proxy-api-loopback');
      for(const role of ['pg','redis','proxy','a','b'])assert.equal((await control(role,'status')).state,'running');
      await until(()=>ready('a')&&ready('b'),'post-preflight readiness');
      return result;
    }finally{for(const connection of connections)connection.close()}
  }
  return {inside,ready,control,processes,preflight};
}
