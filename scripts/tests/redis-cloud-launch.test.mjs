import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {randomUUID} from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {dockerConfig} from './redis-monitor-docker-config.mjs';
const root=path.resolve('deploy/monitoring/redis-monitor-cloud');
const artifact=path.resolve('.artifacts/redis-cloud-local-20261103','task1-launch-'+randomUUID());
const monitor='a'.repeat(96), token='synthetic_write_token_123456789';
const good={GRAFANA_CLOUD_REMOTE_WRITE_URL:'https://prometheus-fixture.grafana.net/api/prom/push',GRAFANA_CLOUD_EXPECTED_HOST:'prometheus-fixture.grafana.net',GRAFANA_CLOUD_TENANT_ID:'123456',FASTTOWIN_REDIS_MONITOR_SCRAPE_ADDRESS:'redis-monitor.railway.internal:19121'};
const image='node@sha256:c86ecead9e55a5210ac1727de38a4ca840bf03dcaf34dc9749addbf383b1920c';
const docker=process.env.FTW_TEST_DOCKER||'C:/Users/Hien Hue/AppData/Local/Programs/DockerDesktop/resources/bin/docker.exe';
let ready=false;
function setup(){
 if(ready)return;
 fs.mkdirSync(artifact,{recursive:true,mode:0o700});
 if(process.platform==='win32') {
  const owner=process.env.USERDOMAIN+'\\'+process.env.USERNAME;
  const acl=spawnSync('icacls.exe',[artifact,'/inheritance:r','/grant:r',owner+':(OI)(CI)F'],{encoding:'utf8'});
  assert.equal(acl.status,0,'Could not restrict synthetic fixture ACL');
 }
 fs.mkdirSync(path.join(artifact,'docker-config'));
 fs.writeFileSync(path.join(artifact,'docker-config/config.json'),JSON.stringify(dockerConfig(docker)));
 ready=true;
}
function restartCommand(service){
 const variable=service==='collector'?'FASTTOWIN_REDIS_MONITOR_PASSWORD':'GRAFANA_CLOUD_WRITE_TOKEN';
 const original=service==='collector'?monitor:token;
 const changed=service==='collector'?'b'.repeat(96):'synthetic_changed_write_token_456';
 const file=service==='collector'?'redis-monitor':'grafana-write';
 return `set -eu
printf '%s' '${original}' > /run/expected
/bin/sh /opt/redis-monitor-cloud/${service}-entrypoint.sh
 test -d /run/secrets
printf '%s' '${changed}' > /run/expected
export ${variable}='${changed}'
/bin/sh /opt/redis-monitor-cloud/${service}-entrypoint.sh
${service==='collector'?'export FASTTOWIN_REDIS_MONITOR_PASSWORD=invalid':'export GRAFANA_CLOUD_TENANT_ID=invalid'}
if /bin/sh /opt/redis-monitor-cloud/${service}-entrypoint.sh; then exit 95; fi
test ! -e /run/secrets
mkdir -m700 /run/secrets
printf '${service}-runtime-v1\n' > /run/secrets/.launcher-owner
chmod 600 /run/secrets/.launcher-owner
reject() { if /usr/local/bin/node /opt/redis-monitor-cloud/runtime-directory.mjs ${service} start; then exit 97; fi; }
touch /run/secrets/unknown; chmod 600 /run/secrets/unknown
reject; test -f /run/secrets/unknown; rm /run/secrets/unknown
ln -s /run/expected /run/secrets/${file}
reject; test -L /run/secrets/${file}; rm /run/secrets/${file}
chmod 644 /run/secrets/.launcher-owner
reject; test -f /run/secrets/.launcher-owner
chmod 600 /run/secrets/.launcher-owner
printf 'wrong-role-runtime-v1\n' > /run/secrets/.launcher-owner
reject; test -f /run/secrets/.launcher-owner
printf '${service}-runtime-v1\n' > /run/secrets/.launcher-owner
chmod 755 /run/secrets
reject; test -d /run/secrets
chmod 700 /run/secrets
/usr/local/bin/node /opt/redis-monitor-cloud/runtime-directory.mjs ${service} cleanup
test ! -e /run/secrets
printf 'RESTART_CHANGED_SECRET_FAILURE_CLEANUP_AND_REFUSAL_OK\n'
`;
}
function run(service,env={},exit=0,failWrite=false,restart=false){
 assert.ok(fs.existsSync(path.join(root,service+'-entrypoint.sh')),service+' launcher is not implemented');setup();
 const fake=path.join(artifact,service+'-'+randomUUID());
 fs.writeFileSync(fake,`#!/bin/sh\nset -eu\n${service==='sender'?'if [ "$1" = validate ]; then exit 0; fi\n':''}file=${service==='collector'?'$FASTTOWIN_REDIS_MONITOR_PASSWORD_FILE':'/run/secrets/grafana-write'}\n[ -f "$file" ] && [ ! -L "$file" ]\n[ "$(cat "$file")" = ${restart?'"$(cat /run/expected)"':"'"+(service==='collector'?monitor:token)+"'"} ]\n[ "$(stat -c %a "$file")" = 600 ]\n[ "$(stat -c %u "$file")" = "$(id -u)" ]\n[ "$(stat -c %a /run/secrets)" = 700 ]\nprintf 'PRIVATE_FILE_OK\\n'\n[ "$#" -eq 0 ] || printf 'ARG:%s\\n' "$@"\nenv\nexit ${exit}\n`,{mode:0o700});
 const args=['--config',path.join(artifact,'docker-config'),'--host','npipe:////./pipe/docker_engine','run','--rm','--name','ftw-cloud-launch-'+randomUUID(),'--label','fasttowin.fixture=redis-cloud-task1','--network','none','--read-only','--cap-drop','ALL','--security-opt','no-new-privileges','--pids-limit','48','--user','65532:65532','--tmpfs',`/run:rw,noexec,nosuid,mode=0700,uid=65532,gid=65532${failWrite?',size=4096':''}`,'--mount',`type=bind,source=${root},target=/opt/redis-monitor-cloud,readonly`,'--mount',`type=bind,source=${fake},target=${service==='collector'?'/usr/local/bin/redis-monitor':'/bin/alloy'},readonly`];
 // Each test owns these files, containing only synthetic data. Credentials never appear in Docker arguments.
 const envFile=path.join(artifact,'env-'+randomUUID());fs.writeFileSync(envFile,Object.entries(env).map(([k,v])=>k+'='+v).join('\n'),{mode:0o600});
 const command=restart?restartCommand(service):failWrite?
  `mkdir -m700 /run/secrets; touch /run/secrets/block; /bin/sh /opt/redis-monitor-cloud/${service}-entrypoint.sh; code=$?; test -f /run/secrets/block || exit 90; exit "$code"`:
  `/bin/sh /opt/redis-monitor-cloud/${service}-entrypoint.sh; code=$?; if [ "$code" = 1 ]; then test ! -e /run/secrets || exit 91; printf 'SETUP_CLEANUP_OK\\n'; fi; exit "$code"`;
 if(restart){
  args.splice(args.indexOf('--read-only'),1);
  args[args.indexOf('--user')+1]='0:0';
  args.push('--cap-add','CHOWN','--cap-add','SETUID','--cap-add','SETGID');
  args.splice(args.indexOf('--tmpfs'),2);
  // Keep /run in the actual writable container layer across both invocations.
  args.push('--env-file',envFile,'--entrypoint','/usr/local/bin/node',image,'-e',`const fs=require('fs');fs.chmodSync('/run',0o700);fs.chownSync('/run',65532,65532);const r=require('child_process').spawnSync('/bin/sh',['-c',process.argv[1]],{uid:65532,gid:65532,env:process.env,stdio:'inherit'});process.exit(r.status??1);`,command);
 }else args.push('--env-file',envFile,'--entrypoint','/bin/sh',image,'-c',command);
 try{return spawnSync(docker,args,{encoding:'utf8',timeout:60000});}finally{fs.rmSync(envFile,{force:true});fs.rmSync(fake,{force:true});}
}
function safe(result,expected){assert.equal(result.error,undefined);assert.equal(result.status,expected,result.stdout+result.stderr);assert.doesNotMatch(result.stdout+result.stderr,new RegExp(monitor+'|'+token+'|MARKER'));}
test('collector writes private secret and execs only the fixed collector with a private IPv6 listener',()=>{
 const r=run('collector',{FASTTOWIN_REDIS_MONITOR_PASSWORD:monitor,FASTTOWIN_REDIS_MONITOR_ADDRESS:'redis.railway.internal:6379',GRAFANA_CLOUD_WRITE_TOKEN:token},23);safe(r,23);assert.match(r.stdout,/PRIVATE_FILE_OK/);assert.match(r.stdout,/FASTTOWIN_REDIS_MONITOR_LISTEN=\[::\]:19121/);assert.match(r.stdout,/FASTTOWIN_REDIS_MONITOR_PASSWORD_FILE=\/run\/secrets\/redis-monitor/);assert.doesNotMatch(r.stdout,/GRAFANA_|FASTTOWIN_REDIS_MONITOR_PASSWORD=|ARG:/);
});
test('sender strips credentials/environment, prepares one config and execs Alloy with safe fixed flags',()=>{
 const r=run('sender',{...good,GRAFANA_CLOUD_WRITE_TOKEN:token,FASTTOWIN_REDIS_MONITOR_PASSWORD:monitor},29);safe(r,29);assert.match(r.stdout,/PRIVATE_FILE_OK/);for(const arg of ['run','--disable-reporting','--server.http.listen-addr=127.0.0.1:12345','--server.http.enable-pprof=false','--server.http.disable-support-bundle','--storage.path=/var/lib/alloy','/run/secrets/config.alloy'])assert.ok(r.stdout.includes('ARG:'+arg+'\n'),arg);assert.doesNotMatch(r.stdout,/GRAFANA_|FASTTOWIN_|HTTP_PROXY|HTTPS_PROXY/);
});
test('launchers fail closed on missing or malformed credentials/config and preserve existing private directories',()=>{
 for(const secret of [undefined,'MARKER','a'.repeat(95),'A'.repeat(96)]){const env={FASTTOWIN_REDIS_MONITOR_ADDRESS:'redis:6379'};if(secret!==undefined)env.FASTTOWIN_REDIS_MONITOR_PASSWORD=secret;safe(run('collector',env),1);}
 for(const bad of [{GRAFANA_CLOUD_WRITE_TOKEN:''},{GRAFANA_CLOUD_WRITE_TOKEN:'MARKER with space'},{GRAFANA_CLOUD_REMOTE_WRITE_URL:'https://MARKER.invalid/api/prom/push'},{GRAFANA_CLOUD_TENANT_ID:'MARKER'},{FASTTOWIN_REDIS_MONITOR_SCRAPE_ADDRESS:'https://MARKER'}])safe(run('sender',{...good,GRAFANA_CLOUD_WRITE_TOKEN:token,...bad}),1);
 safe(run('collector',{FASTTOWIN_REDIS_MONITOR_PASSWORD:monitor,FASTTOWIN_REDIS_MONITOR_ADDRESS:'redis:6379'},0,true),1);
});
test('same container filesystem refreshes changed secrets and cleans failed third invocation',()=>{for(const service of ['sender','collector'])safe(run(service,{...good,GRAFANA_CLOUD_WRITE_TOKEN:token,FASTTOWIN_REDIS_MONITOR_PASSWORD:monitor,FASTTOWIN_REDIS_MONITOR_ADDRESS:'redis:6379'},0,false,true),0);});

test.after(()=>{if(ready)fs.rmSync(artifact,{recursive:true,force:true});});
