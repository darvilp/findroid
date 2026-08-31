#!/usr/bin/env bash
set -euo pipefail

fail() { echo "Findroid TV release build failed: $*" >&2; exit 1; }

[[ $# -eq 1 ]] || fail "usage: $0 OUTPUT_DIRECTORY"
output_dir=$1
[[ -n "$output_dir" ]] || fail "output directory must not be empty"

required_signing=(
    FINDROID_TV_KEYSTORE_FILE FINDROID_TV_KEYSTORE_PASSWORD
    FINDROID_TV_KEY_ALIAS FINDROID_TV_KEY_PASSWORD
)
missing=()
for variable in "${required_signing[@]}"; do
    [[ -n "${!variable:-}" ]] || missing+=("$variable")
done
((${#missing[@]} == 0)) || fail "required signing environment variables are missing: ${missing[*]}"
[[ -f "$FINDROID_TV_KEYSTORE_FILE" ]] || fail "FINDROID_TV_KEYSTORE_FILE does not name a file"

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
gradlew=${FINDROID_TV_GRADLEW:-$repo_root/gradlew}
[[ -x "$gradlew" ]] || fail "Gradle wrapper is not executable: $gradlew"
mkdir -p "$output_dir"
output_dir=$(cd "$output_dir" && pwd)
apk_dir="$repo_root/app/tv/build/outputs/apk/libre/release"
stage=$(mktemp -d "$output_dir/.findroid-tv-release.XXXXXX")
trap 'rm -rf -- "$stage"' EXIT

"$gradlew" --console=plain :app:tv:clean :app:tv:assembleLibreRelease
for abi in armeabi-v7a arm64-v8a x86 x86_64; do
    source_apk="$apk_dir/tv-libre-$abi-release.apk"
    [[ -f "$source_apk" ]] || fail "optimized APK is missing for $abi"
    cp -- "$source_apk" "$stage/findroid-tv-1.1.0-atv.1-$abi.apk"
done

"$gradlew" --console=plain :app:tv:clean -PfindroidTvUniversalApk=true :app:tv:assembleLibreRelease
[[ -f "$apk_dir/tv-libre-release.apk" ]] || fail "universal APK is missing"
cp -- "$apk_dir/tv-libre-release.apk" "$stage/findroid-tv-1.1.0-atv.1-universal.apk"

for artifact in "$stage"/*.apk; do
    mv -f -- "$artifact" "$output_dir/${artifact##*/}"
done
echo "Findroid TV release APKs written to $output_dir"
