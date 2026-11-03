import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';

const dashboardPath=process.env.REDIS_CLOUD_DASHBOARD ?? 'deploy/monitoring/grafana/dashboards/fasttowin-redis-cloud.json';
const dashboard=JSON.parse(fs.readFileSync(dashboardPath,'utf8'));
const fixed='job="fasttowin-redis-monitor",instance="redis-primary"';
const labels='{job="fasttowin-redis-monitor",instance="redis-primary"}';
const series=(name,values)=>({series:`${name}{${fixed}}`,values});
const sample=(name,value)=>({labels:`{__name__="${name}", job="fasttowin-redis-monitor", instance="redis-primary"}`,value});
const metrics=[['up',1],['fasttowin_redis_monitor_up',1],['fasttowin_redis_monitor_aof_enabled',1],['fasttowin_redis_monitor_aof_last_write_ok',1],['fasttowin_redis_monitor_aof_last_rewrite_ok',0],['fasttowin_redis_monitor_memory_used_bytes',81],['fasttowin_redis_monitor_memory_max_bytes',100],['fasttowin_redis_monitor_aof_current_size_bytes',256],['fasttowin_redis_monitor_aof_base_size_bytes',128],['fasttowin_redis_monitor_aof_pending_fsync',2],['fasttowin_redis_monitor_evicted_keys_total',0],['fasttowin_redis_monitor_aof_delayed_fsync_total',0]];
// Hand-derived output for each shipped target. Changing the query to a wrong
// metric, zero-fill, unguarded or reset-unsafe expression must fail real PromQL.
const expected=new Map([
 ['1A',sample('fasttowin_redis_monitor_up',1)], ['2A',sample('fasttowin_redis_monitor_aof_enabled',1)],
 ['3A',sample('fasttowin_redis_monitor_aof_last_write_ok',1)], ['4A',sample('fasttowin_redis_monitor_aof_last_rewrite_ok',0)],
 ['5A',sample('fasttowin_redis_monitor_memory_used_bytes',81)], ['6A',sample('fasttowin_redis_monitor_memory_max_bytes',100)],
 ['7A',sample('fasttowin_redis_monitor_aof_current_size_bytes',256)], ['8A',sample('fasttowin_redis_monitor_aof_base_size_bytes',128)],
 ['9A',sample('fasttowin_redis_monitor_memory_used_bytes',81)], ['9B',sample('fasttowin_redis_monitor_memory_max_bytes',100)],
 ['10A',sample('fasttowin_redis_monitor_aof_current_size_bytes',256)], ['10B',sample('fasttowin_redis_monitor_aof_base_size_bytes',128)],
 ['11A',{labels,value:0}], ['12A',sample('fasttowin_redis_monitor_aof_pending_fsync',2)], ['12B',{labels,value:0}],
]);
const targets=dashboard.panels.flatMap(p=>p.targets.map(t=>({...t,key:`${p.id}${t.refId}`})));

function verifyPromql(cases){
 const temp=fs.mkdtempSync(path.join(os.tmpdir(),'ftw-cloud-promql-'));
 try {
  const file=path.join(temp,'queries.test.yml');
  fs.writeFileSync(file,JSON.stringify({rule_files:[],evaluation_interval:'15s',tests:cases},null,2));
  const tool=process.env.PROMTOOL ?? path.resolve('.artifacts/redis-monitor-local-20261103/tools/prometheus-windows/promtool.exe');
  const result=spawnSync(tool,['test','rules',file],{encoding:'utf8',timeout:60000});
  assert.ifError(result.error);
  assert.equal(result.status,0,result.stdout+result.stderr);
 } finally {fs.rmSync(temp,{recursive:true,force:true});}
}

test('shipped queries evaluate boundaries, outages, WAL age, future samples and recovery using real promtool',()=>{
 const checks=(time,available,transform=x=>x)=>targets.map(t=>({expr:transform(t.expr),eval_time:time,exp_samples:available?[expected.get(t.key)]:[]}));
 const healthy=metrics.map(([n,v])=>series(n,`${v}x30`));
 const cases=[
  {name:'healthy query-time sample age zero',interval:'15s',input_series:healthy,promql_expr_test:checks('60s',true)},
  {name:'sender stopped with old scrape and own-up one; inclusive 60s then unavailable',interval:'15s',input_series:metrics.map(([n,v])=>series(n,`${v} ${v}`)),promql_expr_test:[...checks('75s',true),...checks('76s',false),...checks('120s',false)]},
  {name:'old WAL timestamp cannot become current merely because it arrived later',interval:'15s',input_series:metrics.map(([n,v])=>series(n,`${v} ${v}`)),promql_expr_test:checks('240s',false)},
  {name:'scrape zero suppresses current domain and historic query steps',interval:'15s',input_series:healthy.map(s=>s.series.startsWith('up{')?{...s,values:'0x30'}:s),promql_expr_test:checks('60s',false)},
  {name:'collector own-up zero suppresses retained domain values',interval:'15s',input_series:healthy.map(s=>s.series.startsWith('fasttowin_redis_monitor_up{')?{...s,values:'0x30'}:s),promql_expr_test:checks('60s',false)},
  {name:'missing own-up suppresses retained domain values',interval:'15s',input_series:healthy.filter(s=>!s.series.startsWith('fasttowin_redis_monitor_up{')),promql_expr_test:checks('60s',false)},
  {name:'future-only samples are excluded by query time',interval:'15s',input_series:metrics.map(([n,v])=>series(n,`_x7 ${v}x10`)),promql_expr_test:checks('60s',false)},
  // Pin only timestamp's source at t=120s while evaluating at t=60s. This
  // forces the negative-age branch even though normal instant selectors
  // exclude future samples. A t=120 probe makes promtool load that timestamp
  // before evaluating t=60. The rest of each shipped query is unchanged.
  {name:'negative sample age guard rejects future timestamp explicitly',interval:'15s',input_series:healthy,promql_expr_test:[...checks('60s',false,e=>e.replaceAll(`timestamp(up{${fixed}})`,`timestamp(up{${fixed}} @ 120)`)),{expr:'vector(1)',eval_time:'120s',exp_samples:[{labels:'{}',value:1}]}]},
  {name:'delayed current recovery restores correct values and history gaps',interval:'15s',input_series:metrics.map(([n,v])=>series(n,`${v} ${v} _x18 ${v}x10`)),promql_expr_test:[...checks('240s',false),...checks('315s',true)]},
  {name:'counter reset remains zero increase',interval:'1m',input_series:metrics.map(([n,v])=>series(n,n.endsWith('_total')?'10 10 0 0 0 0':`${v}x5`)),promql_expr_test:checks('240s',true)},
 ];
 assert.equal(targets.length,15); assert.equal(expected.size,15);
 verifyPromql(cases);
});

test('each shipped rule expression rejects stale and negative ages using real PromQL',()=>{
 const file=process.env.REDIS_CLOUD_RULES ?? 'deploy/monitoring/redis-monitor-cloud/redis-alerts.yml';
 // Extract the exact single-line expressions from the shipped YAML. Assertions
 // below concern their evaluated results, rather than their source spelling.
 const rules=[...fs.readFileSync(file,'utf8').matchAll(/alert: (\w+)\r?\n        expr: (.+)/g)].map(m=>({name:m[1],expr:m[2]}));
 assert.equal(rules.length,8);
 const check=(t,missingValue,transform=x=>x)=>rules.map(r=>({expr:transform(r.expr),eval_time:t,exp_samples:r.name==='FastToWinRedisCollectorMissing'?[{labels,value:missingValue}]:[]}));
 const bad=metrics.map(([n,v])=>series(n,n==='fasttowin_redis_monitor_evicted_keys_total'?'0 10':`${n.includes('aof_')?0:v} ${n.includes('aof_')?0:v}`));
 const unlimited=bad.map(s=>s.series.startsWith('fasttowin_redis_monitor_memory_max_bytes{')?{...s,values:'0 0'}:s);
 const failed=bad.map(s=>s.series.startsWith('fasttowin_redis_monitor_up{')?{...s,values:'0 0'}:s);
 const extend=s=>({...s,values:`${s.values} ${s.values.split(' ').at(-1)}x8`});
 const futureInput=bad.map(extend);
 const futureChecks=[...check('60s',-60,e=>e.replaceAll(`timestamp(up{${fixed}})`,`timestamp(up{${fixed}} @ 120)`)),{expr:'vector(1)',eval_time:'120s',exp_samples:[{labels:'{}',value:1}]}];
 verifyPromql([
  {name:'all domain conditions suppressed after 60 seconds even with old own-up and scrape one',interval:'15s',input_series:bad,promql_expr_test:check('76s',61)},
  {name:'unlimited memory suppressed after freshness cutoff',interval:'15s',input_series:unlimited,promql_expr_test:check('76s',61)},
  {name:'old scrape with own-up zero is shipping unavailable rather than a current collection failure',interval:'15s',input_series:failed,promql_expr_test:check('76s',61)},
  {name:'all rule expressions reject negative sample age with active domain failures',interval:'15s',input_series:futureInput,promql_expr_test:futureChecks},
  {name:'negative sample age suppresses unlimited memory',interval:'15s',input_series:unlimited.map(extend),promql_expr_test:futureChecks},
  {name:'negative sample age suppresses collection failed',interval:'15s',input_series:failed.map(extend),promql_expr_test:futureChecks},
 ]);
});

test('cloud import resolves a selected datasource and renders explicit text states with units and gaps',()=>{
 assert.equal(dashboard.uid,'fasttowin-redis-cloud');
 assert.deepEqual(dashboard.__inputs,[{name:'DS_PROMETHEUS',label:'Prometheus',description:'Select the private Cloud metrics datasource',type:'datasource',pluginId:'prometheus',pluginName:'Prometheus'}]);
 const imported=JSON.parse(JSON.stringify(dashboard).replaceAll('${DS_PROMETHEUS}','synthetic-cloud-datasource'));
 for(const p of imported.panels){
  assert.equal(p.datasource.uid,'synthetic-cloud-datasource');
  assert.equal(p.fieldConfig.defaults.noValue,'Unavailable');
  assert.ok(p.title.length>0); assert.ok(p.fieldConfig.defaults.unit);
  if(p.type==='stat')for(const t of p.targets){assert.equal(t.instant,true);assert.equal(t.range,false);}
  if(p.type==='timeseries'){assert.equal(p.fieldConfig.defaults.custom.spanNulls,false);for(const t of p.targets)assert.ok(t.legendFormat);}
 }
 const status=imported.panels.filter(p=>p.id<=4);
 for(const p of status){const values=p.fieldConfig.defaults.mappings.flatMap(m=>Object.values(m.options)).map(v=>v.text);assert.ok(values.length>=2);}
 assert.equal(imported.panels.find(p=>p.id===6).fieldConfig.defaults.mappings[0].options['0'].text,'Unlimited');
});
