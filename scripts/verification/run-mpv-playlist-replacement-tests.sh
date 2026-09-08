#!/usr/bin/env bash
set -euo pipefail

# Build first with :player:local:assembleDebugAndroidTest. Windows ADB can then run
# this native-player regression suite against a Windows emulator from WSL.
if [[ $# != 2 || "$2" != emulator-* ]]; then
    echo "Usage: $0 <adb executable> <emulator serial>" >&2
    exit 2
fi
adb_client=$1
serial=$2
repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
apk="$repo_root/player/local/build/outputs/apk/androidTest/debug/local-debug-androidTest.apk"
[[ -f "$apk" ]] || { echo "Build :player:local:assembleDebugAndroidTest first" >&2; exit 1; }
[[ $("$adb_client" -s "$serial" shell getprop sys.boot_completed | tr -d '\r\n') == 1 ]] || {
    echo "Emulator has not finished booting" >&2
    exit 1
}
if [[ "$adb_client" == *.exe ]]; then
    apk=$(wslpath -w "$apk")
fi
"$adb_client" -s "$serial" install -r "$apk"
test_log=$(mktemp)
trap 'rm -f -- "$test_log"' EXIT
"$adb_client" -s "$serial" shell am instrument -w -r \
    -e class dev.jdtech.jellyfin.player.local.mpv.MPVPlayerReplacementTest \
    dev.jdtech.jellyfin.player.local.test/androidx.test.runner.AndroidJUnitRunner | tee "$test_log"
# am instrument may return shell success even when a test or the process fails.
grep -Eq '^OK \([1-9][0-9]* tests?\)' "$test_log"
! grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed' "$test_log"
