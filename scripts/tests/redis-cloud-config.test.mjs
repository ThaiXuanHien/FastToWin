import test from 'node:test';
import assert from 'node:assert/strict';
const moduleUrl = new URL('../../deploy/monitoring/redis-monitor-cloud/prepare.mjs', import.meta.url);
const input = {url:'https://prometheus-fixture.grafana.net/api/prom/push',expectedHost:'prometheus-fixture.grafana.net',tenantId:'123456',collectorAddress:'redis-monitor.railway.internal:19121'};
const names = [
 'fasttowin_redis_monitor_up', 'fasttowin_redis_monitor_collection_duration_seconds',
 'fasttowin_redis_monitor_last_success_timestamp_seconds', 'fasttowin_redis_monitor_collection_errors_total',
 'fasttowin_redis_monitor_memory_used_bytes', 'fasttowin_redis_monitor_memory_max_bytes',
 'fasttowin_redis_monitor_evicted_keys_total', 'fasttowin_redis_monitor_aof_enabled',
 'fasttowin_redis_monitor_aof_current_size_bytes', 'fasttowin_redis_monitor_aof_base_size_bytes',
 'fasttowin_redis_monitor_aof_last_write_ok', 'fasttowin_redis_monitor_aof_last_rewrite_ok',
 'fasttowin_redis_monitor_aof_rewrite_in_progress', 'fasttowin_redis_monitor_aof_pending_fsync',
 'fasttowin_redis_monitor_aof_delayed_fsync_total', 'up',
];
async function api() { try {return await import(moduleUrl);} catch(e) {if(e.code==='ERR_MODULE_NOT_FOUND')assert.fail('Cloud preparation module is not implemented');throw e;} }
test('destination permits only the explicit Grafana HTTPS ingestion endpoint', async()=>{
 const {validateDestination}=await api();
 assert.deepEqual(validateDestination(input),{url:input.url,expectedHost:input.expectedHost,tenantId:'123456'});
 assert.deepEqual(validateDestination({...input,url:'https://prometheus-fixture.grafana.net:443/api/prom/push'}),{url:input.url,expectedHost:input.expectedHost,tenantId:'123456'});
 for(const invalid of [undefined,{},...[
  {url:'http://prometheus-fixture.grafana.net/api/prom/push'}, {url:'https://MARKER@prometheus-fixture.grafana.net/api/prom/push'},
  {url:'https://prometheus-fixture.grafana.net:444/api/prom/push'}, {url:'https://prometheus-fixture.grafana.net/api/prom/%70ush'},
  {url:'https://prometheus-fixture.grafana.net/api/../api/prom/push'}, {url:input.url+'?MARKER'}, {url:input.url+'#MARKER'},
  {url:input.url+'\nMARKER'}, {url:'MARKER'}, {url:'https://receiver:9443/api/v1/write'},
  {url:'https://prometheus-fixture.grafana.net.evil.test/api/prom/push'}, {expectedHost:'MARKER'},
  {expectedHost:'grafana.net'}, {expectedHost:'prometheus-MARKER.grafana.net'},
  {tenantId:'MARKER'}, {tenantId:''}, {tenantId:123456}, {tenantId:'123\n'}, {url:undefined},
 ].map(x=>({...input,...x}))]) {
  assert.throws(()=>validateDestination(invalid),e=>e.message==='Invalid cloud destination'&&!e.message.includes('MARKER'));
 }
});
test('renderer rejects URI, public, malformed and missing collector targets without disclosing input', async()=>{
 const {renderAlloy}=await api();
 for(const collectorAddress of [undefined,'','https://MARKER:19121','MARKER:0','MARKER:65536','MARKER:1/path','public.example.com:19121','8.8.8.8:19121','collector:19121\nMARKER','user@collector:19121','[2001:4860:4860::8888]:19121'])
  assert.throws(()=>renderAlloy({...input,collectorAddress}),e=>e.message==='Invalid collector target');
 for(const collectorAddress of ['redis-monitor.railway.internal:19121','127.0.0.1:19121','10.0.0.2:19121','172.16.0.2:19121','172.31.255.254:19121','192.168.1.2:19121','[::1]:19121','[fc00::1]:19121','[fd00::1]:19121']) assert.match(renderAlloy({...input,collectorAddress}),new RegExp('prometheus.scrape'));
});
test('renderer rejects single-label/public DNS and private-namespace near misses without echoing the target', async()=>{
 const {renderAlloy}=await api();
 for(const collectorAddress of ['ai:19121','collector:19121','marker:19121','marker.com:19121','marker.railway.internal.example.com:19121','marker.railway-internal:19121','marker.internal:19121','marker.railway.internal.:19121','railway.internal:19121','172.15.255.255:19121','172.32.0.1:19121','192.169.1.2:19121']) {
  assert.throws(()=>renderAlloy({...input,collectorAddress}),e=>e.message==='Invalid collector target'&&!e.message.includes('marker'),collectorAddress);
 }
});
test('renderer produces the fixed bounded allowlist pipeline and no credentials', async()=>{
 const {renderAlloy,metricNames}=await api(); const config=renderAlloy(input);
 assert.deepEqual(metricNames,names);assert.ok(Object.isFrozen(metricNames));
 assert.equal((config.match(/prometheus\.scrape "/g)||[]).length,1);
 assert.equal((config.match(/prometheus\.remote_write "/g)||[]).length,1);
 // Both HTTP clients must remain on their explicit endpoint; redirects could
 // otherwise bypass destination/target validation after the first request.
 assert.equal((config.match(/follow_redirects = false/g)||[]).length,2);
 assert.equal((config.match(/proxy_from_environment = false/g)||[]).length,2);
 assert.match(config,/targets = \[\{ "__address__" = "redis-monitor.railway.internal:19121" \}\]/);
 for(const literal of ['scrape_interval = "15s"','scrape_timeout = "5s"','action = "keep"','action = "labelkeep"','regex = "__name__|job|instance|reason"','replacement = "fasttowin-redis-monitor"','replacement = "redis-primary"','password_file = "/run/secrets/grafana-write"','send_exemplars = false','send = false','follow_redirects = false','proxy_from_environment = false','remote_timeout = "10s"','min_shards = 1','max_shards = 1','capacity = 1000','max_samples_per_send = 250','min_backoff = "1s"','max_backoff = "30s"','truncate_frequency = "5m"','min_keepalive_time = "5m"','max_keepalive_time = "2h"']) assert.ok(config.includes(literal),literal);
 const rules=[...config.matchAll(/rule \{([\s\S]*?)\n  \}/g)].map(x=>x[1]);
 const keep=rules.find(x=>x.includes('source_labels = ["__name__"]')&&x.includes('action = "keep"'));
 assert.ok(keep);assert.equal(keep.match(/regex = "([^"]+)"/)[1],names.join('|'));
 const reasonKeep=rules.find(x=>x.includes('source_labels = ["__name__", "reason"]'));
 const reasonPattern=new RegExp('^(?:'+reasonKeep.match(/regex = "([^"]+)"/)[1]+')$');
 for(const reason of ['connect','timeout','auth','protocol','oversized','missing','invalid'])assert.ok(reasonPattern.test('fasttowin_redis_monitor_collection_errors_total;'+reason));
 for(const reason of ['','MARKER','timeoutx'])assert.equal(reasonPattern.test('fasttowin_redis_monitor_collection_errors_total;'+reason),false);
 const removeReason=rules.find(x=>x.includes('target_label = "reason"'));
 assert.match(removeReason,/replacement = ""/);const removePattern=new RegExp('^(?:'+removeReason.match(/regex = "([^"]+)"/)[1]+')$');
 for(const name of names) assert.equal(removePattern.test(name),name!=='fasttowin_redis_monitor_collection_errors_total');
 assert.doesNotMatch(config,/insecure_skip_verify|discovery\.|prometheus\.exporter|otelcol|loki\.|pyroscope|sys\.env|password =|bearer_token =|external_labels/);
});
