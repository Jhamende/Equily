#!/usr/bin/env bash
set -euo pipefail
if [[ $# != 2 || "$1" != emulator-* || "$2" != --wipe-test-app ]]; then
    echo "Usage: bash tools/verify-clean-restore.sh emulator-5554 --wipe-test-app" >&2
    echo "Deletes Equily data only on the dedicated equily-api26 / equily-clean-restore test AVD." >&2
    exit 2
fi
serial="$1"
root="$(cd "$(dirname "$0")/.." && pwd)"
avd="$(adb -s "$serial" emu avd name | head -1 | tr -d '\r')"
if [[ "$avd" != equily-api26 && "$avd" != equily-clean-restore ]]; then
    echo "Refusing to wipe an AVD that is not reserved for Equily tests." >&2
    exit 2
fi
debug="$root/app/build/outputs/apk/debug/app-debug.apk"
tests="$root/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
[[ -f "$debug" && -f "$tests" ]]
umask 077
temporary="$(mktemp -d)"
remote="/data/local/tmp/equily-clean-restore-$(basename "$temporary").equily"
cleanup() { adb -s "$serial" shell rm -f "$remote" >/dev/null 2>&1 || true; rm -rf "$temporary"; }
trap cleanup EXIT
runner="be.equily.app.test/androidx.test.runner.AndroidJUnitRunner"
test_class="be.equily.app.CleanInstallationBackupTest"
run_phase() {
    timeout 600 adb -s "$serial" shell am instrument -w -r -e class "$test_class" -e equilyBackupPhase "$1" "$runner" | tee "$temporary/$1.txt"
    grep -q 'OK (1 test)' "$temporary/$1.txt"
    if grep -Eq 'FAILURES!!!|INSTRUMENTATION_STATUS_CODE: -(2|3|4)' "$temporary/$1.txt"; then
        echo "A failed or skipped backup phase prevents acceptance." >&2
        exit 1
    fi
}
echo "Wiping only be.equily.app on dedicated test AVD $avd ($serial)."
if adb -s "$serial" shell pm path be.equily.app | grep -q 'package:'; then adb -s "$serial" uninstall be.equily.app; fi
adb -s "$serial" install --no-streaming -t "$debug"
adb -s "$serial" install --no-streaming -r -t "$tests"
run_phase export
adb -s "$serial" exec-out run-as be.equily.app cat cache/camera/clean-install.equily > "$temporary/backup.equily"
[[ -s "$temporary/backup.equily" ]]
adb -s "$serial" uninstall be.equily.app
adb -s "$serial" install --no-streaming -t "$debug"
adb -s "$serial" push "$temporary/backup.equily" "$remote"
adb -s "$serial" shell "run-as be.equily.app sh -c 'mkdir -p cache/camera; cat > cache/camera/clean-install.equily' < '$remote'"
run_phase restore
echo "Verified: encrypted backup retained outside app, uninstall, clean install, restore and database reopen."
