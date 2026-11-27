#!/usr/bin/env bats
#
# Tests for _require-common-automation-file.sh.
#
# The check every shim makes before it hands work to Common-Automation.
# COMMON_AUTOMATION_ROOT points each case at a throwaway folder in BATS_TEST_TMPDIR,
# which holds only the files a case puts there.
#
# Run with: bats scripts/imports/_require-common-automation-file.bats

setup() {

    unset common_automation_root
    export COMMON_AUTOMATION_ROOT="${BATS_TEST_TMPDIR}/Common-Automation"

    IMPORT="${BATS_TEST_DIRNAME}/_require-common-automation-file.sh"
    REQUIRED_PATH='scripts/engine.sh'
}

@test "passes silently when the required file is there" {

    # ARRANGE
    mkdir -p "${COMMON_AUTOMATION_ROOT}/scripts"
    touch "${COMMON_AUTOMATION_ROOT}/${REQUIRED_PATH}"

    # ACT
    run bash -c 'set -euo pipefail; source "$1"; require_common_automation_file "$2"' _ "${IMPORT}" \
        "${REQUIRED_PATH}"

    # ASSERT
    [ "${status}" -eq 0 ]
    [ -z "${output}" ]
}

@test "fails when the required file is missing" {

    # ARRANGE

    # ACT
    run bash -c 'set -euo pipefail; source "$1"; require_common_automation_file "$2"' _ "${IMPORT}" \
        "${REQUIRED_PATH}"

    # ASSERT
    [ "${status}" -eq 1 ]
}

@test "names the missing file" {

    # ARRANGE
    expectedError="Common-Automation not found: ${COMMON_AUTOMATION_ROOT}/${REQUIRED_PATH} is missing."

    # ACT
    run bash -c 'set -euo pipefail; source "$1"; require_common_automation_file "$2"' _ "${IMPORT}" \
        "${REQUIRED_PATH}"

    # ASSERT
    [ "${status}" -eq 1 ]
    [[ "${output}" == *"${expectedError}"* ]]
}

@test "says how to point at Common-Automation" {

    # ARRANGE
    expectedHint='or set COMMON_AUTOMATION_ROOT to its folder.'

    # ACT
    run bash -c 'set -euo pipefail; source "$1"; require_common_automation_file "$2"' _ "${IMPORT}" \
        "${REQUIRED_PATH}"

    # ASSERT
    [ "${status}" -eq 1 ]
    [[ "${output}" == *"${expectedHint}"* ]]
}

@test "reports in one line" {

    # ARRANGE
    expectedLineCount=1

    # ACT
    run bash -c 'set -euo pipefail; source "$1"; require_common_automation_file "$2"' _ "${IMPORT}" \
        "${REQUIRED_PATH}"

    # ASSERT
    [ "${status}" -eq 1 ]
    [ "${#lines[@]}" -eq "${expectedLineCount}" ]
}

# A caller without `set -e` would carry on past a check that only returned.
@test "stops a caller that runs without set -e" {

    # ARRANGE
    unexpectedLine='carried on'

    # ACT
    run bash -c 'source "$1"; require_common_automation_file "$2"; echo "carried on"' _ "${IMPORT}" \
        "${REQUIRED_PATH}"

    # ASSERT
    [ "${status}" -eq 1 ]
    [[ "${output}" != *"${unexpectedLine}"* ]]
}
