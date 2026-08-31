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
    export FAKE_APK_OUTPUT="$REPO_ROOT/app/tv/build/outputs/apk/libre/release"
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

@test "promotion failure rolls back every managed artifact" {
    make_fake_gradle
    run "$REPO_ROOT/scripts/release/build-findroid-tv-release.sh" "$TEST_ROOT/out"
    [ "$status" -eq 0 ]
    cp "$TEST_ROOT/out/findroid-tv-1.1.0-atv.1-arm64-v8a.apk" "$TEST_ROOT/old"
    cat >"$TEST_ROOT/bin/mv" <<'EOF'
#!/usr/bin/env bash
for arg in "$@"; do
    if [[ "$arg" == *'.findroid-tv-release.'*'/findroid-tv-'* && "$arg" != *'/backup/'* ]]; then exit 9; fi
done
exec /usr/bin/mv "$@"
EOF
    chmod +x "$TEST_ROOT/bin/mv"
    run "$REPO_ROOT/scripts/release/build-findroid-tv-release.sh" "$TEST_ROOT/out"
    [ "$status" -ne 0 ]
    cmp "$TEST_ROOT/old" "$TEST_ROOT/out/findroid-tv-1.1.0-atv.1-arm64-v8a.apk"
    [ "$(find "$TEST_ROOT/out" -maxdepth 1 -name '*.apk' | wc -l)" -eq 5 ]
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
    sed -i '/echo/i [[ "${@: -1}" == *x86.apk ]] \&\& echo "Signer #1 certificate SHA-256 digest: EE:FF" \&\& exit 0' "$ANDROID_HOME/build-tools/37.0.0/apksigner"
    run "$REPO_ROOT/scripts/release/verify-findroid-tv-release.sh" "$TEST_ROOT/out"
    [ "$status" -ne 0 ]
    [[ "$output" == *"certificate fingerprint mismatch"* ]]
}

@test "verification rejects an APK with an additional signer" {
    make_fake_tools
    sed -i '/echo/a echo "Signer #2 certificate SHA-256 digest: 11:22"' "$ANDROID_HOME/build-tools/37.0.0/apksigner"
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
