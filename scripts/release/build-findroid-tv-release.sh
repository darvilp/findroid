#!/usr/bin/env bash
set -euo pipefail

fail() { echo "Findroid TV release build failed: $*" >&2; exit 1; }

[[ $# -eq 1 ]] || fail "usage: $0 OUTPUT_DIRECTORY"
output_dir=$1
[[ -n "$output_dir" ]] || fail "output directory must not be empty"

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
# The path is anchored to the discovered repository root.
# shellcheck disable=SC1091
source "$repo_root/scripts/release/findroid-tv-release.env"
artifact_prefix="findroid-tv-$FINDROID_TV_VERSION_NAME"
tv_build_dir=$(realpath -m "$repo_root/app/tv/build")
physical_output=$(realpath -m "$output_dir")
if [[ "$physical_output" == "$tv_build_dir" || "$physical_output" == "$tv_build_dir/"* ]]; then
    fail "output directory must not be inside app/tv/build because Gradle clean deletes it"
fi

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

gradlew=${FINDROID_TV_GRADLEW:-$repo_root/gradlew}
[[ -x "$gradlew" ]] || fail "Gradle wrapper is not executable: $gradlew"
mkdir -p "$output_dir"
output_dir=$(cd "$output_dir" && pwd)
apk_dir="$repo_root/app/tv/build/outputs/apk/libre/release"
stage=$(mktemp -d "$output_dir/.findroid-tv-release.XXXXXX")
backup="$stage/backup"
mkdir "$backup"
promoting=false
rollback() {
    status=$?
    if [[ "$promoting" == true ]]; then
        for managed in "${managed_paths[@]}"; do
            [[ -e "$managed" ]] && rm -f -- "$managed"
        done
        for prior in "$backup"/*; do [[ -e "$prior" ]] && mv -- "$prior" "$output_dir/${prior##*/}"; done
    fi
    rm -rf -- "$stage"
    exit "$status"
}
trap rollback EXIT
managed_paths=()
for abi in armeabi-v7a arm64-v8a x86 x86_64 universal; do
    managed_paths+=("$output_dir/$artifact_prefix-$abi.apk")
done
managed_paths+=("$output_dir/SHA256SUMS")

"$gradlew" --console=plain :app:tv:clean :app:tv:assembleLibreRelease
for abi in armeabi-v7a arm64-v8a x86 x86_64; do
    source_apk="$apk_dir/tv-libre-$abi-release.apk"
    [[ -f "$source_apk" ]] || fail "optimized APK is missing for $abi"
    cp -- "$source_apk" "$stage/$artifact_prefix-$abi.apk"
done

"$gradlew" --console=plain :app:tv:clean -PfindroidTvUniversalApk=true :app:tv:assembleLibreRelease
[[ -f "$apk_dir/tv-libre-release.apk" ]] || fail "universal APK is missing"
cp -- "$apk_dir/tv-libre-release.apk" "$stage/$artifact_prefix-universal.apk"

promoting=true
for managed in "${managed_paths[@]}"; do
    [[ -e "$managed" ]] && mv -- "$managed" "$backup/${managed##*/}"
done
for artifact in "$stage"/*.apk; do
    mv -f -- "$artifact" "$output_dir/${artifact##*/}"
done
promoting=false
echo "Findroid TV release APKs written to $output_dir"
