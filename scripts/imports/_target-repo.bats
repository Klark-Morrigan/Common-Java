#!/usr/bin/env bats
# Tests for _target-repo.sh - the import that names the repo a shim works on,
# under the variable Common-Automation's runners read. Each case sources it in a
# throwaway shell and reads the result back from a child process, which sees
# only what the import exported.
# Run with: bats scripts/imports/_target-repo.bats

setup() {
    unset COMMON_JAVA_TARGET_REPO COMMON_AUTOMATION_TARGET_REPO

    IMPORT="${BATS_TEST_DIRNAME}/_target-repo.sh"
    COMMON_JAVA_ROOT="$(cd "${BATS_TEST_DIRNAME}/../.." && pwd)"
}

@test "exports Common-Java itself as the target when no consumer is named" {
    run bash -c 'set -euo pipefail; source "$1"; printenv COMMON_AUTOMATION_TARGET_REPO' _ "${IMPORT}"
    [ "${status}" -eq 0 ]
    [ "${output}" = "${COMMON_JAVA_ROOT}" ]
}

@test "exports the consumer named by COMMON_JAVA_TARGET_REPO as the target" {
    COMMON_JAVA_TARGET_REPO=/work/consumer \
        run bash -c 'set -euo pipefail; source "$1"; printenv COMMON_AUTOMATION_TARGET_REPO' _ "${IMPORT}"
    [ "${status}" -eq 0 ]
    [ "${output}" = "/work/consumer" ]
}

@test "sets common_java_root to the repo the import sits in, whatever the target" {
    COMMON_JAVA_TARGET_REPO=/work/consumer \
        run bash -c 'set -euo pipefail; source "$1"; printf "%s" "${common_java_root}"' _ "${IMPORT}"
    [ "${status}" -eq 0 ]
    [ "${output}" = "${COMMON_JAVA_ROOT}" ]
}
