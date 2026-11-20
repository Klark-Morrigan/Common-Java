#!/usr/bin/env bats
# Tests for executable-paths.sh - the JVM tier's +x paths, declared once and
# read by every JVM repo's pre-commit hook and by fix-permissions.sh.
# Run with: bats .github/lib/executable-paths.bats

setup() {

    source "${BATS_TEST_DIRNAME}/executable-paths.sh"
}

@test "the pathspecs name each executable at the root and nested" {

    [ "${EXECUTABLE_PATHSPECS[*]}" = "gradlew */gradlew" ]
}

@test "the pathspecs widen a set already declared rather than replace it" {

    EXECUTABLE_PATHSPECS=('*.sh')

    source "${BATS_TEST_DIRNAME}/executable-paths.sh"

    [ "${EXECUTABLE_PATHSPECS[*]}" = "*.sh gradlew */gradlew" ]
}

@test "sourcing leaves no working variables behind" {

    [ -z "${executable_file_name+set}" ]
}
