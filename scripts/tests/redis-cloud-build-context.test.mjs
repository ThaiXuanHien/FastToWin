import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {randomBytes} from 'node:crypto';
import {ownedPath,stageImageContext} from './redis-cloud-fixture.mjs';

test('staged fixture context supplies every repository-local Dockerfile COPY source',()=>{
  const repo=path.resolve('.'),artifact=ownedPath(repo,randomBytes(16).toString('hex'));
  fs.mkdirSync(artifact,{recursive:true,mode:0o700});
  try{
    const collector=path.join(artifact,'synthetic-collector');
    fs.writeFileSync(collector,'synthetic build input; never executed');
    stageImageContext(repo,artifact,collector);
    const context=path.join(artifact,'image');
    let checked=0;
    for(const dockerfile of ['Dockerfile.collector','Dockerfile.sender']){
      const staged=path.join(context,'deploy/monitoring/redis-monitor-cloud',dockerfile);
      for(const line of fs.readFileSync(staged,'utf8').split(/\r?\n/)){
        if(!/^COPY\s/i.test(line)||/\s--from(?:=|\s)/.test(line))continue;
        const words=line.trim().split(/\s+/).slice(1).filter(word=>!word.startsWith('--'));
        for(const source of words.slice(0,-1)){
          const file=path.resolve(context,source);
          assert.ok(file.startsWith(context+path.sep),'COPY source must stay inside staged context');
          assert.ok(fs.statSync(file).isFile(),dockerfile+' missing staged COPY source: '+source);
          checked++;
        }
      }
    }
    assert.ok(checked>0,'must evaluate actual Dockerfile COPY consumers');
  }finally{fs.rmSync(artifact,{recursive:true,force:true});}
});
