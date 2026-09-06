#!/usr/bin/env bash
set -euo pipefail

fail() { echo "Findroid TV release verification failed: $*" >&2; exit 1; }
write_manifests=false
if [[ ${1:-} == --write-manifests ]]; then
    write_manifests=true
    shift
fi
[[ $# -eq 1 ]] || fail "usage: $0 [--write-manifests] ARTIFACT_DIRECTORY"
artifact_dir=$1
[[ -d "$artifact_dir" ]] || fail "artifact directory does not exist: $artifact_dir"
artifact_dir=$(cd "$artifact_dir" && pwd)
repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
# The path is anchored to the discovered repository root.
# shellcheck disable=SC1091
source "$repo_root/scripts/release/findroid-tv-release.env"

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

prefix="findroid-tv-$FINDROID_TV_VERSION_NAME"
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
    [[ "$badging" == *"package: name='$FINDROID_TV_APPLICATION_ID' versionCode='$FINDROID_TV_VERSION_CODE' versionName='$FINDROID_TV_VERSION_NAME'"* ]] ||
        fail "package or version mismatch in ${apk##*/}"
    signer=$("$tool_dir/apksigner" verify --verbose --print-certs "$apk") || fail "signature verification failed for ${apk##*/}"
    mapfile -t signer_counts < <(printf '%s\n' "$signer" | sed -n -E 's/^Number of signers: ([0-9]+)$/\1/p')
    if ((${#signer_counts[@]} != 1)) || [[ "${signer_counts[0]}" != 1 ]]; then
        fail "exactly one signer is required for ${apk##*/}"
    fi
    mapfile -t signer_fingerprints < <(
        printf '%s\n' "$signer" |
            sed -n -E \
                -e 's/^Signer #1 certificate SHA-256 digest: //p' \
                -e 's/^V2 Signer: certificate SHA-256 digest: //p' |
            sort -u
    )
    ((${#signer_fingerprints[@]} == 1)) || fail "exactly one signer is required for ${apk##*/}"
    fingerprint=${signer_fingerprints[0]}
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

checksums=$(
    cd "$artifact_dir"
    names=()
    for apk in "${artifacts[@]}"; do names+=("${apk##*/}"); done
    LC_ALL=C printf '%s\n' "${names[@]}" | sort | xargs sha256sum
)

if [[ "$write_manifests" == true ]]; then
    (
        cd "$artifact_dir"
        checksum_tmp=$(mktemp .SHA256SUMS.XXXXXX)
        fingerprint_tmp=$(mktemp .CERTIFICATE_SHA256.XXXXXX)
        trap 'rm -f -- "$checksum_tmp" "$fingerprint_tmp"' EXIT
        printf '%s\n' "$checksums" >"$checksum_tmp"
        printf '%s\n' "$common_fingerprint" >"$fingerprint_tmp"
        mv -- "$checksum_tmp" SHA256SUMS
        mv -- "$fingerprint_tmp" CERTIFICATE_SHA256
        trap - EXIT
    )
else
    # Verification of downloaded artifacts must never replace the supplied evidence.
    [[ -f "$artifact_dir/SHA256SUMS" ]] || fail "checksum manifest is missing"
    [[ "$(<"$artifact_dir/SHA256SUMS")" == "$checksums" ]] || fail "checksum manifest mismatch"
    # Draft release assets omit this file; their certificate is recorded in release notes.
    if [[ -e "$artifact_dir/CERTIFICATE_SHA256" ]]; then
        [[ "$(<"$artifact_dir/CERTIFICATE_SHA256")" == "$common_fingerprint" ]] ||
            fail "certificate manifest mismatch"
    fi
fi
echo "Findroid TV release verification passed; certificate SHA-256: $common_fingerprint"
