#!/usr/bin/env bash
set -euo pipefail
if [[ $# != 2 || "$1" != emulator-* || "$2" != --wipe-test-app ]]; then
    echo "Usage: bash tools/verify-device-suite.sh emulator-5554 --wipe-test-app" >&2
    exit 2
fi
serial="$1"
root="$(cd "$(dirname "$0")/.." && pwd)"
avd="$(adb -s "$serial" emu avd name | head -1 | tr -d '\r')"
if [[ "$avd" != equily-api26 && "$avd" != equily-clean-restore ]]; then
    echo "Refusing an AVD that is not reserved for Equily tests." >&2
    exit 2
fi
[[ "$(adb -s "$serial" shell getprop ro.build.version.sdk | tr -d '\r')" == 26 ]]
debug="$root/app/build/outputs/apk/debug/app-debug.apk"
tests="$root/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
[[ -f "$debug" && -f "$tests" ]]
pin_created=false
cleanup() {
    if [[ "$pin_created" == true ]]; then adb -s "$serial" shell locksettings clear --old 246824; fi
}
trap cleanup EXIT
mkdir -p "$root/artifacts/verification"
log="$root/artifacts/verification/android-api26-$(date -u +%Y%m%dT%H%M%SZ).txt"
if adb -s "$serial" shell pm path be.equily.app | grep -q 'package:'; then adb -s "$serial" uninstall be.equily.app; fi
adb -s "$serial" install --no-streaming -t "$debug"
adb -s "$serial" install --no-streaming -r -t "$tests"
pin_result="$(adb -s "$serial" shell locksettings set-pin 246824)"
grep -q 'Pin set to' <<< "$pin_result"
pin_created=true
timeout 3600 adb -s "$serial" shell am instrument -w -r -e equilySyntheticPin 246824 be.equily.app.test/androidx.test.runner.AndroidJUnitRunner | tee "$log"
grep -Eq '^OK \([0-9]+ tests?\)' "$log"
! grep -Eq 'FAILURES!!!|INSTRUMENTATION_STATUS_CODE: -(2|3|4)' "$log"
echo "Full API 26 suite verified, including actual credential and SAF return. Log: $log"
