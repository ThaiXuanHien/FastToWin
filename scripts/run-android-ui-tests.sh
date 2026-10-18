#!/usr/bin/env bash
set -u

case "${ANDROID_SERIAL:-}" in
  emulator-*) ;;
  *) echo 'Android UI diagnostics require an explicit emulator ANDROID_SERIAL.' >&2; exit 64 ;;
esac

# The emulator-runner action terminates its emulator as soon as this script exits.
# Keep the original test result while collecting evidence before that teardown.
./gradlew :app:connectedDevDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true --no-daemon --no-configuration-cache
test_status=$?

capture_directory=.artifacts/android-ui
mkdir -p "$capture_directory"

capture() {
  local destination=$1
  shift
  if ! adb -s "$ANDROID_SERIAL" "$@" > "$capture_directory/$destination"; then
    echo "Android UI diagnostic capture failed: $destination" >&2
  fi
}

capture logcat.txt logcat -d -v threadtime
capture window.txt shell dumpsys window
capture input-method.txt shell dumpsys input_method
capture activity.txt shell dumpsys activity top
capture emulator.png exec-out screencap -p
capture app-cache.tar exec-out run-as com.hienthai.fastowin.dev tar -C cache -cf - .

exit "$test_status"