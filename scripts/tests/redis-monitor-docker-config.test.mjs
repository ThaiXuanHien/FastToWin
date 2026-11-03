import {test} from 'node:test';
import assert from 'node:assert/strict';
import cp from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {dockerConfig} from './redis-monitor-docker-config.mjs';
test('isolated config discovers installed Compose without user registry config',()=>{
  const executable=cp.spawnSync('where.exe',['docker'],{encoding:'utf8'}).stdout.trim().split(/\r?\n/)[0];
  const owned=fs.mkdtempSync(path.join(os.tmpdir(),'ftw-monitor-config-'));
  try {
    const config=dockerConfig(executable);
    assert.deepEqual(config.auths,{});
    assert.equal(config.credsStore,undefined);
    fs.writeFileSync(path.join(owned,'config.json'),JSON.stringify(config));
    const result=cp.spawnSync(executable,['--config',owned,'compose','version'],{encoding:'utf8',windowsHide:true});
    assert.equal(result.status,0,result.stderr);
    assert.match(result.stdout,/Docker Compose version/);
  }finally{fs.unlinkSync(path.join(owned,'config.json'));fs.rmdirSync(owned);}
});
