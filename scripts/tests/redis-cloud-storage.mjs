import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import cp from 'node:child_process';
import {diskRetentionEvidence,filesystemEvidence,allocationEvidence} from './redis-cloud-evidence.mjs';

const allocationProbe=String.raw`import fs from 'node:fs';
const root='/var/lib/alloy',stat=fs.statfsSync(root),line=fs.readFileSync('/proc/self/mountinfo','utf8').split('\n').find(l=>l.split(' ')[4]===root);
const mount=line?{target:root,filesystem:line.split(' - ')[1].split(' ')[0]}:null;
function file(p){const s=fs.statSync(p);return {bytes:s.size,allocatedBytes:s.blocks*512};}
const walFiles=[];function walk(p,prefix=''){if(!fs.existsSync(p))return;for(const e of fs.readdirSync(p,{withFileTypes:true})){const f=p+'/'+e.name;if(e.isDirectory())walk(f,prefix+e.name+'/');else walFiles.push({name:prefix+e.name,...file(f)});}}
walk(root+'/prometheus.remote_write.grafana/wal');
console.log(JSON.stringify({mount,filesystem:{type:stat.type,totalBytes:stat.blocks*stat.bsize,freeBytes:stat.bfree*stat.bsize,availableBytes:stat.bavail*stat.bsize},filler:fs.existsSync(root+'/fixture-full')?file(root+'/fixture-full'):null,walFiles}));`;

export async function runStoragePhases({artifact,id,docker,containers,query,until,delay,inspect,owns,receiverMode,receiverReceipts,write,localAlloy,network,config,gate,setStage,fullOnly=false}){
    // Controller ruling2026-11-03: disk WAL/checkpoint expiry is not a wire TTL.
    if(!fullOnly){setStage('accelerated-retention');
    try{docker(['stop','-t','10',containers.sender]);write(path.join(artifact,'sender/config.alloy'),localAlloy({accelerated:true}));receiverMode('unavailable');docker(['start',containers.sender]);
    const snapshot=label=>{
      const copy=path.join(artifact,'wal-copy');
      docker(['pause',containers.sender],10);
      try{docker(['cp',containers.sender+':/var/lib/alloy/prometheus.remote_write.grafana/wal',copy],15);}
      finally{docker(['unpause',containers.sender],10);}
      try{
        const manifest=[];
        function walk(dir,prefix=''){for(const entry of fs.readdirSync(dir,{withFileTypes:true})){const name=prefix+entry.name;if(entry.isDirectory())walk(path.join(dir,entry.name),name+'/');else manifest.push({name,bytes:fs.statSync(path.join(dir,entry.name)).size});}}
        walk(copy);
        const decoded=JSON.parse(cp.execFileSync(path.join(artifact,'decoder.exe'),['wal',copy],{timeout:15000,maxBuffer:1048576,windowsHide:true,stdio:['ignore','pipe','ignore']}));
        const result={at:Date.now(),memoryTimes:decoded.memoryTimes,manifest,complete:true};
        write(path.join(artifact,'wal-'+label+'.json'),result);return result;
      }finally{if(fs.existsSync(copy))fs.rmSync(copy,{recursive:true});}
    };
    const expiryStart=Date.now();await delay(25000);const before=snapshot('retention-before');
    await delay(82000);const after=snapshot('retention-after');
    const expiry=diskRetentionEvidence({beforeTimes:before.memoryTimes,afterTimes:after.memoryTimes,older:[expiryStart,before.at],newer:[after.at-30000,after.at+1],beforeComplete:before.complete,afterComplete:after.complete});
    receiverMode('accept');const recovery=Date.now();let received=[];
    try{await until(()=>{received=query('fasttowin_redis_monitor_memory_used_bytes{job="fasttowin-redis-monitor",instance="redis-primary"}[4m]')[0]?.values??[];return received.some(([t])=>t*1000>=recovery);},45);}catch{}
    gate('accelerated-retention-expiry',{...expiry,before,after,wireReceipts:receiverReceipts().filter(r=>r.at>=expiryStart),receivedTimes:received.map(([t])=>t*1000),truncate:'10s',minimum:'10s',maximum:'30s',productionUnchanged:'5m/5m/2h',twoHourWallClockTest:false,wireTTL:false});
    }catch(error){gate('accelerated-retention-expiry',{passed:false,error:error.message,inconclusive:true});}}
    receiverMode('accept');
    // Bounded writable tmpfs models full filesystem behavior, not physical durability.
    setStage('full-filesystem');
    try{
    write(path.join(artifact,'sender/config.alloy'),localAlloy({accelerated:true}));docker(['stop','-t','10',containers.sender]);
    const fullName='ftw-cloud-full-'+id;
    const full=docker(['create','--name',fullName,'--label','fasttowin.redis-cloud-run='+id,'--network',network.Id,'--user','65532:65532','--cpus','0.1','--memory','256m','--pids-limit','64','--cap-drop','ALL','--security-opt','no-new-privileges:true','--read-only','--tmpfs','/var/lib/alloy:uid=65532,gid=65532,mode=0700,size=64m','--mount',`type=bind,source=${path.join(artifact,'sender')},target=/fixture,readonly`,'--mount',`type=bind,source=${path.join(artifact,'sender/token')},target=/run/secrets/grafana-write,readonly`,'--entrypoint','/bin/alloy',config.services.sender.image,'run','--disable-reporting','--server.http.listen-addr=127.0.0.1:12345','--storage.path=/var/lib/alloy','/fixture/config.alloy']);
    assert.ok(owns(inspect('container',full).Config));const fullStartedAt=Date.now();docker(['start',full]);
    await until(()=>{const values=query('fasttowin_redis_monitor_memory_used_bytes{job="fasttowin-redis-monitor",instance="redis-primary"}[4m]')[0]?.values??[];return values.some(([t])=>t*1000>=fullStartedAt);},45);
    const allocation=()=>JSON.parse(docker(['exec',full,'node','--input-type=module','-e',allocationProbe]));
    const beforeFill=allocation();write(path.join(artifact,'filesystem-before-fill.json'),beforeFill);
    const fullAt=Date.now(),observations=[];
    const fill=JSON.parse(docker(['exec',full,'node','--input-type=module','-e','import fs from "node:fs";const f=fs.openSync("/var/lib/alloy/fixture-full","w");let bytes=0,code="limit-not-hit";try{for(let i=0;i<65;i++){bytes+=fs.writeSync(f,Buffer.alloc(1048576));}}catch(e){code=e.code;}finally{fs.closeSync(f);}console.log(JSON.stringify({bytes,code}));']));assert.equal(fill.code,'ENOSPC');
    const afterFill=allocation();write(path.join(artifact,'filesystem-after-fill.json'),afterFill);
    const precondition=allocationEvidence({fill,snapshot:afterFill});gate('filesystem-allocation-precondition',{...precondition,beforeFill,afterFill,fill,fixtureWAL:'10s/10s/30s',productionUnchanged:'5m/5m/2h'});
    if(!precondition.passed)throw new Error('filesystem_allocation_precondition_inconclusive');
    for(let i=0;i<9;i++){await delay(5000);const values=query('fasttowin_redis_monitor_memory_used_bytes{job="fasttowin-redis-monitor",instance="redis-primary"}[4m]')[0]?.values??[];observations.push({at:Date.now(),latest:values.length?values.at(-1)[0]*1000:null});}
    const fullLogs=docker(['logs','--since','60s',full]);const diskFailureObserved=/no space left|ENOSPC|failed.*(append|write)|error.*(append|write)/i.test(fullLogs);
    write(path.join(artifact,'filesystem-fault-classification.json'),{diskFailureObserved,events:fullLogs.split('\n').filter(l=>/no space left|ENOSPC|failed.*(append|write)|error.*(append|write)/i.test(l)).map(l=>({enospc:/no space left|ENOSPC/i.test(l),walPath:/wal|append|segment|checkpoint/i.test(l)}))});
    docker(['exec',full,'node','-e','require("fs").unlinkSync("/var/lib/alloy/fixture-full")']);const freeAt=Date.now();let recoveredSamples=[];
    try{await until(()=>{recoveredSamples=query('fasttowin_redis_monitor_memory_used_bytes{job="fasttowin-redis-monitor",instance="redis-primary"}[4m]')[0]?.values??[];return recoveredSamples.some(([t])=>t*1000>=freeAt);},45);}catch{}
    write(path.join(artifact,'filesystem-after-free.json'),allocation());
    const filesystem=filesystemEvidence({fullAt,freeAt,checkedAt:Date.now(),observations,recoveredSamples,diskFailureObserved,fill});
    gate('constrained-filesystem-full-and-recovery',{...filesystem,tmpfsMiB:64,fill,diskFailureObserved,physicalDurability:false});
    const state=inspect('container',full);
    gate('filesystem-resource-state',{passed:state.HostConfig.Memory===268435456&&state.HostConfig.NanoCpus===100000000&&!state.State.OOMKilled,memoryCap:state.HostConfig.Memory,nanoCPUs:state.HostConfig.NanoCpus,oomKilled:state.State.OOMKilled});
    docker(['stop','-t','10',full]);
    }catch(error){gate('constrained-filesystem-full-and-recovery',{passed:false,error:error.message,inconclusive:true});}
}
