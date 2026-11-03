// Independent acceptance against an already owned native fixture.
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import cp from 'node:child_process';
const [artifactArg,phase]=process.argv.slice(2),artifact=path.resolve(artifactArg);
const owned=path.resolve('.artifacts/redis-monitor-local-20261103')+path.sep;
assert.ok(artifact.startsWith(owned)&&/^run-[a-f0-9]{32}$/.test(path.basename(artifact)));
const s=JSON.parse(fs.readFileSync(path.join(artifact,'endpoints.json')));
assert.equal(s.artifact,artifact);assert.equal(s.synthetic,true);
for(const u of [s.collector,s.prometheus,s.grafana])assert.match(u,/^http:\/\/127\.0\.0\.1:\d+$/);
assert.ok(['healthy','fault','recovery','unreachable'].includes(phase));
const dashboard=JSON.parse(fs.readFileSync('deploy/monitoring/grafana/dashboards/fasttowin-redis.json'));
if(phase==='fault')fs.writeFileSync(path.join(artifact,'mode'),'fault');
if(phase==='recovery')fs.writeFileSync(path.join(artifact,'mode'),'healthy');
if(phase==='unreachable')fs.writeFileSync(path.join(artifact,'collector-stop'),'stop');
async function query(expr){const u=new URL(s.prometheus+'/api/v1/query');u.searchParams.set('query',expr);const r=await(await fetch(u)).json();assert.equal(r.status,'success');return r.data.result;}
const expr=dashboard.panels[0].targets[0].expr;
const expected=phase==='fault'?'0':phase==='unreachable'?null:'1';
let current;
for(let i=0;i<180;i++){
  current=await query(expr);
  if(expected===null?current.length===0:current[0]?.value[1]===expected)break;
  await new Promise(r=>setTimeout(r,250));
}
assert.equal(current[0]?.value[1]??null,expected);
const counts=[];
for(const p of dashboard.panels.filter(p=>p.type==='stat')){
  const q=await query(p.targets[0].expr);counts.push({panel:p.id,samples:q.length,value:q[0]?.value[1]??null});
  if(p.id!==1)assert.equal(q.length,expected==='1'?1:0);
}
let metricsChecked=false;
if(phase!=='unreachable'){
  const m=await(await fetch(s.collector+'/metrics')).text();fs.writeFileSync(path.join(artifact,phase+'.metrics'),m);
  const r=cp.spawnSync('.artifacts/redis-monitor-local-20261103/tools/prometheus-windows/promtool.exe',['check','metrics'],{input:m,encoding:'utf8',windowsHide:true});assert.equal(r.status,0,r.stderr);metricsChecked=true;
  if(phase==='fault')assert.ok(!/^fasttowin_redis_monitor_memory_/m.test(m));
}
// Range data remains available for earlier healthy history, while instant data is unavailable.
const history=await query('count_over_time(fasttowin_redis_monitor_memory_used_bytes{job="fasttowin-redis-monitor"}[5m])');
assert.ok(history.length>0);
const result={phase,synthetic:true,metricsChecked,counts,healthyHistoryPreserved:true};
fs.writeFileSync(path.join(artifact,phase+'-check.json'),JSON.stringify(result,null,2)+'\n');console.log(JSON.stringify(result));
