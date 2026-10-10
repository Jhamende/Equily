#!/usr/bin/env bash
set -euo pipefail
if [[ $# != 2 || "$1" != emulator-* || "$2" != --wipe-test-app ]]; then
    echo "Usage: bash tools/verify-layouts.sh emulator-5554 --wipe-test-app" >&2
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
original_size="$(adb -s "$serial" shell wm size | tr -d '\r' | sed -n 's/Override size: //p')"
original_density="$(adb -s "$serial" shell wm density | tr -d '\r' | sed -n 's/Override density: //p')"
original_font="$(adb -s "$serial" shell settings get system font_scale | tr -d '\r')"
cleanup() {
    adb -s "$serial" shell am force-stop be.equily.app.test >/dev/null 2>&1 || true
    adb -s "$serial" shell am force-stop be.equily.app >/dev/null 2>&1 || true
    adb -s "$serial" shell wm size "${original_size:-reset}"
    adb -s "$serial" shell wm density "${original_density:-reset}"
    if [[ "$original_font" == null ]]; then adb -s "$serial" shell settings delete system font_scale
    else adb -s "$serial" shell settings put system font_scale "$original_font"; fi
}
trap cleanup EXIT
mkdir -p "$root/artifacts/verification"
run_layout() {
    local variant="$1" size="$2" font="$3"
    local destination="$root/artifacts/visual/$variant"
    local log="$root/artifacts/verification/layout-$variant-$(date -u +%Y%m%dT%H%M%SZ).txt"
    adb -s "$serial" shell wm size "$size"
    adb -s "$serial" shell wm density 160
    adb -s "$serial" shell settings put system font_scale "$font"
    timeout 900 adb -s "$serial" shell am instrument -w -r -e class be.equily.app.VisualLayoutTest be.equily.app.test/androidx.test.runner.AndroidJUnitRunner | tee "$log"
    grep -q '^OK (1 test)' "$log"
    if grep -Eq 'FAILURES!!!|INSTRUMENTATION_STATUS_CODE: -(2|3|4)' "$log"; then
        echo "A failed or skipped layout test prevents acceptance." >&2
        exit 1
    fi
    mkdir -p "$destination"
    for language in fr nl en; do
        for theme in light dark; do
            adb -s "$serial" exec-out run-as be.equily.app cat "cache/camera/equily-visual-$language-$theme.png" > "$destination/$language-$theme.png"
            [[ -s "$destination/$language-$theme.png" ]]
        done
    done
    echo "Verified $variant ($size, font scale $font), six language/theme captures: $destination"
}
run_layout phone 320x640 1.0
run_layout tablet 1280x800 1.0
run_layout large-font 320x640 1.3
