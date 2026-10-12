#!/usr/bin/env bats
# Tests for run-ci-gradle.sh - the local mirror of _ci-gradle.yml. Each case
# runs a copy of the script from a throwaway Common-Java tree in
# BATS_TEST_TMPDIR, so the self build reaches a stub gradlew under its own
# ci-smoke/ rather than the real Gradle project. No Common-Automation sits
# beside that tree, so the hold-window pause is never armed.
# Run with: bats scripts/run-ci-gradle.bats

readonly CI_FLAGS='--no-daemon --console=plain --warning-mode all'

setup() {
    unset COMMON_JAVA_TARGET_REPO
    export COMMON_AUTOMATION_ROOT="${BATS_TEST_TMPDIR}/absent-common-automation"

    FAKE_COMMON_JAVA="${BATS_TEST_TMPDIR}/Common-Java"
    mkdir -p "${FAKE_COMMON_JAVA}/scripts/imports" "${FAKE_COMMON_JAVA}/ci-smoke"
    cp "${BATS_TEST_DIRNAME}/run-ci-gradle.sh" "${FAKE_COMMON_JAVA}/scripts/"
    cp "${BATS_TEST_DIRNAME}"/imports/*.sh "${FAKE_COMMON_JAVA}/scripts/imports/"
    SCRIPT="${FAKE_COMMON_JAVA}/scripts/run-ci-gradle.sh"

    TARGET="${BATS_TEST_TMPDIR}/consumer"
    mkdir -p "${TARGET}"
}

# Writes a gradlew into <dir> that reports where it ran and with what, then
# exits with <exit code> (default 0).
create_gradlew_stub() {

    local dir="$1" exit_code="${2:-0}"

    cat > "${dir}/gradlew" <<STUB
#!/usr/bin/env bash
echo "gradlew ran in \${PWD} with: \$*"
exit ${exit_code}
STUB
    chmod +x "${dir}/gradlew"
}

@test "a target run builds the target's gradlew with the given tasks and CI's flags" {
    create_gradlew_stub "${TARGET}"
    COMMON_JAVA_TARGET_REPO="${TARGET}" run bash "${SCRIPT}" coverage jar
    [ "${status}" -eq 0 ]
    [[ "${output}" == *"gradlew ran in ${TARGET} with: coverage jar ${CI_FLAGS}"* ]]
    [[ "${output}" == *"Gradle build passed."* ]]
}

@test "a target run with no tasks fails before building" {
    create_gradlew_stub "${TARGET}"
    COMMON_JAVA_TARGET_REPO="${TARGET}" run bash "${SCRIPT}"
    [ "${status}" -eq 2 ]
    [[ "${output}" == *"No Gradle tasks given for ${TARGET}"* ]]
    [[ "${output}" != *"gradlew ran"* ]]
}

@test "a target without gradlew fails naming the directory" {
    COMMON_JAVA_TARGET_REPO="${TARGET}" run bash "${SCRIPT}" build
    [ "${status}" -eq 1 ]
    [[ "${output}" == *"No gradlew in ${TARGET}"* ]]
}

@test "a self run builds the ci-smoke project with build by default" {
    create_gradlew_stub "${FAKE_COMMON_JAVA}/ci-smoke"
    run bash "${SCRIPT}"
    [ "${status}" -eq 0 ]
    [[ "${output}" == *"gradlew ran in ${FAKE_COMMON_JAVA}/ci-smoke with: build ${CI_FLAGS}"* ]]
}

@test "a self run passes the given tasks instead of the default" {
    create_gradlew_stub "${FAKE_COMMON_JAVA}/ci-smoke"
    run bash "${SCRIPT}" testGradle
    [ "${status}" -eq 0 ]
    [[ "${output}" == *"with: testGradle ${CI_FLAGS}"* ]]
}

@test "a self run without the ci-smoke gradlew fails naming the directory" {
    run bash "${SCRIPT}"
    [ "${status}" -eq 1 ]
    [[ "${output}" == *"No gradlew in ${FAKE_COMMON_JAVA}/ci-smoke"* ]]
}

@test "a failing build fails the run with Gradle's exit code" {
    create_gradlew_stub "${TARGET}" 3
    COMMON_JAVA_TARGET_REPO="${TARGET}" run bash "${SCRIPT}" build
    [ "${status}" -eq 3 ]
    [[ "${output}" != *"Gradle build passed."* ]]
}
