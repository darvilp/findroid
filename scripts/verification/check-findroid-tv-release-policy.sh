#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$repo_root"

unset FINDROID_TV_KEYSTORE_FILE
unset FINDROID_TV_KEYSTORE_PASSWORD
unset FINDROID_TV_KEY_ALIAS
unset FINDROID_TV_KEY_PASSWORD

fail() {
    echo "Findroid TV release policy check failed: $*" >&2
    exit 1
}

find_aapt2() {
    if command -v aapt2 >/dev/null 2>&1; then
        command -v aapt2
        return
    fi

    local sdk_root=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
    [[ -n "$sdk_root" ]] || fail "ANDROID_HOME or ANDROID_SDK_ROOT is required to locate aapt2"

    local candidate
    candidate=$(find "$sdk_root/build-tools" -mindepth 2 -maxdepth 2 -type f -name aapt2 | sort -V | tail -n 1)
    [[ -n "$candidate" ]] || fail "aapt2 was not found under $sdk_root/build-tools"
    echo "$candidate"
}

assert_contains() {
    local actual=$1
    local expected=$2
    local description=$3
    [[ "$actual" == *"$expected"* ]] || fail "$description (expected '$expected' in '$actual')"
}

assert_release_signing_required() {
    local task=$1
    local output
    if output=$(./gradlew --console=plain "$task" 2>&1); then
        fail "$task succeeded without release signing credentials"
    fi
    assert_contains "$output" \
        "Findroid TV release signing requires environment variables: FINDROID_TV_KEYSTORE_FILE, FINDROID_TV_KEYSTORE_PASSWORD, FINDROID_TV_KEY_ALIAS, FINDROID_TV_KEY_PASSWORD" \
        "$task did not report the missing release signing credentials"
}

aapt2=$(find_aapt2)
apk_output_dir=app/tv/build/outputs/apk/libre/debug

./gradlew --console=plain :app:tv:lintLibreRelease

./gradlew --console=plain :app:tv:clean :app:tv:assembleLibreDebug

mapfile -t split_apks < <(find "$apk_output_dir" -maxdepth 1 -type f -name '*.apk' -printf '%f\n' | sort)
expected_split_apks=(
    tv-libre-arm64-v8a-debug.apk
    tv-libre-armeabi-v7a-debug.apk
    tv-libre-x86-debug.apk
    tv-libre-x86_64-debug.apk
)
[[ "${split_apks[*]}" == "${expected_split_apks[*]}" ]] ||
    fail "default APK outputs were '${split_apks[*]}'"

debug_badging=$("$aapt2" dump badging "$apk_output_dir/tv-libre-arm64-v8a-debug.apk")
assert_contains "$debug_badging" "package: name='dev.jdtech.jellyfin.debug' versionCode='33' versionName='1.1.0'" \
    "debug APK identity or version is wrong"
assert_contains "$debug_badging" "application-label:'Findroid Debug'" "debug APK label is wrong"

./gradlew --console=plain :app:tv:clean -PfindroidTvUniversalApk=true :app:tv:assembleLibreDebug

mapfile -t universal_apks < <(find "$apk_output_dir" -maxdepth 1 -type f -name '*.apk' -printf '%f\n' | sort)
[[ "${universal_apks[*]}" == "tv-libre-debug.apk" ]] ||
    fail "universal APK outputs were '${universal_apks[*]}'"

./gradlew --console=plain \
    :app:tv:processApplicationManifestLibreReleaseForBundle \
    :app:tv:generateLibreReleaseResValues

bundle_manifest=app/tv/build/intermediates/bundle_manifest/libreRelease/processApplicationManifestLibreReleaseForBundle/AndroidManifest.xml
release_values=app/tv/build/generated/res/resValues/libre/release/values/gradleResValues.xml
[[ -f "$bundle_manifest" ]] || fail "release bundle manifest was not generated"
[[ -f "$release_values" ]] || fail "release resource values were not generated"

bundle_metadata=$(<"$bundle_manifest")
assert_contains "$bundle_metadata" 'package="dev.jdtech.jellyfin.atv"' "release bundle application ID is wrong"
assert_contains "$bundle_metadata" 'android:versionCode="33001"' "release bundle version code is wrong"
assert_contains "$bundle_metadata" 'android:versionName="1.1.0-atv.1"' "release bundle version name is wrong"

release_resources=$(<"$release_values")
assert_contains "$release_resources" '>Findroid TV</string>' "release label is wrong"

assert_release_signing_required :app:tv:assembleLibreRelease
assert_release_signing_required :app:tv:bundleLibreRelease

echo "FINDROID_TV_RELEASE_POLICY_CHECK_PASS"
