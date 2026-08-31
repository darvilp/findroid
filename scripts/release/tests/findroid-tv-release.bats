#!/usr/bin/env bats

setup() {
    export TEST_ROOT="$BATS_TEST_TMPDIR/case"
    mkdir -p "$TEST_ROOT/bin" "$TEST_ROOT/sdk/build-tools/37.0.0" "$TEST_ROOT/out"
    export ANDROID_HOME="$TEST_ROOT/sdk"
    export FINDROID_TV_GRADLEW="$TEST_ROOT/bin/gradlew"
    export FINDROID_TV_KEYSTORE_FILE="$TEST_ROOT/key.p12"
    export FINDROID_TV_KEYSTORE_PASSWORD=store-secret
    export FINDROID_TV_KEY_ALIAS=release
    export FINDROID_TV_KEY_PASSWORD=key-secret
    touch "$FINDROID_TV_KEYSTORE_FILE"
    export PATH="$TEST_ROOT/bin:$PATH"
    export REPO_ROOT="$BATS_TEST_DIRNAME/../../.."
    export FINDROID_TV_APK_OUTPUT_DIR="$TEST_ROOT/fake-apk-output"
    export PRODUCTION_APK_OUTPUT="$REPO_ROOT/app/tv/build/outputs/apk/libre/release/tv-libre-release.apk"
    if [[ -e "$PRODUCTION_APK_OUTPUT" ]]; then
        production_apk_sentinel="$(sha256sum "$PRODUCTION_APK_OUTPUT")"
    else
        production_apk_sentinel=absent
    fi
}

assert_production_apk_output_unchanged() {
    if [[ "$production_apk_sentinel" == absent ]]; then
        [ ! -e "$PRODUCTION_APK_OUTPUT" ]
    else
        [ "$(sha256sum "$PRODUCTION_APK_OUTPUT")" = "$production_apk_sentinel" ]
    fi
}

make_fake_gradle() {
    cat >"$FINDROID_TV_GRADLEW" <<'EOF'
#!/usr/bin/env bash
set -eu
output="$FAKE_APK_OUTPUT"
mkdir -p "$output"
case " $* " in
  *" -PfindroidTvUniversalApk=true "*) rm -f "$output"/tv-libre-*-release.apk; printf universal >"$output/tv-libre-release.apk" ;;
  *)
    for abi in armeabi-v7a arm64-v8a x86 x86_64; do
      printf '%s' "$abi" >"$output/tv-libre-$abi-release.apk"
    done
    ;;
esac
EOF
    chmod +x "$FINDROID_TV_GRADLEW"
    export FAKE_APK_OUTPUT="$FINDROID_TV_APK_OUTPUT_DIR"
}

@test "build rejects output beneath the Gradle-cleaned TV build directory" {
    make_fake_gradle
    run "$REPO_ROOT/scripts/release/build-findroid-tv-release.sh" "$REPO_ROOT/app/tv/build/release-output"
    [ "$status" -ne 0 ]
    [[ "$output" == *"must not be inside"* ]]
    ln -s "$REPO_ROOT/app/tv/build" "$TEST_ROOT/build-link"
    run "$REPO_ROOT/scripts/release/build-findroid-tv-release.sh" "$TEST_ROOT/build-link/release-output"
    [ "$status" -ne 0 ]
    [[ "$output" == *"must not be inside"* ]]
}

@test "failed rebuild preserves the complete prior managed set" {
    make_fake_gradle
    run "$REPO_ROOT/scripts/release/build-findroid-tv-release.sh" "$TEST_ROOT/out"
    [ "$status" -eq 0 ]
    cp "$TEST_ROOT/out/findroid-tv-1.1.0-atv.1-arm64-v8a.apk" "$TEST_ROOT/old"
    sed -i '/case/i [[ " $* " == *" -PfindroidTvUniversalApk=true "* ]] \&\& exit 9' "$FINDROID_TV_GRADLEW"
    run "$REPO_ROOT/scripts/release/build-findroid-tv-release.sh" "$TEST_ROOT/out"
    [ "$status" -ne 0 ]
    cmp "$TEST_ROOT/old" "$TEST_ROOT/out/findroid-tv-1.1.0-atv.1-arm64-v8a.apk"
    [ "$(find "$TEST_ROOT/out" -maxdepth 1 -name '*.apk' | wc -l)" -eq 5 ]
}

seed_prior_managed_set() {
    make_fake_gradle
    run "$REPO_ROOT/scripts/release/build-findroid-tv-release.sh" "$TEST_ROOT/out"
    [ "$status" -eq 0 ]
    for abi in armeabi-v7a arm64-v8a x86 x86_64 universal; do
        printf 'old-%s' "$abi" >"$TEST_ROOT/out/findroid-tv-1.1.0-atv.1-$abi.apk"
    done
    printf old-checksums >"$TEST_ROOT/out/SHA256SUMS"
    printf unrelated >"$TEST_ROOT/out/unrelated.txt"
    rm -rf -- "$TEST_ROOT/prior"
    mkdir "$TEST_ROOT/prior"
    cp "$TEST_ROOT/out"/findroid-tv-*.apk "$TEST_ROOT/out/SHA256SUMS" "$TEST_ROOT/prior/"
}

assert_prior_managed_set_and_unrelated_survive() {
    for prior in "$TEST_ROOT/prior"/*; do
        cmp "$prior" "$TEST_ROOT/out/${prior##*/}"
    done
    [ "$(cat "$TEST_ROOT/out/unrelated.txt")" = unrelated ]
    assert_production_apk_output_unchanged
}

make_failing_mv() {
    local kind=$1
    local failure_index=$2
    cat >"$TEST_ROOT/bin/mv" <<'EOF'
#!/usr/bin/env bash
set -eu
source=${@: -2:1}
destination=${@: -1}
if [[ "${FAIL_MV_KIND:-}" == backup && "$destination" == */backup/* ]]; then
    count_file="$TEST_ROOT/backup-mv-count"
elif [[ "${FAIL_MV_KIND:-}" == promote && "$source" == *'/.findroid-tv-release.'* && "$source" != */backup/* ]]; then
    count_file="$TEST_ROOT/promote-mv-count"
else
    exec /usr/bin/mv "$@"
fi
count=0
[[ -f "$count_file" ]] && count=$(<"$count_file")
count=$((count + 1))
printf '%s' "$count" >"$count_file"
[[ "$count" -eq "${FAIL_MV_INDEX:?}" ]] && exit 9
exec /usr/bin/mv "$@"
EOF
    chmod +x "$TEST_ROOT/bin/mv"
    export FAIL_MV_KIND=$kind
    export FAIL_MV_INDEX=$failure_index
}

@test "rollback restores every backed file without deleting untouched old files after first middle and final backup failures" {
    for failure_index in 1 3 6; do
        seed_prior_managed_set
        make_failing_mv backup "$failure_index"
        run "$REPO_ROOT/scripts/release/build-findroid-tv-release.sh" "$TEST_ROOT/out"
        [ "$status" -ne 0 ]
        assert_prior_managed_set_and_unrelated_survive
        rm -f "$TEST_ROOT/backup-mv-count"
        rm -f "$TEST_ROOT/bin/mv"
    done
}

@test "rollback removes only promoted new files and restores all backed files after first middle and final promotion failures" {
    for failure_index in 1 3 5; do
        seed_prior_managed_set
        make_failing_mv promote "$failure_index"
        run "$REPO_ROOT/scripts/release/build-findroid-tv-release.sh" "$TEST_ROOT/out"
        [ "$status" -ne 0 ]
        assert_prior_managed_set_and_unrelated_survive
        rm -f "$TEST_ROOT/promote-mv-count"
        rm -f "$TEST_ROOT/bin/mv"
    done
}

make_fake_tools() {
    local package=${1:-dev.jdtech.jellyfin.atv}
    local version=${2:-1.1.0-atv.1}
    local code=${3:-33001}
    local fingerprint=${4:-AA:BB}
    cat >"$ANDROID_HOME/build-tools/37.0.0/aapt2" <<EOF
#!/usr/bin/env bash
echo "package: name='$package' versionCode='$code' versionName='$version'"
EOF
    cat >"$ANDROID_HOME/build-tools/37.0.0/apksigner" <<EOF
#!/usr/bin/env bash
echo 'Verifies'
echo 'Number of signers: 1'
echo 'Signer #1 certificate SHA-256 digest: $fingerprint'
EOF
    cat >"$ANDROID_HOME/build-tools/37.0.0/zipalign" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
    chmod +x "$ANDROID_HOME/build-tools/37.0.0/"{aapt2,apksigner,zipalign}
    cat >"$TEST_ROOT/bin/unzip" <<'EOF'
#!/usr/bin/env bash
apk=${@: -1}
name=${apk##*/}
case "$name" in
  *universal*) printf '%s\n' lib/armeabi-v7a/x.so lib/arm64-v8a/x.so lib/x86/x.so lib/x86_64/x.so ;;
  *armeabi-v7a*) echo lib/armeabi-v7a/x.so ;;
  *arm64-v8a*) echo lib/arm64-v8a/x.so ;;
  *x86_64*) echo lib/x86_64/x.so ;;
  *x86*) echo lib/x86/x.so ;;
esac
EOF
    chmod +x "$TEST_ROOT/bin/unzip"
}

@test "build rejects missing signing credentials without exposing other values" {
    unset FINDROID_TV_KEY_ALIAS
    run "$REPO_ROOT/scripts/release/build-findroid-tv-release.sh" "$TEST_ROOT/out"
    [ "$status" -ne 0 ]
    [[ "$output" == *FINDROID_TV_KEY_ALIAS* ]]
    [[ "$output" != *store-secret* ]]
}

@test "build preserves four splits, creates exact names, and keeps unrelated output" {
    make_fake_gradle
    echo keep >"$TEST_ROOT/out/unrelated.txt"
    run "$REPO_ROOT/scripts/release/build-findroid-tv-release.sh" "$TEST_ROOT/out"
    [ "$status" -eq 0 ]
    run bash -c "cd '$TEST_ROOT/out' && printf '%s\n' * | sort"
    [ "$output" = $'findroid-tv-1.1.0-atv.1-arm64-v8a.apk\nfindroid-tv-1.1.0-atv.1-armeabi-v7a.apk\nfindroid-tv-1.1.0-atv.1-universal.apk\nfindroid-tv-1.1.0-atv.1-x86.apk\nfindroid-tv-1.1.0-atv.1-x86_64.apk\nunrelated.txt' ]
    [ "$(cat "$TEST_ROOT/out/findroid-tv-1.1.0-atv.1-armeabi-v7a.apk")" = armeabi-v7a ]
}

@test "verification rejects package or version mismatch" {
    make_fake_tools wrong.package
    for abi in armeabi-v7a arm64-v8a x86 x86_64 universal; do touch "$TEST_ROOT/out/findroid-tv-1.1.0-atv.1-$abi.apk"; done
    run "$REPO_ROOT/scripts/release/verify-findroid-tv-release.sh" "$TEST_ROOT/out"
    [ "$status" -ne 0 ]
    [[ "$output" == *"package or version mismatch"* ]]
}

@test "verification rejects an incorrect ABI set" {
    make_fake_tools
    sed -i 's/echo lib\/x86\/x.so/printf "%s\\n" lib\/x86\/x.so lib\/arm64-v8a\/x.so/' "$TEST_ROOT/bin/unzip"
    for abi in armeabi-v7a arm64-v8a x86 x86_64 universal; do touch "$TEST_ROOT/out/findroid-tv-1.1.0-atv.1-$abi.apk"; done
    run "$REPO_ROOT/scripts/release/verify-findroid-tv-release.sh" "$TEST_ROOT/out"
    [ "$status" -ne 0 ]
    [[ "$output" == *"ABI set mismatch"* ]]
}

@test "verification rejects certificate fingerprint mismatch" {
    make_fake_tools
    sed -i 's/AA:BB/CC:DD/' "$ANDROID_HOME/build-tools/37.0.0/apksigner"
    for abi in armeabi-v7a arm64-v8a x86 x86_64 universal; do touch "$TEST_ROOT/out/findroid-tv-1.1.0-atv.1-$abi.apk"; done
    # Give one artifact a distinct signer result.
    sed -i '/echo/i [[ "${@: -1}" == *x86.apk ]] \&\& { echo "Number of signers: 1"; echo "Signer #1 certificate SHA-256 digest: EE:FF"; exit 0; }' "$ANDROID_HOME/build-tools/37.0.0/apksigner"
    run "$REPO_ROOT/scripts/release/verify-findroid-tv-release.sh" "$TEST_ROOT/out"
    [ "$status" -ne 0 ]
    [[ "$output" == *"certificate fingerprint mismatch"* ]]
}

@test "verification rejects an APK with an additional signer" {
    make_fake_tools
    sed -i 's/Number of signers: 1/Number of signers: 2/' "$ANDROID_HOME/build-tools/37.0.0/apksigner"
    sed -i '/Signer #1/a echo "Signer #2 certificate SHA-256 digest: 11:22"' "$ANDROID_HOME/build-tools/37.0.0/apksigner"
    for abi in armeabi-v7a arm64-v8a x86 x86_64 universal; do touch "$TEST_ROOT/out/findroid-tv-1.1.0-atv.1-$abi.apk"; done
    run "$REPO_ROOT/scripts/release/verify-findroid-tv-release.sh" "$TEST_ROOT/out"
    [ "$status" -ne 0 ]
    [[ "$output" == *"exactly one signer"* ]]
}

@test "verification rejects Build Tools 37 output without the authoritative one-signer count" {
    make_fake_tools
    sed -i '/Number of signers/d' "$ANDROID_HOME/build-tools/37.0.0/apksigner"
    for abi in armeabi-v7a arm64-v8a x86 x86_64 universal; do touch "$TEST_ROOT/out/findroid-tv-1.1.0-atv.1-$abi.apk"; done
    run "$REPO_ROOT/scripts/release/verify-findroid-tv-release.sh" "$TEST_ROOT/out"
    [ "$status" -ne 0 ]
    [[ "$output" == *"exactly one signer"* ]]
}

@test "verification propagates signature and alignment failures" {
    make_fake_tools
    for abi in armeabi-v7a arm64-v8a x86 x86_64 universal; do touch "$TEST_ROOT/out/findroid-tv-1.1.0-atv.1-$abi.apk"; done
    sed -i '2i exit 7' "$ANDROID_HOME/build-tools/37.0.0/apksigner"
    run "$REPO_ROOT/scripts/release/verify-findroid-tv-release.sh" "$TEST_ROOT/out"
    [[ "$output" == *"signature verification failed"* ]]
    make_fake_tools
    sed -i '2i exit 8' "$ANDROID_HOME/build-tools/37.0.0/zipalign"
    run "$REPO_ROOT/scripts/release/verify-findroid-tv-release.sh" "$TEST_ROOT/out"
    [[ "$output" == *"alignment check failed"* ]]
}

@test "verification separately rejects wrong version name and code" {
    for abi in armeabi-v7a arm64-v8a x86 x86_64 universal; do touch "$TEST_ROOT/out/findroid-tv-1.1.0-atv.1-$abi.apk"; done
    make_fake_tools dev.jdtech.jellyfin.atv wrong 33001
    run "$REPO_ROOT/scripts/release/verify-findroid-tv-release.sh" "$TEST_ROOT/out"
    [[ "$output" == *"package or version mismatch"* ]]
    make_fake_tools dev.jdtech.jellyfin.atv 1.1.0-atv.1 99
    run "$REPO_ROOT/scripts/release/verify-findroid-tv-release.sh" "$TEST_ROOT/out"
    [[ "$output" == *"package or version mismatch"* ]]
}

@test "failed verification removes a stale checksum manifest" {
    make_fake_tools wrong.package
    for abi in armeabi-v7a arm64-v8a x86 x86_64 universal; do touch "$TEST_ROOT/out/findroid-tv-1.1.0-atv.1-$abi.apk"; done
    echo stale >"$TEST_ROOT/out/SHA256SUMS"
    run "$REPO_ROOT/scripts/release/verify-findroid-tv-release.sh" "$TEST_ROOT/out"
    [ "$status" -ne 0 ]
    [ ! -e "$TEST_ROOT/out/SHA256SUMS" ]
}

@test "verification writes stable artifact-only checksums" {
    make_fake_tools
    for abi in armeabi-v7a arm64-v8a x86 x86_64 universal; do printf '%s' "$abi" >"$TEST_ROOT/out/findroid-tv-1.1.0-atv.1-$abi.apk"; done
    run "$REPO_ROOT/scripts/release/verify-findroid-tv-release.sh" "$TEST_ROOT/out"
    [ "$status" -eq 0 ]
    first=$(cat "$TEST_ROOT/out/SHA256SUMS")
    run "$REPO_ROOT/scripts/release/verify-findroid-tv-release.sh" "$TEST_ROOT/out"
    [ "$status" -eq 0 ]
    [ "$(cat "$TEST_ROOT/out/SHA256SUMS")" = "$first" ]
    [[ "$first" != *"$TEST_ROOT"* ]]
    [ "$(wc -l <"$TEST_ROOT/out/SHA256SUMS")" -eq 5 ]
}
