import fs from 'node:fs';
import path from 'node:path';
import cp from 'node:child_process';

function watcherCounters(lines){
  if(!Array.isArray(lines))return null;
  const result={};
  for(const line of lines){
    const match=/^(prometheus_wal_watcher_records_read_total|prometheus_wal_watcher_record_decode_failures_total)\{(.*)\} ([^ ]+)$/.exec(line);
    if(!match)continue;
    const labels={};let remainder=match[2];
    while(remainder){
      const label=/^(\w+)=("(?:\\.|[^"\\])*"),?/.exec(remainder);
      if(!label||Object.hasOwn(labels,label[1]))return null;
      try{labels[label[1]]=JSON.parse(label[2]);}catch{return null;}
      remainder=remainder.slice(label[0].length);
    }
    if(labels.component_id!=='prometheus.remote_write.grafana'||labels.component_path!=='/')continue;
    const key=match[1].endsWith('record_decode_failures_total')?'errors':labels.type==='samples'?'read':null;
    if(!key)continue;
    const value=Number(match[3]);
    if(!/^\d+(?:\.\d+)?(?:[eE][+-]?\d+)?$/.test(match[3])||!Number.isSafeInteger(value)||value<0||!labels.consumer||Object.hasOwn(result,key))return null;
    if(result.consumer&&result.consumer!==labels.consumer)return null;
    result.consumer=labels.consumer;result[key]=value;
  }
  return Object.hasOwn(result,'read')&&Object.hasOwn(result,'errors')?result:null;
}

export function diagnoseRestart({outage,restart,walTimes,wire,now,beforeStop,after:afterCounters}){
  const queued=[...new Set(walTimes.filter(t=>t>=outage&&t<restart))];
  const after=wire.filter(r=>r.at>=restart);
  const sentOld=after.flatMap(r=>r.memoryTimes??[]).filter(t=>t>=outage&&t<restart);
  const fresh=after.filter(r=>r.status===204).flatMap(r=>r.memoryTimes??[]).filter(t=>t>=restart&&t<=now&&now-t<=60000);
  const decodeErrors=after.some(r=>r.decodeFailed||!Array.isArray(r.memoryTimes));
  const beforeWatcher=watcherCounters(beforeStop),afterWatcher=watcherCounters(afterCounters);
  // This fixed fixture writes one memory sample per scrape record. After restart
  // the counter resets: require all preserved records plus the fresh controls,
  // not merely a positive counter that could come only from new scrapes.
  const preservedRecordFloor=new Set(walTimes).size,freshRecordFloor=new Set(fresh).size;
  const readerEvidenceValid=!!beforeWatcher&&!!afterWatcher&&beforeWatcher.consumer===afterWatcher.consumer&&
    beforeWatcher.errors===0&&afterWatcher.errors===0&&beforeWatcher.read>=preservedRecordFloor&&
    afterWatcher.read>=beforeWatcher.read+freshRecordFloor&&afterWatcher.read>beforeWatcher.read;
  return {diagnosticConfirmed:queued.length>=2&&fresh.length>0&&!decodeErrors&&sentOld.length===0&&readerEvidenceValid,
    replayAcceptancePassed:false,queuedOutageTimes:queued,sentOutageTimes:sentOld,freshControlTimes:fresh,decodeErrors,
    readerEvidenceValid,beforeWatcher,afterWatcher,preservedRecordFloor,freshRecordFloor};
}

export async function runWalDiagnostic({artifact,docker,containers,query,until,delay,inspect,receiverMode,receiverReceipts,write,deadlineAt}){
  const diagnostic={startedAt:Date.now(),runtimeDeadlineSeconds:240,deadlineAt,replayAcceptancePassed:false};
  const save=()=>write(path.join(artifact,'wal-diagnostic.json'),diagnostic);
  const counters=()=>JSON.parse(docker(['exec',containers.sender,'node','--input-type=module','-e',
    'const t=await(await fetch("http://127.0.0.1:12345/metrics")).text();const names=new Set(["prometheus_remote_write_wal_samples_appended_total","prometheus_remote_storage_samples_pending","prometheus_remote_storage_samples_total","prometheus_remote_storage_samples_failed_total","prometheus_remote_storage_samples_retried_total","prometheus_wal_watcher_records_read_total","prometheus_wal_watcher_record_decode_failures_total"]);console.log(JSON.stringify(t.split("\\n").filter(l=>names.has(l.split(/[ {]/)[0]))));']));
  const samples=()=>query('fasttowin_redis_monitor_memory_used_bytes{job="fasttowin-redis-monitor",instance="redis-primary"}[4m]')[0]?.values??[];
  try{
    await until(()=>query('{job="fasttowin-redis-monitor",instance="redis-primary"}').length===22,65);
    diagnostic.before=counters();diagnostic.outage=Date.now();receiverMode('unavailable');save();
    await delay(46000);diagnostic.beforeStop=counters();
    docker(['stop','-t','10',containers.sender],15);
    const stopped=inspect('container',containers.sender);
    diagnostic.stopped={exitCode:stopped.State.ExitCode,oomKilled:stopped.State.OOMKilled,finishedAt:stopped.State.FinishedAt};save();
    const copy=path.join(artifact,'wal-copy');
    docker(['cp',containers.sender+':/var/lib/alloy/prometheus.remote_write.grafana/wal',copy],15);
    const decoder=path.join(artifact,'decoder.exe');
    const decodeTimeout=Math.min(15000,deadlineAt-Date.now());
    if(decodeTimeout<=0)throw new Error('diagnostic_runtime_deadline');
    const decoded=JSON.parse(cp.execFileSync(decoder,['wal',copy],{timeout:decodeTimeout,maxBuffer:1048576,windowsHide:true,stdio:['ignore','pipe','ignore']}));
    diagnostic.walTimes=decoded.memoryTimes;diagnostic.walCopyRemoved=false;save();
    fs.rmSync(copy,{recursive:true});diagnostic.walCopyRemoved=true;save();
    if(decoded.memoryTimes.filter(t=>t>=diagnostic.outage).length<2)throw new Error('inconclusive_outage_wal_samples_missing');
    receiverMode('accept');diagnostic.restart=Date.now();docker(['start',containers.sender],15);
    const restarted=inspect('container',containers.sender);
    diagnostic.restarted={id:restarted.Id,startedAt:restarted.State.StartedAt,volume:restarted.Mounts.find(m=>m.Destination==='/var/lib/alloy')?.Name};save();
    await until(()=>samples().some(([t])=>t*1000>=diagnostic.restart),60);
    await delay(16000);diagnostic.after=counters();diagnostic.receivedSampleTimes=samples().map(([t])=>t*1000);
    diagnostic.wire=receiverReceipts().filter(r=>r.at>=diagnostic.outage);
    diagnostic.successfulScrapes=fs.readFileSync(path.join(artifact,'receiver/scrapes.jsonl'),'utf8').trim().split('\n').map(JSON.parse).filter(s=>s.status===200&&s.at>=diagnostic.outage);
    Object.assign(diagnostic,diagnoseRestart({outage:diagnostic.outage,restart:diagnostic.restart,walTimes:diagnostic.walTimes,wire:diagnostic.wire,now:Date.now(),beforeStop:diagnostic.beforeStop,after:diagnostic.after}));
    diagnostic.finishedAt=Date.now();save();
    return diagnostic;
  }catch(error){diagnostic.diagnosticConfirmed=false;diagnostic.error=error.message;save();throw error;}
}
