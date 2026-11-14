import test from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {renderAlloy} from '../../deploy/monitoring/redis-monitor-cloud/prepare.mjs';
const input={url:'https://prometheus-fixture.grafana.net/api/prom/push',expectedHost:'prometheus-fixture.grafana.net',tenantId:'123456',collectorAddress:'redis-monitor.railway.internal:19121'};
const backendTargets={ingress:'server.railway.internal:8080',worker:'owned-worker.railway.internal:8080'};
test('optional backend scrape retains Redis pipeline and separates private A/B under one remote writer',()=>{
 const config=renderAlloy({...input,backendTargets});
 assert.equal((config.match(/prometheus.scrape "/g)||[]).length,2);
 assert.equal((config.match(/prometheus.remote_write "/g)||[]).length,1);
 for(const s of ['server.railway.internal:8080','owned-worker.railway.internal:8080','metrics_path = "/internal/metrics"','"instance" = "a"','"instance" = "b"','replacement = "fasttowin-server"','replacement = "fasttowin-redis-monitor"','sample_limit = 1000','label_limit = 12'])assert.ok(config.includes(s),s);
 assert.equal((config.match(/follow_redirects = false/g)||[]).length,3);
 assert.equal((config.match(/proxy_from_environment = false/g)||[]).length,3);
});
test('backend targets require the complete distinct Railway private pair and never echo input',()=>{
 for(const value of [null,{},[],{ingress:backendTargets.ingress},{worker:backendTargets.worker},{...backendTargets,worker:backendTargets.ingress},{...backendTargets,worker:'https://MARKER:8080'},{...backendTargets,worker:'MARKER.example.com:8080'},{...backendTargets,worker:'8.8.8.8:8080'},{...backendTargets,worker:'127.0.0.1:8080'},{...backendTargets,extra:'MARKER'},{...backendTargets,worker:'worker.railway.internal:8080\nMARKER'}])assert.throws(()=>renderAlloy({...input,backendTargets:value}),e=>e.message==='Invalid backend targets');
});
test('backend relabeling accepts only bounded operational metrics and bounded outcome/operation values',()=>{
 const c=renderAlloy({...input,backendTargets}),block=c.slice(c.indexOf('prometheus.relabel "backends"'));
 const rules=[...block.matchAll(/rule \{([\s\S]*?)\n  \}/g)].map(x=>x[1]);
 const keep=rules.find(x=>x.includes('source_labels = ["__name__", "outcome", "operation"]'));
 assert.ok(keep);const re=new RegExp('^(?:'+keep.match(/regex = "([^"]+)"/)[1]+')$');
 for(const s of ['fasttowin_room_ownership_routes_total;local;','fasttowin_room_ownership_routes_total;peer;','fasttowin_room_ownership_routes_total;unavailable;','fasttowin_rate_limit_shared_decisions_total;limited;','fasttowin_presence_redis_failures_total;;read','fasttowin_presence_redis_failures_total;;write','fasttowin_websocket_connections;;','up;;'])assert.ok(re.test(s),s);
 for(const s of ['fasttowin_room_ownership_routes_total;MARKER;','fasttowin_presence_redis_failures_total;;MARKER','fasttowin_secret_token;;','fasttowin_websocket_connections;MARKER;'])assert.equal(re.test(s),false,s);
 assert.ok(block.includes('regex = "__name__|job|instance|outcome|operation"'));
 assert.doesNotMatch(block,/password =|bearer_token =|sys.env/);
});

test('absent backend targets preserve the exact previously deployed Redis configuration',()=>{
 assert.equal(createHash('sha256').update(renderAlloy(input)).digest('hex'),'97d963bc879870b1f4964cf06c02b2a5519fbfb5a2b4c9411b1f0a2b91f0c005');
});
