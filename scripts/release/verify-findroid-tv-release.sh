#!/usr/bin/env bash
set -euo pipefail

fail() { echo "Findroid TV release verification failed: $*" >&2; exit 1; }
[[ $# -eq 1 ]] || fail "usage: $0 ARTIFACT_DIRECTORY"
artifact_dir=$1
[[ -d "$artifact_dir" ]] || fail "artifact directory does not exist: $artifact_dir"
artifact_dir=$(cd "$artifact_dir" && pwd)

sdk_root=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
[[ -n "$sdk_root" ]] || fail "ANDROID_HOME or ANDROID_SDK_ROOT is required"
build_tools="$sdk_root/build-tools"
[[ -d "$build_tools" ]] || fail "Android SDK build-tools directory does not exist: $build_tools"
tool_dir=$(find "$build_tools" -mindepth 1 -maxdepth 1 -type d -print | sort -V | tail -n 1)
[[ -n "$tool_dir" ]] || fail "no Android SDK build-tools installation was found"
for tool in aapt2 apksigner zipalign; do
    [[ -x "$tool_dir/$tool" ]] || fail "$tool was not found in $tool_dir"
done
command -v unzip >/dev/null 2>&1 || fail "unzip is required"

prefix=findroid-tv-1.1.0-atv.1
abis=(armeabi-v7a arm64-v8a x86 x86_64)
artifacts=()
for suffix in "${abis[@]}" universal; do
    apk="$artifact_dir/$prefix-$suffix.apk"
    [[ -f "$apk" ]] || fail "expected artifact is missing: ${apk##*/}"
    artifacts+=("$apk")
done

common_fingerprint=
for apk in "${artifacts[@]}"; do
    badging=$("$tool_dir/aapt2" dump badging "$apk")
    [[ "$badging" == *"package: name='dev.jdtech.jellyfin.atv' versionCode='33001' versionName='1.1.0-atv.1'"* ]] ||
        fail "package or version mismatch in ${apk##*/}"
    signer=$("$tool_dir/apksigner" verify --verbose --print-certs "$apk") || fail "signature verification failed for ${apk##*/}"
    fingerprint=$(printf '%s\n' "$signer" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -n 1)
    [[ -n "$fingerprint" ]] || fail "certificate fingerprint missing from ${apk##*/}"
    if [[ -z "$common_fingerprint" ]]; then common_fingerprint=$fingerprint
    elif [[ "$fingerprint" != "$common_fingerprint" ]]; then fail "certificate fingerprint mismatch in ${apk##*/}"
    fi
    "$tool_dir/zipalign" -c -P 16 4 "$apk" >/dev/null || fail "alignment check failed for ${apk##*/}"
done

apk_abis() {
    unzip -Z1 "$1" | sed -n 's#^lib/\([^/]*\)/.*#\1#p' | sort -u
}
for abi in "${abis[@]}"; do
    actual=$(apk_abis "$artifact_dir/$prefix-$abi.apk")
    [[ "$actual" == "$abi" ]] || fail "ABI set mismatch in $prefix-$abi.apk: ${actual:-none}"
done
actual=$(apk_abis "$artifact_dir/$prefix-universal.apk")
expected=$(printf '%s\n' "${abis[@]}" | sort)
[[ "$actual" == "$expected" ]] || fail "ABI set mismatch in $prefix-universal.apk: ${actual:-none}"

(
    cd "$artifact_dir"
    names=()
    for apk in "${artifacts[@]}"; do names+=("${apk##*/}"); done
    LC_ALL=C printf '%s\n' "${names[@]}" | sort | xargs sha256sum >SHA256SUMS
)
echo "Findroid TV release verification passed; certificate SHA-256: $common_fingerprint"
