// Opt-in real-runner failure lifecycle test. Requires the same guarded owned PostgreSQL settings.
// A missing Java error handler or waiting to stop a never-started child must fail this test.
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {readFile,writeFile,mkdir,unlink,stat} from 'node:fs/promises';
import {dirname,join,resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {validateDatabaseSettings} from '../../scripts/lib/load-test-utils.mjs';
const repo=resolve(dirname(fileURLToPath(import.meta.url)),'../..');
const artifact=join(repo,'.artifacts/admin-missions/task-4-live');
const diagnostics=join(repo,'.artifacts/admin-missions/task-4-fix-1');
const exists=async path=>Boolean(await stat(path).catch(()=>null));
function execute(executable,args,environment,input){return new Promise((yes,no)=>{
 const child=spawn(executable,args,{cwd:repo,env:environment,windowsHide:true,stdio:['pipe','pipe','pipe']});let stdout='',stderr='';
 child.stdout.on('data',b=>stdout+=b);child.stderr.on('data',b=>stderr+=b);
 const timer=setTimeout(()=>{child.kill();no(Error('Owned startup test subprocess timeout'));},15000);
 child.once('error',error=>{clearTimeout(timer);no(error);});child.once('close',code=>{clearTimeout(timer);yes({code,stdout,stderr});});
 child.stdin.on('error',()=>{});child.stdin.end(input);
});}

test('missing Java executable still removes owned password and schema and writes FAIL evidence', {timeout:30000}, async()=>{
 assert.ok(process.env.E2E_POSTGRES_SETTINGS,'Explicit owned PostgreSQL settings required; no skipped failure coverage');
 const settings=JSON.parse(await readFile(process.env.E2E_POSTGRES_SETTINGS,'utf8'));validateDatabaseSettings(settings);
 const psql=process.env.E2E_PSQL||'psql';const password=(await readFile(settings.passwordFile,'utf8')).trim();
 async function sql(query){const result=await execute(psql,['-X','-q','-t','-A','-v','ON_ERROR_STOP=1','-h','127.0.0.1','-p','15432','-d','admin_test','-U',settings.user],{...process.env,PGPASSWORD:password,PGCONNECT_TIMEOUT:'5'},query);assert.equal(result.code,0,'Owned startup-test SQL succeeds');return result.stdout.trim();}
 const schemas=async()=>new Set((await sql("SELECT schema_name FROM information_schema.schemata WHERE schema_name LIKE 'webe2e_%';")).split(/\r?\n/).filter(Boolean));
 await mkdir(artifact,{recursive:true});await mkdir(diagnostics,{recursive:true});
 const passwordPath=join(artifact,'owned-owner-password.txt');assert.equal(await exists(passwordPath),false,'No pre-existing acceptance password may be overwritten');
 const retained=new Map();const files=['acceptance.json','backend.log','mission-clock.txt'];
 for(const name of files){const path=join(artifact,name);retained.set(path,await readFile(path).catch(error=>{if(error.code==='ENOENT')return null;throw error;}));}
 const baseline=await schemas();let created=[];
 try{
  await unlink(join(artifact,'acceptance.json')).catch(error=>{if(error.code!=='ENOENT')throw error;});
  const absentJdk=join(repo,'.artifacts','admin-missions','absent-java-startup-regression');assert.equal(await exists(absentJdk),false);
  const result=await execute(process.execPath,['e2e/support/admin-missions-live.mjs'],{...process.env,JAVA_HOME:absentJdk});
  created=[...await schemas()].filter(schema=>!baseline.has(schema));
  const failureEvidence=JSON.parse(await readFile(join(artifact,'acceptance.json'),'utf8').catch(error=>{if(error.code==='ENOENT')return 'null';throw error;}));
  if(failureEvidence)await writeFile(join(diagnostics,'failed-start-acceptance.json'),JSON.stringify(failureEvidence,null,2));
  const actual={exit:result.code,unhandledError:result.stderr.includes("Unhandled 'error'"),passwordPresent:await exists(passwordPath),leftoverSchemas:created.length,status:failureEvidence?.status??null,failedSteps:failureEvidence?.failedSteps??null,passwordCleanup:failureEvidence?.cleanup?.password??null,backendCleanup:failureEvidence?.cleanup?.backend??null,schemaCleanup:failureEvidence?.cleanup?.schema??null};
  await writeFile(join(diagnostics,'startup-observation.json'),JSON.stringify(actual,null,2));
  assert.deepEqual(actual,{exit:1,unhandledError:false,passwordPresent:false,leftoverSchemas:0,status:'FAIL',failedSteps:['acceptance'],passwordCleanup:'done',backendCleanup:'done',schemaCleanup:'done'});
 }finally{
  // On RED the test owns the leaked UUID schema; never touch a schema present before this run.
  for(const schema of created){assert.match(schema,/^webe2e_[a-f0-9]{32}$/);assert.equal(baseline.has(schema),false);await sql(`DROP SCHEMA IF EXISTS ${schema} CASCADE;`);}
  await unlink(passwordPath).catch(error=>{if(error.code!=='ENOENT')throw error;});
  for(const [path,bytes] of retained){if(bytes===null)await unlink(path).catch(error=>{if(error.code!=='ENOENT')throw error;});else await writeFile(path,bytes);}
 }
});
