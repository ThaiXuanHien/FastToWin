import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {waitForUIReady,cleanupUI} from './redis-cloud-ui.mjs';
test('hanging readiness is aborted within owning deadline',async()=>{
 const started=Date.now();let aborted=false;
 await assert.rejects(waitForUIReady(['http://fixture'],{deadline:Date.now()+40,request:(_url,{signal})=>new Promise((_,reject)=>signal.addEventListener('abort',()=>{aborted=true;reject(new Error('aborted'));}))}));
 assert.ok(aborted);assert.ok(Date.now()-started<1000);
});
test('exhausted readiness rejects',async()=>{await assert.rejects(waitForUIReady(['http://fixture'],{deadline:Date.now()+1000,attempts:2,pause:async()=>{},request:async()=>({ok:false})}));});
test('cleanup attempts every resource and writes failure receipt',async()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'ftw-ui-lifecycle-'));
 const receiptFile=path.join(dir,'cleanup.json');
 const attempted=[];
 try{const child={pid:1,exitCode:null,signalCode:null,kill(){attempted.push('kill');throw Error('kill');}};
 await assert.rejects(cleanupUI({children:[{name:'fake',child}],closeListener:async()=>{attempted.push('listener');throw Error('listener');},closeLogs:[()=>{attempted.push('log');throw Error('log');}],removeSecrets:[()=>{attempted.push('secret1');throw Error('delete');},()=>attempted.push('secret2')],waitChild:async()=>{},save:value=>fs.writeFileSync(receiptFile,JSON.stringify(value))}));
 const receipt=JSON.parse(fs.readFileSync(receiptFile,'utf8'));
 assert.deepEqual(attempted,['kill','listener','log','secret1','secret2']);assert.equal(receipt.complete,false);assert.equal(receipt.children[0].exited,false);assert.equal(receipt.actions.length,6);
 }finally{fs.rmSync(dir,{recursive:true,force:true});}
});
