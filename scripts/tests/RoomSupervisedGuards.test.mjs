import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {localEndpoint,ownedPath,localDocker} from '../lib/room-supervised-local.mjs';
test('endpoint guard rejects remote, newline and argument injection before Docker',()=>{
 for(const e of ['ssh://remote','tcp://127.0.0.1:2375','npipe:////./pipe/docker_engine\n','unix:///var/run/docker.sock --host remote','']) assert.throws(()=>localEndpoint(e));
 for(const e of ['npipe:////./pipe/docker_engine','unix:///var/run/docker.sock']) assert.equal(localEndpoint(e),e);
 const previous=process.env.DOCKER_HOST;
 try {process.env.DOCKER_HOST='ssh://remote';assert.throws(()=>localDocker('.',null));}
 finally {if(previous===undefined)delete process.env.DOCKER_HOST;else process.env.DOCKER_HOST=previous}
});
test('artifact guard rejects escape, root and an existing junction',()=>{
 const root=fileURLToPath(new URL('../../.artifacts/',import.meta.url)),dir=fs.mkdtempSync(path.join(root,'task5-path-'));
 try {
  assert.equal(ownedPath(root,path.join(dir,'receipt.json')),path.join(dir,'receipt.json'));
  assert.throws(()=>ownedPath(root,root));assert.throws(()=>ownedPath(root,path.join(root,'../outside')));
  fs.symlinkSync(root,path.join(dir,'redirect'),'junction');
  assert.throws(()=>ownedPath(root,path.join(dir,'redirect','escape.json')));
  assert.throws(()=>ownedPath(path.join(dir,'redirect','nested'),path.join(dir,'redirect','nested','receipt.json')));
 } finally {
  if(fs.existsSync(path.join(dir,'redirect'))) fs.unlinkSync(path.join(dir,'redirect'));
  fs.rmdirSync(dir);
 }
});
