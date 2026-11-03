import fs from 'node:fs';
import path from 'node:path';
import cp from 'node:child_process';
import {createHash} from 'node:crypto';

export function preservedWalEvidence({before,after}){
  const valid=s=>s?.complete===true&&s.holderRunning===true&&s.volumeName===s.holderVolumeName&&s.volumeName===s.senderVolumeName&&
    typeof s.senderContainerId==='string'&&/^[a-f0-9]{64}$/.test(s.senderContainerId)&&
    s.driver==='local'&&s.options?.type==='tmpfs'&&s.options.device==='tmpfs'&&typeof s.options.o==='string'&&s.options.o.includes('size=64m')&&
    Array.isArray(s.files)&&s.files.some(f=>f.bytes>0)&&s.files.every(f=>Number.isSafeInteger(f.bytes)&&f.bytes>=0&&/^[a-f0-9]{64}$/.test(f.sha256));
  const passed=!!valid(before)&&!!valid(after)&&after.senderStopped===true&&before.volumeName===after.volumeName&&
    before.senderContainerId===after.senderContainerId&&
    JSON.stringify(before.options)===JSON.stringify(after.options)&&JSON.stringify(before.files)===JSON.stringify(after.files);
  return {passed,scope:'same held named tmpfs and byte-identical nonempty WAL before restart'};
}

export function manualRecoveryEvidence({preserved,restart,now,receivedTimes,configUnchanged,repairOrDiscard,memoryCap,nanoCPUs,oomKilled}){
  const freshOriginalSampleTimes=receivedTimes.filter(t=>Number.isSafeInteger(t)&&t>=restart&&t<=now&&now-t<=60000);
  return {passed:preserved===true&&configUnchanged===true&&repairOrDiscard===false&&freshOriginalSampleTimes.length>0&&memoryCap===268435456&&nanoCPUs===100000000&&oomKilled===false,
    freshOriginalSampleTimes,recoveryMethod:'one manual zero-grace stop/restart request for exact sender with held existing WAL',durableBackfillPromised:false};
}

export async function recoverHeldSender({artifact,full,holder,volume,docker,inspect,owns,query,until,write,deadlineAt}){
  const receipt={automaticRecovery:'see separate post-free observation',explicitWalDiscard:false};
  const save=()=>write(path.join(artifact,'manual-filesystem-recovery.json'),receipt);
  const hashConfig=()=>createHash('sha256').update(fs.readFileSync(path.join(artifact,'sender/config.alloy'))).digest('hex');
  const snapshot=label=>{
    const copy=path.join(artifact,'wal-copy');
    try{
      const v=inspect('volume',volume),h=inspect('container',holder),s=inspect('container',full);
      if(!owns(v)||!owns(h.Config)||!owns(s.Config))throw new Error('recovery_ownership_failed');
      docker(['cp',holder+':/var/lib/alloy/prometheus.remote_write.grafana/wal',copy],10);
      const files=[];function walk(dir,prefix=''){for(const e of fs.readdirSync(dir,{withFileTypes:true})){const file=path.join(dir,e.name),name=prefix+e.name;if(e.isDirectory())walk(file,name+'/');else{const body=fs.readFileSync(file);files.push({name,bytes:body.length,sha256:createHash('sha256').update(body).digest('hex')});}}}walk(copy);files.sort((a,b)=>a.name.localeCompare(b.name));
      let decoded;
      try{decoded=JSON.parse(cp.execFileSync(path.join(artifact,'decoder.exe'),['wal',copy],{timeout:Math.max(1,Math.min(10000,deadlineAt-Date.now())),maxBuffer:1048576,windowsHide:true,stdio:['ignore','pipe','ignore']}));}catch{decoded={decodeFailed:true};}
      const result={complete:true,volumeName:volume,driver:v.Driver,options:v.Options,holderRunning:h.State.Running,
        holderVolumeName:h.Mounts.find(m=>m.Destination==='/var/lib/alloy')?.Name,senderVolumeName:s.Mounts.find(m=>m.Destination==='/var/lib/alloy')?.Name,senderContainerId:s.Id,senderCaps:{memoryCap:s.HostConfig.Memory,nanoCPUs:s.HostConfig.NanoCpus,oomKilled:s.State.OOMKilled},senderStopped:!s.State.Running,files,decoded};
      write(path.join(artifact,'recovery-wal-'+label+'.json'),result);return result;
    }finally{if(fs.existsSync(copy))fs.rmSync(copy,{recursive:true});}
  };
  receipt.selectedCounters=JSON.parse(docker(['exec',full,'node','--input-type=module','-e','const t=await(await fetch("http://127.0.0.1:12345/metrics",{signal:AbortSignal.timeout(2000)})).text();console.log(JSON.stringify(t.split("\\n").filter(l=>/^(prometheus_remote_write_wal_|prometheus_wal_watcher_|prometheus_remote_storage_(samples_(pending|total|failed_total|retried_total|dropped_total)))/.test(l))));']));
  receipt.configBefore=hashConfig();receipt.preStopFaults=classifyRecoveryLogs(docker(['logs','--since','70s',full]));save();
  docker(['pause',full],10);receipt.before=snapshot('before-stop');save();
  docker(['stop','-t','0',full],10);
  const stopped=inspect('container',full);receipt.stop={exitCode:stopped.State.ExitCode,oomKilled:stopped.State.OOMKilled,zeroGraceRequested:true};
  receipt.afterStop=snapshot('after-stop');receipt.preservation=preservedWalEvidence({before:receipt.before,after:receipt.afterStop});save();
  if(!receipt.preservation.passed)throw new Error('held_wal_preservation_inconclusive');
  receipt.restart=Date.now();docker(['start',full],10);
  const startupResult=await until(()=>{const logs=classifyRecoveryLogs(docker(['logs','--since',new Date(receipt.restart).toISOString(),full]));if(logs.repairOrDiscard)return {repair:logs};const values=query('fasttowin_redis_monitor_memory_used_bytes{job="fasttowin-redis-monitor",instance="redis-primary"}[4m]')[0]?.values??[];return values.some(([t])=>t*1000>=receipt.restart)?{fresh:true}:false;},45);
  if(startupResult.repair){receipt.startup=startupResult.repair;receipt.passed=false;save();throw new Error('startup_repair_or_discard');}
  receipt.startup=classifyRecoveryLogs(docker(['logs','--since',new Date(receipt.restart).toISOString(),full]));
  const s=inspect('container',full),times=(query('fasttowin_redis_monitor_memory_used_bytes{job="fasttowin-redis-monitor",instance="redis-primary"}[4m]')[0]?.values??[]).map(([t])=>t*1000);
  receipt.configAfter=hashConfig();Object.assign(receipt,manualRecoveryEvidence({preserved:receipt.preservation.passed&&s.Mounts.some(m=>m.Destination==='/var/lib/alloy'&&m.Name===volume),restart:receipt.restart,now:Date.now(),receivedTimes:times,configUnchanged:receipt.configBefore===receipt.configAfter,repairOrDiscard:receipt.startup.repairOrDiscard,memoryCap:s.HostConfig.Memory,nanoCPUs:s.HostConfig.NanoCpus,oomKilled:s.State.OOMKilled}));save();return receipt;
}

export function classifyRecoveryLogs(logs){
  return {repairOrDiscard:/encountered WAL read error|attempting repair|WAL repair failed|truncate corrupted WAL|repair corrupted WAL|corruption|sample referencing non-existing series|skipped_series/i.test(logs),
    walError:/no space left|error tailing WAL|unknown.*ref|not explicitly dropped/i.test(logs)};
}
