#!/usr/bin/env bats
#
# Tests for fix-permissions.sh.
#
# The shim that hands Common-Automation's fix engine this tier's executables.
# COMMON_AUTOMATION_ROOT points each case at a throwaway folder in BATS_TEST_TMPDIR,
# where a stub engine reports what it was given instead of touching any repo.
#
# Run with: bats scripts/fix-permissions.bats

setup() {

    unset CONSUMER_REPO_PATH common_automation_root
    export COMMON_AUTOMATION_ROOT="${BATS_TEST_TMPDIR}/Common-Automation"

    SCRIPT="${BATS_TEST_DIRNAME}/fix-permissions.sh"
    COMMON_JAVA_ROOT="$(cd "${BATS_TEST_DIRNAME}/.." && pwd)"
}

# Writes a stand-in for Common-Automation's engine that prints its arguments and the repo it would fix.
create_engine_stub() {

    mkdir -p "${COMMON_AUTOMATION_ROOT}/scripts"
    cat > "${COMMON_AUTOMATION_ROOT}/scripts/fix-permissions.sh" <<'STUB'
#!/usr/bin/env bash
echo "engine ran with: $*"
echo "engine fixed: ${CONSUMER_REPO_PATH}"
STUB
    chmod +x "${COMMON_AUTOMATION_ROOT}/scripts/fix-permissions.sh"
}

@test "hands the engine this tier's executable paths" {

    # ARRANGE
    create_engine_stub
    expectedLine="engine ran with: ${COMMON_JAVA_ROOT}/.github/lib/executable-paths.sh"

    # ACT
    run bash "${SCRIPT}"

    # ASSERT
    [ "${status}" -eq 0 ]
    [[ "${output}" == *"${expectedLine}"* ]]
}

@test "fixes Common-Java itself when no consumer is named" {

    # ARRANGE
    create_engine_stub
    expectedLine="engine fixed: ${COMMON_JAVA_ROOT}"

    # ACT
    run bash "${SCRIPT}"

    # ASSERT
    [ "${status}" -eq 0 ]
    [[ "${output}" == *"${expectedLine}"* ]]
}

@test "fixes the consumer CONSUMER_REPO_PATH names" {

    # ARRANGE
    create_engine_stub
    expectedLine='engine fixed: /work/consumer'

    # ACT
    CONSUMER_REPO_PATH=/work/consumer run bash "${SCRIPT}"

    # ASSERT
    [ "${status}" -eq 0 ]
    [[ "${output}" == *"${expectedLine}"* ]]
}

@test "fails naming the missing engine when Common-Automation is absent" {

    # ARRANGE
    expectedError="Common-Automation not found: ${COMMON_AUTOMATION_ROOT}/scripts/fix-permissions.sh is missing."

    # ACT
    run bash "${SCRIPT}"

    # ASSERT
    [ "${status}" -eq 1 ]
    [[ "${output}" == *"${expectedError}"* ]]
}
