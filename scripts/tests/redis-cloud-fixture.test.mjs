import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import https from 'node:https';
import http from 'node:http';
import cp from 'node:child_process';
import {randomBytes} from 'node:crypto';
import {renderAlloy} from '../../deploy/monitoring/redis-monitor-cloud/prepare.mjs';

const fixture = await import('./redis-cloud-fixture.mjs').catch(()=>({}));
const receiver = await import('./redis-cloud-receiver.mjs').catch(()=>({}));
const input={url:'https://prometheus-fixture.grafana.net/api/prom/push',expectedHost:'prometheus-fixture.grafana.net',tenantId:'12345',collectorAddress:'redis-monitor.railway.internal:19121'};
test('fixture only accepts a unique descendant of its dedicated workspace artifact root',()=>{
  assert.equal(typeof fixture.ownedPath,'function');
  const repo=path.resolve('.'),id='a'.repeat(32);
  assert.equal(fixture.ownedPath(repo,id),path.join(repo,'.artifacts/redis-cloud-local-20261103','run-'+id));
  for(const id of ['../production','A'.repeat(32),'a'.repeat(31),''])assert.throws(()=>fixture.ownedPath(repo,id));
});
test('local trust and endpoint variant leaves production scrape, relabel and queue bytes intact',()=>{
  assert.equal(typeof fixture.localAlloy,'function');
  const production=renderAlloy(input),local=fixture.localAlloy();
  const reverted=local.replace('https://receiver:9443/api/v1/write',input.url).replace('    tls_config {\n      ca_file = "/fixture/ca.pem"\n    }\n','');
  assert.equal(reverted,production);
  assert.throws(()=>renderAlloy({...input,url:'https://receiver:9443/api/v1/write'}));
  const accelerated=fixture.localAlloy({accelerated:true});
  assert.equal(accelerated.replace('truncate_frequency = "10s"','truncate_frequency = "5m"').replace('min_keepalive_time = "10s"','min_keepalive_time = "5m"').replace('max_keepalive_time = "30s"','max_keepalive_time = "2h"'),local);
});
test('real HTTPS receiver enforces TLS, path, credentials, redirect and unchanged compressed forwarding',async()=>{
  assert.equal(typeof receiver.startReceiver,'function');
  const dir=fixture.ownedPath(path.resolve('.'),randomBytes(16).toString('hex'));
  fs.mkdirSync(dir,{recursive:true,mode:0o700});
  if(process.platform==='win32')assert.equal(cp.spawnSync('icacls.exe',[dir,'/inheritance:r','/grant:r',`${process.env.USERDOMAIN}\\${process.env.USERNAME}:(OI)(CI)F`],{windowsHide:true,stdio:'ignore'}).status,0);
  let server,upstream;
  try{
    fixture.certificates(dir);
    const body=randomBytes(173),received=[];
    upstream=http.createServer((req,res)=>{const chunks=[];req.on('data',c=>chunks.push(c));req.on('end',()=>{received.push(Buffer.concat(chunks));res.writeHead(204).end();});});
    await new Promise(r=>upstream.listen(0,'127.0.0.1',r));
    let mode='accept';const counts=[];
    server=await receiver.startReceiver({key:fs.readFileSync(path.join(dir,'key.pem')),cert:fs.readFileSync(path.join(dir,'ca.pem')),token:'synthetic-only',host:'127.0.0.1',port:0,upstream:`http://127.0.0.1:${upstream.address().port}/api/v1/write`,mode:()=>mode,receipt:r=>counts.push(r),inspectBody:input=>{assert.deepEqual(input,body);return {memoryTimes:[12345],privateValue:'must-not-log'};}});
    const send=({ca=fs.readFileSync(path.join(dir,'ca.pem')),servername='localhost',token='synthetic-only',route='/api/v1/write'}={})=>new Promise((resolve,reject)=>{
      const req=https.request({host:'127.0.0.1',port:server.address().port,path:route,method:'POST',ca,servername,headers:{authorization:'Basic '+Buffer.from('12345:'+token).toString('base64'),'content-encoding':'snappy','content-type':'application/x-protobuf'}},res=>{res.resume();res.on('end',()=>resolve(res.statusCode));});req.on('error',reject);req.end(body);
    });
    assert.equal(await send(),204);assert.deepEqual(received,[body]);
    assert.equal(await send({token:'wrong'}),401);assert.equal(await send({route:'/wrong'}),404);
    await assert.rejects(send({ca:undefined,servername:'wrong.invalid'}));
    await assert.rejects(send({ca:Buffer.from(''),servername:'localhost'}));
    mode='redirect';assert.equal(await send(),307);assert.equal(received.length,1);
    mode='unavailable';assert.equal(await send(),503);assert.equal(received.length,1);
    assert.deepEqual(counts.at(-1).memoryTimes,[12345]);
    assert.ok(counts.every(r=>Object.keys(r).every(k=>['status','bytes','sha256','at','memoryTimes'].includes(k))));
    assert.ok(!JSON.stringify(counts).includes('synthetic-only'));
  }finally{if(server)await new Promise(r=>server.close(r));if(upstream)await new Promise(r=>upstream.close(r));fs.rmSync(dir,{recursive:true,force:true});}
});
