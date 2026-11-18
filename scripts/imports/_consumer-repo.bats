#!/usr/bin/env bats
#
# Tests for _consumer-repo.sh.
#
# The import that names the repo a shim works on,
# under the variable Common-Automation's runners read.
#
# Each case sources it in a throwaway shell
# and reads the result back from a child process,
# which sees only what the import exported.
#
# Run with: bats scripts/imports/_consumer-repo.bats

setup() {

    unset CONSUMER_REPO_PATH

    IMPORT="${BATS_TEST_DIRNAME}/_consumer-repo.sh"
    COMMON_JAVA_ROOT="$(cd "${BATS_TEST_DIRNAME}/../.." && pwd)"
}

@test "exports Common-Java itself when no consumer is named" {

    run bash -c 'set -euo pipefail; source "$1"; printenv CONSUMER_REPO_PATH' _ "${IMPORT}"

    [ "${status}" -eq 0 ]
    [ "${output}" = "${COMMON_JAVA_ROOT}" ]
}

@test "keeps the consumer CONSUMER_REPO_PATH names" {

    CONSUMER_REPO_PATH=/work/consumer \
        run bash -c 'set -euo pipefail; source "$1"; printenv CONSUMER_REPO_PATH' _ "${IMPORT}"

    [ "${status}" -eq 0 ]
    [ "${output}" = "/work/consumer" ]
}

@test "sets common_java_root to the repo the import sits in, whatever the consumer" {

    CONSUMER_REPO_PATH=/work/consumer \
        run bash -c 'set -euo pipefail; source "$1"; printf "%s" "${common_java_root}"' _ "${IMPORT}"

    [ "${status}" -eq 0 ]
    [ "${output}" = "${COMMON_JAVA_ROOT}" ]
}
