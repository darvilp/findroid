#!/usr/bin/env bash
set -euo pipefail

fail() { echo "Findroid TV release notes verification failed: $*" >&2; exit 1; }

[[ $# -eq 2 ]] || fail "usage: $0 RELEASE_NOTES CERTIFICATE_FINGERPRINT_FILE"
notes=$1
fingerprint_file=$2
[[ -f "$notes" ]] || fail "release notes do not exist: $notes"
[[ -f "$fingerprint_file" ]] || fail "verified certificate fingerprint does not exist: $fingerprint_file"

mapfile -t declarations < <(
    # The dollar sign is a regex anchor, not a shell expansion.
    # shellcheck disable=SC2016
    sed -n -E 's/^Verified release certificate SHA-256: `([^`]*)`$/\1/p' "$notes"
)
((${#declarations[@]} == 1)) || fail "release notes must contain exactly one certificate fingerprint declaration"

declared=${declarations[0]}
actual=$(tr -d '\r\n' <"$fingerprint_file")
normalize() {
    printf '%s' "$1" | tr '[:upper:]' '[:lower:]' | tr -d ':'
}
declared_normalized=$(normalize "$declared")
actual_normalized=$(normalize "$actual")
[[ "$declared_normalized" =~ ^[0-9a-f]{64}$ ]] || fail "release notes certificate fingerprint is not a SHA-256 digest"
[[ "$actual_normalized" =~ ^[0-9a-f]{64}$ ]] || fail "verified artifact certificate fingerprint is not a SHA-256 digest"
[[ "$declared_normalized" == "$actual_normalized" ]] ||
    fail "release notes certificate fingerprint does not match verified artifacts"

echo "Findroid TV release notes certificate matches verified artifacts"
