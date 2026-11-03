#!/usr/bin/env bash
set -u

case "${ANDROID_SERIAL:-}" in
  emulator-*) ;;
  *) echo 'Android UI diagnostics require an explicit emulator ANDROID_SERIAL.' >&2; exit 64 ;;
esac

capture_directory=.artifacts/android-ui
mkdir -p "$capture_directory"

device() {
  # Bound transport calls too: a lost emulator must not hang the health gate.
  timeout --kill-after=2s 15s adb -s "$ANDROID_SERIAL" "$@"
}

capture() {
  local destination=$1
  shift
  if ! device "$@" > "$capture_directory/$destination"; then
    echo "Android UI diagnostic capture failed: $destination" >&2
    return 1
  fi
  return 0
}

capture_final() {
  capture logcat.txt logcat -d -v threadtime
  capture window.txt shell dumpsys window
  capture input-method.txt shell dumpsys input_method
  capture activity.txt shell dumpsys activity top
  capture emulator.png exec-out screencap -p
  capture app-cache.tar exec-out run-as com.hienthai.fastowin.dev tar -C cache -cf - .
}

emulator_preflight() {
  local launcher windows focus boot stable=0 recovered=0 attempt
  launcher=$(device shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME) || return 1
  launcher=$(printf '%s\n' "$launcher" | tr -d '\r' | tail -n 1)
  # No arbitrary package restart, even when some other launcher/system UI fails.
  case "$launcher" in
    com.google.android.apps.nexuslauncher/.NexusLauncherActivity|com.google.android.apps.nexuslauncher/com.google.android.apps.nexuslauncher.NexusLauncherActivity) ;;
    *) return 1 ;;
  esac
  for ((attempt=0; attempt<20; attempt++)); do
    windows=$(device shell dumpsys window) || return 1
    printf '%s\n' "$windows" > "$capture_directory/preflight-window.txt" || return 1
    focus=$(printf '%s\n' "$windows" | tr -d '\r' | grep -m 1 '^[[:space:]]*mCurrentFocus=')
    if [[ "$focus" == *'Application Not Responding:'* ]]; then
      if [[ "$focus" != *' Application Not Responding: com.google.android.apps.nexuslauncher}' || "$recovered" == 1 ]]; then
        return 1
      fi
      # Preserve the observed window plus diagnostics BEFORE the only recovery.
      printf '%s\n' "$windows" > "$capture_directory/preflight-launcher-anr-window.txt" || return 1
      capture preflight-launcher-anr-logcat.txt logcat -d -v threadtime || return 1
      capture preflight-launcher-anr.png exec-out screencap -p || return 1
      recovered=1
      stable=0
      device shell am force-stop com.google.android.apps.nexuslauncher || return 1
      device shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n "$launcher" > "$capture_directory/preflight-launcher-start.txt" || return 1
    elif [[ "$focus" == *' com.google.android.apps.nexuslauncher/com.google.android.apps.nexuslauncher.NexusLauncherActivity}' || "$focus" == *' com.google.android.apps.nexuslauncher/.NexusLauncherActivity}' ]]; then
      boot=$(device shell getprop sys.boot_completed) || return 1
      if [[ "${boot//$'\r'/}" == 1 ]]; then
        stable=$((stable + 1))
        if ((stable >= 3)); then return 0; fi
      else
        stable=0
      fi
    else
      stable=0
    fi
    sleep 1
  done
  return 1
}

# Warm build first, so compiler/download load is not between preflight and tests.
./gradlew :app:assembleDevDebug :app:assembleDevDebugAndroidTest --no-daemon --no-configuration-cache
build_status=$?
if ((build_status != 0)); then
  capture_final
  exit "$build_status"
fi
if ! emulator_preflight; then
  echo 'emulator_unhealthy: Android UI instrumentation was not started.' >&2
  capture_final
  exit 70
fi

# The action tears the emulator down when this exits. Never retry instrumentation
# or replace its exit code with a diagnostic/recovery result.
./gradlew :app:connectedDevDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true --no-daemon --no-configuration-cache
test_status=$?
capture_final

exit "$test_status"
