#!/usr/bin/env bats
# Tests for _common-automation-root.sh - the one place this repo's scripts learn
# where Common-Automation lives. Each case sources a copy of the import from a
# throwaway workspace in BATS_TEST_TMPDIR that holds no Common-Automation, under
# `set -euo pipefail` as the scripts that source it run.
# Run with: bats scripts/imports/_common-automation-root.bats

setup() {
    unset COMMON_AUTOMATION_ROOT common_automation_root

    WORKSPACE="${BATS_TEST_TMPDIR}/workspace"
    mkdir -p "${WORKSPACE}/Common-Java/scripts/imports"
    cp "${BATS_TEST_DIRNAME}/_common-automation-root.sh" "${WORKSPACE}/Common-Java/scripts/imports/"
    IMPORT="${WORKSPACE}/Common-Java/scripts/imports/_common-automation-root.sh"
}

@test "defaults to the sibling path even when no sibling is checked out" {
    run bash -c 'set -euo pipefail; source "$1"; printf "%s" "${common_automation_root}"' _ "${IMPORT}"
    [ "${status}" -eq 0 ]
    [ "${output}" = "${WORKSPACE}/Common-Automation" ]
}

@test "COMMON_AUTOMATION_ROOT overrides the sibling default" {
    COMMON_AUTOMATION_ROOT=/elsewhere/Common-Automation \
        run bash -c 'set -euo pipefail; source "$1"; printf "%s" "${common_automation_root}"' _ "${IMPORT}"
    [ "${status}" -eq 0 ]
    [ "${output}" = "/elsewhere/Common-Automation" ]
}

@test "keeps a root the caller already resolved, over the override too" {
    COMMON_AUTOMATION_ROOT=/elsewhere/Common-Automation \
        run bash -c 'set -euo pipefail; common_automation_root=/from-caller; source "$1"
            printf "%s" "${common_automation_root}"' _ "${IMPORT}"
    [ "${status}" -eq 0 ]
    [ "${output}" = "/from-caller" ]
}
