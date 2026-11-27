#!/usr/bin/env bats
#
# Tests for executable-paths.sh -
# the JVM tier's +x paths, declared once and
# read by every JVM repo's pre-commit hook and by fix-permissions.sh.
#
# Run with: bats .github/lib/executable-paths.bats

setup() {

    unset EXECUTABLE_PATHSPECS
    DECLARATIONS="${BATS_TEST_DIRNAME}/executable-paths.sh"
}

@test "the pathspecs name each executable at the root and nested" {

    # ARRANGE
    expectedPathspecs='gradlew */gradlew'

    # ACT
    source "${DECLARATIONS}"

    # ASSERT
    [ "${EXECUTABLE_PATHSPECS[*]}" = "${expectedPathspecs}" ]
}

@test "the pathspecs widen a set already declared rather than replace it" {

    # ARRANGE
    EXECUTABLE_PATHSPECS=('*.sh')
    expectedPathspecs='*.sh gradlew */gradlew'

    # ACT
    source "${DECLARATIONS}"

    # ASSERT
    [ "${EXECUTABLE_PATHSPECS[*]}" = "${expectedPathspecs}" ]
}

@test "sourcing leaves no working variables behind" {

    # ARRANGE
    unset executable_file_name

    # ACT
    source "${DECLARATIONS}"

    # ASSERT
    [ -z "${executable_file_name+set}" ]
}
