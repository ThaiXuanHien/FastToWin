import assert from 'node:assert/strict';
import path from 'node:path';

export const WAL_TARGET='/var/lib/alloy/prometheus.remote_write.grafana/wal';
export const RESET_ACK='monitoring-only unsent history deliberately discarded; historical backfill not promised';
const options={device:'tmpfs',type:'tmpfs',o:'size=64m,uid=65532,gid=65532,mode=0700'};

export function validateResetScope({id,target,ack,full,holderId,volume,sender,holder,before,fault}){
  const own=x=>x?.Labels?.['fasttowin.redis-cloud-run']===id;
  const mounted=x=>x.Mounts?.find(m=>m.Destination==='/var/lib/alloy'&&m.Type==='volume'&&m.Name===volume.Name);
  const passed=/^[a-f0-9]{32}$/.test(id)&&target===WAL_TARGET&&path.posix.resolve(target)===WAL_TARGET&&path.posix.dirname(target)==='/var/lib/alloy/prometheus.remote_write.grafana'&&ack===RESET_ACK&&
    volume.Name==='ftw-cloud-held-'+id&&own(volume)&&volume.Driver==='local'&&Object.entries(options).every(([k,v])=>volume.Options?.[k]===v)&&
    sender.Id===full&&/^[a-f0-9]{64}$/.test(full)&&own(sender.Config)&&sender.State.Running===false&&sender.State.ExitCode===137&&sender.State.OOMKilled===false&&mounted(sender)&&sender.HostConfig.Memory===268435456&&sender.HostConfig.NanoCpus===100000000&&
    holder.Id===holderId&&/^[a-f0-9]{64}$/.test(holderId)&&own(holder.Config)&&holder.State.Running===true&&holder.Config.User==='65532:65532'&&holder.HostConfig.ReadonlyRootfs===true&&holder.HostConfig.NetworkMode==='none'&&mounted(holder)?.RW===false&&
    before.complete===true&&before.senderContainerId===full&&before.volumeName===volume.Name&&before.files?.some(f=>f.bytes>0&&/^[a-f0-9]{64}$/.test(f.sha256))&&
    fault.diskFailureObserved===true&&fault.gapObserved===true&&Number.isSafeInteger(fault.availableBytes)&&fault.availableBytes>0;
  if(!passed)throw new Error('reset_scope_refused');
  return {passed:true,target:WAL_TARGET,volumeName:volume.Name,senderId:full,holderId,ack};
}

// Fixed local-only program: no argv/env target, symlink traversal or parent wipe.
export const resetProgram=String.raw`import fs from 'node:fs';
const root='/var/lib/alloy',parent=root+'/prometheus.remote_write.grafana',target=parent+'/wal';
if(process.getuid()!==65532||process.getgid()!==65532||fs.realpathSync(root)!==root||fs.realpathSync(parent)!==parent||fs.realpathSync(target)!==target||fs.lstatSync(target).isSymbolicLink()||!fs.statSync(target).isDirectory())throw new Error('reset_path_refused');
const mount=fs.readFileSync('/proc/self/mountinfo','utf8').split('\n').find(l=>l.split(' ')[4]===root);
if(!mount||mount.split(' - ')[1].split(' ')[0]!=='tmpfs'||fs.statfsSync(root).blocks*fs.statfsSync(root).bsize!==67108864)throw new Error('reset_mount_refused');
fs.rmSync(target,{recursive:true});fs.mkdirSync(target,{mode:0o700});
const stat=fs.statSync(target),empty=fs.readdirSync(target).length===0;
if(fs.realpathSync(target)!==target||stat.uid!==65532||stat.gid!==65532||!empty)throw new Error('reset_empty_refused');
console.log(JSON.stringify({target,empty,uid:stat.uid,gid:stat.gid,mode:stat.mode&0o777}));`;

export async function resetHeldWal({id,artifact,full,holder,volume,docker,inspect,before,fault,image,write}){
  const scope=validateResetScope({id,target:WAL_TARGET,ack:RESET_ACK,full,holderId:holder,volume:inspect('volume',volume),sender:inspect('container',full),holder:inspect('container',holder),before,fault});
  const receipt={scope,ack:RESET_ACK,explicitWalDiscard:true,emptyVerified:false};
  const save=()=>write(path.join(artifact,'monitoring-wal-reset.json'),receipt);save();
  const helper=docker(['create','--name','ftw-cloud-reset-'+id,'--label','fasttowin.redis-cloud-run='+id,'--network','none','--user','65532:65532','--read-only','--cpus','0.01','--memory','32m','--pids-limit','8','--cap-drop','ALL','--security-opt','no-new-privileges:true','--mount','type=volume,source='+volume+',target=/var/lib/alloy,volume-nocopy','--entrypoint','node',image,'--input-type=module','-e',resetProgram],10);
  const h=inspect('container',helper);
  assert.equal(h.Config.Labels?.['fasttowin.redis-cloud-run'],id);assert.equal(h.Config.User,'65532:65532');assert.equal(h.HostConfig.NetworkMode,'none');assert.equal(h.HostConfig.ReadonlyRootfs,true);
  assert.equal(h.Mounts.length,1);assert.equal(h.Mounts[0].Name,volume);assert.equal(h.Mounts[0].Destination,'/var/lib/alloy');assert.equal(h.Mounts[0].RW,true);
  // Revalidate the stopped sender and held volume immediately before destructive helper start.
  validateResetScope({id,target:WAL_TARGET,ack:RESET_ACK,full,holderId:holder,volume:inspect('volume',volume),sender:inspect('container',full),holder:inspect('container',holder),before,fault});
  receipt.helper=helper;save();docker(['start',helper],10);
  assert.equal(docker(['wait',helper],10),'0');
  const done=inspect('container',helper);assert.equal(done.State.ExitCode,0);assert.equal(done.State.OOMKilled,false);
  receipt.empty=JSON.parse(docker(['logs',helper],10));assert.equal(receipt.empty.target,WAL_TARGET);assert.equal(receipt.empty.empty,true);assert.equal(receipt.empty.uid,65532);assert.equal(receipt.empty.gid,65532);assert.equal(receipt.empty.mode,448);
  receipt.emptyVerified=true;save();return receipt;
}

export function resetFreshEvidence({ack,emptyVerified,restart,now,times,gapObserved,configUnchanged,sameVolume,stopQualified,memoryCap,nanoCPUs,oomKilled,repairOrDiscard}){
  const freshOriginalSampleTimes=[...new Set(times.filter(t=>Number.isSafeInteger(t)&&t>=restart&&t<=now&&now-t<=60000))];
  return {passed:ack===RESET_ACK&&emptyVerified===true&&gapObserved===true&&configUnchanged===true&&sameVolume===true&&stopQualified===true&&memoryCap===268435456&&nanoCPUs===100000000&&oomKilled===false&&repairOrDiscard===false&&freshOriginalSampleTimes.length>=2,
    freshOriginalSampleTimes,explicitWalDiscard:true,ack,durableBackfillPromised:false,recoveryMethod:'Owner-approved local manual monitoring-WAL reset after restored capacity'};
}
