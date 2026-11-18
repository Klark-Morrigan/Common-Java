#!/usr/bin/env bats
# Tests for run-ci-gradle.sh - the local mirror of _ci-gradle.yml. Each case
# runs a copy of the script from a throwaway Common-Java tree in
# BATS_TEST_TMPDIR, so the self build reaches a stub gradlew under its own
# ci-smoke/ rather than the real Gradle project. No Common-Automation sits
# beside that tree, so the hold-window pause is never armed.
# Run with: bats scripts/run-ci-gradle.bats

readonly CI_FLAGS='--no-daemon --console=plain --warning-mode all'

setup() {
    unset CONSUMER_REPO_PATH
    export COMMON_AUTOMATION_ROOT="${BATS_TEST_TMPDIR}/absent-common-automation"

    FAKE_COMMON_JAVA="${BATS_TEST_TMPDIR}/Common-Java"
    mkdir -p "${FAKE_COMMON_JAVA}/scripts/imports" "${FAKE_COMMON_JAVA}/ci-smoke"
    cp "${BATS_TEST_DIRNAME}/run-ci-gradle.sh" "${FAKE_COMMON_JAVA}/scripts/"
    cp "${BATS_TEST_DIRNAME}"/imports/*.sh "${FAKE_COMMON_JAVA}/scripts/imports/"
    SCRIPT="${FAKE_COMMON_JAVA}/scripts/run-ci-gradle.sh"

    CONSUMER="${BATS_TEST_TMPDIR}/consumer"
    mkdir -p "${CONSUMER}"
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

@test "a consumer run builds the consumer's gradlew with the given tasks and CI's flags" {
    create_gradlew_stub "${CONSUMER}"
    CONSUMER_REPO_PATH="${CONSUMER}" run bash "${SCRIPT}" coverage jar
    [ "${status}" -eq 0 ]
    [[ "${output}" == *"gradlew ran in ${CONSUMER} with: coverage jar ${CI_FLAGS}"* ]]
    [[ "${output}" == *"Gradle build passed."* ]]
}

@test "a consumer run with no tasks fails before building" {
    create_gradlew_stub "${CONSUMER}"
    CONSUMER_REPO_PATH="${CONSUMER}" run bash "${SCRIPT}"
    [ "${status}" -eq 2 ]
    [[ "${output}" == *"No Gradle tasks given for ${CONSUMER}"* ]]
    [[ "${output}" != *"gradlew ran"* ]]
}

@test "a consumer without gradlew fails naming the directory" {
    CONSUMER_REPO_PATH="${CONSUMER}" run bash "${SCRIPT}" build
    [ "${status}" -eq 1 ]
    [[ "${output}" == *"No gradlew in ${CONSUMER}"* ]]
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
    create_gradlew_stub "${CONSUMER}" 3
    CONSUMER_REPO_PATH="${CONSUMER}" run bash "${SCRIPT}" build
    [ "${status}" -eq 3 ]
    [[ "${output}" != *"Gradle build passed."* ]]
}
