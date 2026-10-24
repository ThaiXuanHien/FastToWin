import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
const source = await readFile(new URL('../../server/src/main/resources/admin/admin-missions.js', import.meta.url), 'utf8').catch(() => '');
const { validateMission, createMissionSubmission, missionSummary } = await import('data:text/javascript;base64,' + Buffer.from(source).toString('base64'));
const draft = { code:'TEST_PLAY', scope:'PERSONAL', action:'PLAY', period:'DAILY', difficulty:'NORMAL', target:3, reward:{xp:20,gold:100,gems:0,contributionPoints:0} };
test('validation preserves exact protocol and rejects unsupported params and economy', () => {
  assert.deepEqual(validateMission(draft), {});
  assert.ok(validateMission({...draft,target:-1}).target);
  assert.ok(validateMission({...draft,action:'ACCURACY'}).accuracyPercent);
  assert.ok(validateMission({...draft,accuracyPercent:80}).accuracyPercent);
  assert.ok(validateMission({...draft,startsOn:'2026-10-26',endsOn:'2026-10-25'}).endsOn);
  assert.ok(validateMission({...draft,scope:'CLAN',action:'DONATE_GOLD'}).gold);
  assert.ok(validateMission({...draft,action:'DONATE_GOLD',target:3}).gold);
  assert.ok(validateMission({...draft,scope:'CLAN',action:'WIN_STREAK'}).streakThreshold);
  assert.match(missionSummary({...draft,scope:'CLAN',action:'WIN_STREAK',target:5,streakThreshold:3}), /5 thành viên.*3/);
});
test('unknown result retains body and id; changed payload gets fresh id; stale requires reload', async () => {
  const calls=[]; let n=0;
  const submit=createMissionSubmission({role:()=> 'OWNER', current:()=>true, uuid:()=>`request-${++n}`, send:async b=>{calls.push(b); if(calls.length===1) throw new Error('lost'); return b;}});
  const payload={expectedVersion:1,reason:'Create',draft};
  await assert.rejects(submit.submit(payload)); await submit.submit(payload); await submit.submit({...payload,reason:'Changed'});
  assert.deepEqual(calls[0],calls[1]); assert.notEqual(calls[1].requestId,calls[2].requestId);
  const stale=createMissionSubmission({role:()=> 'OWNER',current:()=>true,send:async()=>{throw Object.assign(new Error('stale'),{status:409});}});
  await assert.rejects(stale.submit(payload)); await assert.rejects(stale.submit(payload),/Tải lại/);
});
test('current role, view fence and double submit guard every mutation', async () => {
  let role='OWNER',current=true,resolve;const calls=[];
  const submit=createMissionSubmission({role:()=>role,current:()=>current,ownerOnly:true,send:b=>{calls.push(b);return new Promise(r=>resolve=r);}});
  const pending=submit.submit({reason:'Publish',expectedVersion:1});
  await assert.rejects(submit.submit({reason:'Publish',expectedVersion:1}),/xử lý/); resolve({}); await pending;
  role='OPERATOR'; await assert.rejects(submit.submit({}),/quyền/); role='OWNER';current=false; await assert.rejects(submit.submit({}),/hết hiệu lực/); assert.equal(calls.length,1);
});
