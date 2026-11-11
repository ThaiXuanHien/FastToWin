import fs from 'node:fs';
import path from 'node:path';
import {execFileSync, spawn} from 'node:child_process';

export function localEndpoint(endpoint) {
  if (!/^(npipe:\/\/\/\/\.\/pipe\/[a-zA-Z0-9_.-]+|unix:\/\/\/[a-zA-Z0-9_./-]+)$/.test(endpoint)) throw new Error('Local Docker endpoint required');
  return endpoint;
}
export function ownedPath(root, target) {
  const base=path.resolve(root), resolved=path.resolve(target);
  if (!resolved.startsWith(base+path.sep)) throw new Error('Artifact path escapes owned directory');
  let current=resolved;
  while (true) {
    if(fs.existsSync(current) && fs.lstatSync(current).isSymbolicLink()) throw new Error('Artifact path traverses a link');
    const parent=path.dirname(current);
    if(parent===current)break;
    current=parent;
  }
  return resolved;
}
export function localDocker(root, commandLog) {
  const endpoint=localEndpoint(process.env.DOCKER_HOST || (process.platform==='win32'?'npipe:////./pipe/docker_engine':'unix:///var/run/docker.sock'));
  function args(values) {
    localEndpoint(endpoint);
    if(process.env.DOCKER_HOST) localEndpoint(process.env.DOCKER_HOST);
    if(commandLog) fs.appendFileSync(commandLog,JSON.stringify({at:new Date().toISOString(),args:values})+'\n');
    return ['--host',endpoint,...values];
  }
  return {
    run(values,options={}) {
      try {return execFileSync('docker',args(values),{cwd:root,encoding:'utf8',windowsHide:true,timeout:120000,maxBuffer:4*1024*1024,stdio:['pipe','pipe','pipe'],...options}).trim();}
      catch(e) { throw new Error('Local Docker '+values[0]+' failed, exit '+e.status); }
    },
    spawn(values,options={}) {return spawn('docker',args(values),{cwd:root,windowsHide:true,...options});}
  };
}
