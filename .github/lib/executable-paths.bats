#!/usr/bin/env bats
# Tests for executable-paths.sh - the JVM tier's +x set, declared once and read
# in two forms: the ERE a pre-commit hook passes and the git pathspecs
# fix-permissions.sh passes.
# Run with: bats .github/lib/executable-paths.bats

setup() {

    source "${BATS_TEST_DIRNAME}/executable-paths.sh"
}

@test "the pathspecs name each executable at the root and nested" {

    [ "${EXECUTABLE_PATHSPECS[*]}" = "gradlew */gradlew" ]
}

@test "the regex matches every path that must carry +x" {

    local path

    for path in run.sh scripts/imports/_x.sh gradlew ci-smoke/gradlew a/b/gradlew .githooks/pre-commit; do

        grep -qE "${EXECUTABLE_PATHSPEC_REGEX}" <<< "${path}" || {

            echo "not matched: ${path}"
            return 1
        }
    done
}

@test "the regex leaves every other path alone" {

    local path

    for path in gradlew.bat mygradlew gradlew/settings.gradle README.md run.shx .githooks/.gitignore; do

        if grep -qE "${EXECUTABLE_PATHSPEC_REGEX}" <<< "${path}"; then

            echo "matched: ${path}"
            return 1
        fi
    done
}

@test "sourcing leaves no working variables behind" {

    [ -z "${executable_file_name+set}" ]
    [ -z "${executable_name_alternation+set}" ]
}
