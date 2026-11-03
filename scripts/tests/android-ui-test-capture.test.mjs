import test from 'node:test';
import assert from 'node:assert/strict';
import { copyFileSync, existsSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
const bash = process.platform === 'win32' ? 'C:/Program Files/Git/bin/bash.exe' : 'bash';
const scratch = resolve('.tmp');
mkdirSync(scratch, { recursive: true });
const launcher = 'com.google.android.apps.nexuslauncher';
// Captured current-focus shape from CI41595152837; opaque window ID is immaterial.
const capturedFocus = `  mCurrentFocus=Window{3aed091 u0 Application Not Responding: ${launcher}}\n`;
function runCollector({gradleExit=0,buildExit=0,diagnosticExit=0,scenario='healthy',serial='emulator-5556'}={}) {
  const directory=mkdtempSync(join(scratch,'android-ci-capture-'));
  try {
    copyFileSync('scripts/run-android-ui-tests.sh',join(directory,'run.sh'));
    writeFileSync(join(directory,'captured-focus.txt'),capturedFocus);
    mkdirSync(join(directory,'bin'));
    writeFileSync(join(directory,'gradlew'),`#!/bin/sh
printf 'gradle:%s\\n' "$*" >> calls.txt
case "$*" in
 *connectedDevDebugAndroidTest*) touch instrumentation-ran; exit "$FAKE_GRADLE_EXIT" ;;
 *) exit "$FAKE_BUILD_EXIT" ;;
esac
`,{mode:0o755});
    writeFileSync(join(directory,'bin/adb'),`#!/bin/sh
printf 'adb:%s\\n' "$*" >> calls.txt
if [ -f instrumentation-ran ]; then printf 'snapshot:%s\\n' "$*"; exit "$FAKE_DIAGNOSTIC_EXIT"; fi
if [ "$FAKE_SCENARIO" = capture-logcat-failed ]; then case "$*" in *'logcat -d'*) exit 9 ;; esac; fi
if [ "$FAKE_SCENARIO" = capture-screenshot-failed ]; then case "$*" in *'screencap -p'*) exit 9 ;; esac; fi
case "$*" in
 *'shell getprop sys.boot_completed') printf '1\\n' ;;
 *'shell cmd package resolve-activity'*)
   if [ "$FAKE_SCENARIO" = wrong-home ]; then printf 'com.example.home/.Home\\n'; else printf '${launcher}/.NexusLauncherActivity\\n'; fi ;;
 *'shell am force-stop '*) touch recovered ;;
 *'shell am start '*) [ "$FAKE_SCENARIO" != recovery-failed ] ;;
 *'shell dumpsys window')
   case "$FAKE_SCENARIO" in
    adb-failed) exit 9 ;;
    no-focus) printf 'mCurrentFocus=null\\n' ;;
    other-anr) printf 'mCurrentFocus=Window{123 u0 Application Not Responding: com.example.app}\\n' ;;
    misleading-anr) printf 'mCurrentFocus=Window{123 u0 Application Not Responding: ${launcher}.other}\\n' ;;
    recovered|persistent|recovery-failed|wrong-home|capture-logcat-failed|capture-screenshot-failed)
      if [ ! -f recovered ] || [ "$FAKE_SCENARIO" = persistent ]; then cat captured-focus.txt;
      else printf 'mCurrentFocus=Window{123 u0 ${launcher}/${launcher}.NexusLauncherActivity}\\n'; fi ;;
    *) printf 'mCurrentFocus=Window{123 u0 ${launcher}/${launcher}.NexusLauncherActivity}\\n' ;;
   esac ;;
 *) printf 'snapshot:%s\\n' "$*" ;;
esac
`,{mode:0o755});
    writeFileSync(join(directory,'bin/sleep'),'#!/bin/sh\nexit 0\n',{mode:0o755});
    const r=spawnSync(bash,['-c','PATH="$PWD/bin:$PATH"; export PATH; bash run.sh'],{cwd:directory,encoding:'utf8',timeout:15000,
      env:{...process.env,ANDROID_SERIAL:serial,FAKE_GRADLE_EXIT:String(gradleExit),FAKE_BUILD_EXIT:String(buildExit),FAKE_DIAGNOSTIC_EXIT:String(diagnosticExit),FAKE_SCENARIO:scenario}});
    assert.ifError(r.error);
    const captures=join(directory,'.artifacts/android-ui');
    return {status:r.status,stderr:r.stderr,calls:existsSync(join(directory,'calls.txt'))?readFileSync(join(directory,'calls.txt'),'utf8').trim().split('\n'):[],
      artifacts:existsSync(captures)?Object.fromEntries(readdirSync(captures).map(n=>[n,readFileSync(join(captures,n),'utf8')])):{}};
  } finally { assert.equal(dirname(directory),scratch); rmSync(directory,{recursive:true,force:true}); }
}
test('build precedes stable emulator preflight and instrumentation runs once',()=>{
 const r=runCollector({gradleExit:7}); assert.equal(r.status,7);
 const build=r.calls.findIndex(c=>c.includes(':app:assembleDevDebugAndroidTest'));
 const health=r.calls.findIndex(c=>c.includes('shell dumpsys window'));
 const instrument=r.calls.findIndex(c=>c.includes(':app:connectedDevDebugAndroidTest'));
 assert.ok(build>=0 && health>build && instrument>health);
 assert.equal(r.calls.filter(c=>c.includes(':app:connectedDevDebugAndroidTest')).length,1);
 assert.ok(r.calls.slice(0,instrument).filter(c=>c.includes('shell dumpsys window')).length>=3);
 assert.ok(!r.calls.some(c=>c.includes('force-stop')));
 assert.match(r.artifacts['logcat.txt'],/logcat -d -v threadtime/);
 assert.match(r.artifacts['app-cache.tar'],/run-as com.hienthai.fastowin.dev/);
});
test('captured Pixel Launcher ANR is saved before one recovery; test result stays intact',()=>{
 const r=runCollector({scenario:'recovered',gradleExit:7}); assert.equal(r.status,7);
 assert.equal(r.calls.filter(c=>c.includes(`shell am force-stop ${launcher}`)).length,1);
 assert.equal(r.calls.filter(c=>c.includes(':app:connectedDevDebugAndroidTest')).length,1);
 assert.equal(r.artifacts['preflight-launcher-anr-window.txt'],capturedFocus);
 assert.ok(r.calls.findIndex(c=>c.includes('force-stop'))>r.calls.findIndex(c=>c.includes('logcat -d')));
 assert.ok(r.calls.some(c=>c.includes(`-n ${launcher}/.NexusLauncherActivity`)));
});
for(const scenario of ['persistent','recovery-failed','other-anr','misleading-anr','wrong-home','no-focus','adb-failed','capture-logcat-failed','capture-screenshot-failed']) {
 test(`unhealthy ${scenario} emulator stops before instrumentation without broad recovery`,()=>{
  const r=runCollector({scenario}); assert.equal(r.status,70); assert.match(r.stderr,/emulator_unhealthy/);
  assert.ok(!r.calls.some(c=>c.includes(':app:connectedDevDebugAndroidTest')));
  assert.equal(r.calls.filter(c=>c.includes('force-stop')).length,['persistent','recovery-failed'].includes(scenario)?1:0);
  assert.ok(!r.calls.some(c=>/pm clear|disable|keyevent/.test(c)));
  // A disconnected ADB can only leave an empty capture plus its warning.
  assert.ok(Object.hasOwn(r.artifacts,'window.txt'));
 });
}
test('APK build failure keeps its code and captures evidence without recovery or tests',()=>{
 const r=runCollector({buildExit:11}); assert.equal(r.status,11);
 assert.ok(!r.calls.some(c=>c.includes(':app:connectedDevDebugAndroidTest')||c.includes('force-stop'))); assert.ok(r.artifacts['logcat.txt']);
});
for(const gradleExit of [0,7]) test(`failed post-test diagnostics preserve instrumentation code ${gradleExit}`,()=>{
 const r=runCollector({gradleExit,diagnosticExit:9}); assert.equal(r.status,gradleExit);
 assert.equal(r.calls.filter(c=>c.includes(':app:connectedDevDebugAndroidTest')).length,1); assert.match(r.stderr,/diagnostic/);
});
for(const serial of ['', 'RFGYC1RJC3T']) test(`rejects non-emulator ${serial||'(empty)'} before commands`,()=>{
 const r=runCollector({serial}); assert.equal(r.status,64); assert.deepEqual(r.calls,[]);
});
