import test from 'node:test';
import assert from 'node:assert/strict';
import { copyFileSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';

const bash = process.platform === 'win32' ? 'C:/Program Files/Git/bin/bash.exe' : 'bash';
const scratch = resolve('.tmp');
mkdirSync(scratch, { recursive: true });

function runCollector(gradleExit, adbExit, serial = 'emulator-5556') {
  const directory = mkdtempSync(join(scratch, 'android-ci-capture-'));
  try {
    assert.ok(existsSync('scripts/run-android-ui-tests.sh'), 'Android UI diagnostic collector is not implemented');
    copyFileSync('scripts/run-android-ui-tests.sh', join(directory, 'run.sh'));
    mkdirSync(join(directory, 'bin'));
    writeFileSync(join(directory, 'gradlew'), '#!/bin/sh\nprintf "%s\\n" "$@" > gradle-args.txt\nexit "$FAKE_GRADLE_EXIT"\n', { mode: 0o755 });
    writeFileSync(join(directory, 'bin/adb'), '#!/bin/sh\nprintf "%s\\n" "$*" >> adb-calls.txt\nprintf "snapshot:%s\\n" "$*"\nexit "$FAKE_ADB_EXIT"\n', { mode: 0o755 });
    const result = spawnSync(bash, ['-c', 'PATH="$PWD/bin:$PATH"; export PATH; bash run.sh'], {
      cwd: directory,
      encoding: 'utf8',
      env: { ...process.env, ANDROID_SERIAL: serial, FAKE_GRADLE_EXIT: String(gradleExit), FAKE_ADB_EXIT: String(adbExit) },
    });
    assert.ifError(result.error);
    return {
      status: result.status,
      stderr: result.stderr,
      gradleArgs: existsSync(join(directory, 'gradle-args.txt')) ? readFileSync(join(directory, 'gradle-args.txt'), 'utf8') : null,
      adbCalls: existsSync(join(directory, 'adb-calls.txt')) ? readFileSync(join(directory, 'adb-calls.txt'), 'utf8').trim().split('\n') : [],
      // Read captures before removing the fixture.
      artifacts: existsSync(join(directory, '.artifacts/android-ui/logcat.txt')) ? {
        logcat: readFileSync(join(directory, '.artifacts/android-ui/logcat.txt'), 'utf8'),
        cache: readFileSync(join(directory, '.artifacts/android-ui/app-cache.tar'), 'utf8'),
      } : null,
    };
  } finally {
    assert.equal(dirname(directory), scratch);
    rmSync(directory, { recursive: true, force: true });
  }
}

test('failed Android tests preserve their status after collecting device evidence', () => {
  const result = runCollector(7, 0);
  assert.equal(result.status, 7);
  assert.deepEqual(result.gradleArgs.trim().split('\n'), [':app:connectedDevDebugAndroidTest', '-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true', '--no-daemon', '--no-configuration-cache']);
  assert.equal(result.adbCalls.length, 6);
  assert.ok(result.adbCalls.every((call) => call.startsWith('-s emulator-5556 ')));
  assert.match(result.artifacts.logcat, /logcat -d -v threadtime/);
  assert.match(result.artifacts.cache, /exec-out run-as com.hienthai.fastowin.dev tar -C cache -cf - ./);
  assert.ok(result.adbCalls.some((call) => call.endsWith('shell dumpsys window')));
  assert.ok(result.adbCalls.some((call) => call.endsWith('shell dumpsys input_method')));
});

for (const gradleExit of [0, 7]) {
  test(`failed diagnostics do not replace Android test status ${gradleExit}`, () => {
    const result = runCollector(gradleExit, 9);
    assert.equal(result.status, gradleExit);
    assert.equal(result.adbCalls.length, 6);
    assert.match(result.stderr, /diagnostic/);
  });
}

test('collector requires an explicit emulator serial before starting tests or adb', () => {
  const result = runCollector(0, 0, '');
  assert.equal(result.status, 64);
  assert.equal(result.gradleArgs, null);
  assert.deepEqual(result.adbCalls, []);
});