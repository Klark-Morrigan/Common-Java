#!/usr/bin/env bats
#
# Tests for _hold-window.sh.
#
# The import that holds a double-clicked window open on exit,
# through Common-Automation's helper when there is one.
#
# COMMON_AUTOMATION_ROOT points each case at a throwaway folder in BATS_TEST_TMPDIR.
# Where a case needs the helper, a stub stands in for it and prints a marker instead of pausing.
#
# Run with: bats scripts/imports/_hold-window.bats

setup() {

    unset common_automation_root
    export COMMON_AUTOMATION_ROOT="${BATS_TEST_TMPDIR}/Common-Automation"

    IMPORT="${BATS_TEST_DIRNAME}/_hold-window.sh"
}

# Writes a stand-in for Common-Automation's helper that prints <marker> when the window would be held.
create_hold_window_stub() {

    local marker="$1"

    mkdir -p "${COMMON_AUTOMATION_ROOT}/scripts"
    cat > "${COMMON_AUTOMATION_ROOT}/scripts/_hold-window.sh" <<STUB
hold_window_open() {
    echo "${marker}"
}
STUB
}

@test "holds the window through Common-Automation's helper on exit" {

    # ARRANGE
    expectedOutput='window held'
    create_hold_window_stub "${expectedOutput}"

    # ACT
    run bash -c 'set -euo pipefail; source "$1"; exit 0' _ "${IMPORT}"

    # ASSERT
    [ "${status}" -eq 0 ]
    [ "${output}" = "${expectedOutput}" ]
}

@test "sources cleanly without Common-Automation" {

    # ARRANGE

    # ACT
    run bash -c 'set -euo pipefail; source "$1"' _ "${IMPORT}"

    # ASSERT
    [ "${status}" -eq 0 ]
    [ -z "${output}" ]
}

@test "sets no exit trap without Common-Automation" {

    # ARRANGE

    # ACT
    run bash -c 'set -euo pipefail; source "$1"; trap -p EXIT' _ "${IMPORT}"

    # ASSERT
    [ "${status}" -eq 0 ]
    [ -z "${output}" ]
}
