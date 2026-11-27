#!/usr/bin/env bats
#
# Tests for fix-whitespace.sh.
#
# The shim that hands Common-Automation's whitespace trim this tier's text types.
# COMMON_AUTOMATION_ROOT points each case at a throwaway folder in BATS_TEST_TMPDIR,
# where a stub engine reports what it was given instead of touching any repo.
#
# Run with: bats scripts/fix-whitespace.bats

setup() {

    unset CONSUMER_REPO_PATH common_automation_root
    export COMMON_AUTOMATION_ROOT="${BATS_TEST_TMPDIR}/Common-Automation"

    SCRIPT="${BATS_TEST_DIRNAME}/fix-whitespace.sh"
    COMMON_JAVA_ROOT="$(cd "${BATS_TEST_DIRNAME}/.." && pwd)"
}

# Writes a stand-in for Common-Automation's engine that prints its arguments and the repo it would trim.
create_engine_stub() {

    mkdir -p "${COMMON_AUTOMATION_ROOT}/scripts"
    cat > "${COMMON_AUTOMATION_ROOT}/scripts/fix-whitespace.sh" <<'STUB'
#!/usr/bin/env bash
echo "engine ran with: $*"
echo "engine trimmed: ${CONSUMER_REPO_PATH}"
STUB
    chmod +x "${COMMON_AUTOMATION_ROOT}/scripts/fix-whitespace.sh"
}

@test "hands the engine this tier's trimmed file types" {

    # ARRANGE
    create_engine_stub
    expectedLine="engine ran with: ${COMMON_JAVA_ROOT}/.github/lib/trimmed-file-types.sh"

    # ACT
    run bash "${SCRIPT}"

    # ASSERT
    [ "${status}" -eq 0 ]
    [[ "${output}" == *"${expectedLine}"* ]]
}

@test "trims Common-Java itself when no consumer is named" {

    # ARRANGE
    create_engine_stub
    expectedLine="engine trimmed: ${COMMON_JAVA_ROOT}"

    # ACT
    run bash "${SCRIPT}"

    # ASSERT
    [ "${status}" -eq 0 ]
    [[ "${output}" == *"${expectedLine}"* ]]
}

@test "trims the consumer CONSUMER_REPO_PATH names" {

    # ARRANGE
    create_engine_stub
    expectedLine='engine trimmed: /work/consumer'

    # ACT
    CONSUMER_REPO_PATH=/work/consumer run bash "${SCRIPT}"

    # ASSERT
    [ "${status}" -eq 0 ]
    [[ "${output}" == *"${expectedLine}"* ]]
}

@test "fails naming the missing engine when Common-Automation is absent" {

    # ARRANGE
    expectedError="Common-Automation not found: ${COMMON_AUTOMATION_ROOT}/scripts/fix-whitespace.sh is missing."

    # ACT
    run bash "${SCRIPT}"

    # ASSERT
    [ "${status}" -eq 1 ]
    [[ "${output}" == *"${expectedError}"* ]]
}
