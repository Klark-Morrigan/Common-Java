#!/usr/bin/env bats
#
# Tests for trimmed-file-types.sh -
# the JVM tier's text types for the
# whitespace trim, read by every JVM repo's pre-commit hook and by
# fix-whitespace.sh.
#
# Run with: bats .github/lib/trimmed-file-types.bats

setup() {

    unset WHITESPACE_TRIMMED_TYPES
    DECLARATIONS="${BATS_TEST_DIRNAME}/trimmed-file-types.sh"
}

@test "the trimmed types name Gradle scripts" {

    # ARRANGE
    expectedTypes='*.gradle'

    # ACT
    source "${DECLARATIONS}"

    # ASSERT
    [ "${WHITESPACE_TRIMMED_TYPES[*]}" = "${expectedTypes}" ]
}

@test "the trimmed types widen a set already declared rather than replace it" {

    # ARRANGE
    WHITESPACE_TRIMMED_TYPES=('*.md')
    expectedTypes='*.md *.gradle'

    # ACT
    source "${DECLARATIONS}"

    # ASSERT
    [ "${WHITESPACE_TRIMMED_TYPES[*]}" = "${expectedTypes}" ]
}

@test "executing the file on its own does nothing and is not an error" {

    # ARRANGE

    # ACT
    run bash "${DECLARATIONS}"

    # ASSERT
    [ "${status}" -eq 0 ]
    [ -z "${output}" ]
}
