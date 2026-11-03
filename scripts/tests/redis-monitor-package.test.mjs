import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import cp from 'node:child_process';
import path from 'node:path';
const dir = path.resolve('deploy/monitoring/redis-monitor');
test('expanded opt-in fixture isolates credentials, ports and collector privileges', () => {
  const env = {...process.env, FTW_MONITOR_RUN_ID:'contracttest', FTW_MONITOR_ARTIFACT:path.resolve('.artifacts/redis-monitor-contract'), FTW_MONITOR_CONTEXT:path.resolve('.artifacts/redis-monitor-contract/image')};
  const r=cp.spawnSync('docker',['compose','-f',path.join(dir,'compose.local.yaml'),'config','--no-env-resolution','--format','json'],{env,encoding:'utf8'});
  assert.equal(r.status,0,r.stderr);
  const c=JSON.parse(r.stdout), s=c.services;
  for(const volume of Object.values(c.volumes)) assert.equal(volume.labels['fasttowin.monitor-run'],'contracttest');
  for(const network of Object.values(c.networks)) {assert.equal(network.labels['fasttowin.monitor-run'],'contracttest');assert.equal(network.internal,true);}
  assert.deepEqual(Object.keys(s).sort(),['alertmanager','collector','grafana','prometheus','redis']);
  for (const service of Object.values(s)) {
    assert.equal(service.labels['fasttowin.monitor-run'],'contracttest');
    for(const p of service.ports??[]) assert.equal(p.host_ip,'127.0.0.1');
    for(const v of service.volumes??[]) assert.ok(!/docker.sock|production|backup-password|app-password|postgres/i.test(v.source));
  }
  const m=s.collector;
  assert.equal(m.user,'65532:65532'); assert.equal(m.read_only,true);
  assert.deepEqual(m.cap_drop,['ALL']); assert.ok(m.security_opt.includes('no-new-privileges:true'));
  assert.equal(Number(m.cpus),0.1); assert.equal(Number(m.mem_limit),67108864);
  assert.deepEqual(Object.keys(m.environment).sort(),['FASTTOWIN_REDIS_MONITOR_ADDRESS','FASTTOWIN_REDIS_MONITOR_LISTEN','FASTTOWIN_REDIS_MONITOR_PASSWORD_FILE']);
  assert.equal(m.environment.FASTTOWIN_REDIS_MONITOR_ADDRESS,'redis:6379');
  assert.equal(m.volumes.length,1); assert.equal(m.volumes[0].target,'/run/secrets/monitor'); assert.equal(m.volumes[0].read_only,true);
  for(const name of ['prometheus','grafana','alertmanager']) assert.match(s[name].image,/@sha256:[a-f0-9]{64}$/);
  assert.match(fs.readFileSync(path.join(dir,'Dockerfile'),'utf8'),/FROM gcr.io\/distroless\/static-debian13@sha256:[a-f0-9]{64}/);
});
test('current dashboard values gate on authoritative own-up and show unavailable',()=>{
  const d=JSON.parse(fs.readFileSync('deploy/monitoring/grafana/dashboards/fasttowin-redis.json'));
  for(const p of d.panels.filter(p=>p.type==='stat')) {
    assert.equal(p.fieldConfig.defaults.noValue,'Unavailable');
    for(const t of p.targets) assert.ok(t.instant);
    for(const t of p.targets) assert.match(t.expr,/and on\(job,instance\) \(up\{job="fasttowin-redis-monitor"\} == 1\)/);
    if(p.id!==1) for(const t of p.targets) assert.match(t.expr,/and on\(job,instance\).*fasttowin_redis_monitor_up/);
  }
  for(const p of d.panels.filter(p=>p.type==='timeseries')) assert.equal(p.fieldConfig.defaults.custom.spanNulls,false);
});
