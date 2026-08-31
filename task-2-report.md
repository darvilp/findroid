# Task 2 release-publishing repair

- Fixed transactional rollback in `build-findroid-tv-release.sh`: only files moved to the backup are restored, and only successfully promoted new artifacts are removed. Untouched prior artifacts and unrelated files are never deleted.
- Added the `FINDROID_TV_APK_OUTPUT_DIR` seam, usable only with the existing test Gradle-wrapper override. Bats fake APKs now remain in `BATS_TEST_TMPDIR`; a hash sentinel proves the pre-existing production-path fixture is unchanged.
- Hardened signing verification to require the authoritative `Number of signers: 1` line and exactly one `Signer #1` SHA-256 fingerprint.
- Removed the exact ignored, task-polluted `app/tv/build/outputs/apk/libre/release/tv-libre-release.apk` only after recording that it was 9 bytes with content `universal` (SHA-256 `a9fd078562420a276b6902ca3d8bf583c0366e38af72e23be99b16035a76b39a`).

Validation completed:

- `bats scripts/release/tests/findroid-tv-release.bats` — 15 passing tests, including first/middle/final backup and promotion failures, unrelated-file preservation, BATS output isolation, and Build Tools 37 signer fixtures.
- `ruby scripts/release/tests/workflow_policy_test.rb` — 2 runs, 30 assertions, all passing.
- `bash -n` and `shellcheck` on release scripts and focused policy script — passing.
- `git diff --check` — passing.

Limit: two disposable PKCS12 build/verify attempts were started with a unique `/tmp/findroid-tv-release-task2.*` directory and removed afterward, but the local execution runner interrupted Gradle after build initialization, before it emitted artifacts. This was an execution-environment limitation, not a release/publishing action; no successful real five-APK transcript is claimed.
