#!/usr/bin/env bash
# shellcheck disable=SC2034 # every variable here is read by the scripts that source this
# The paths a JVM repo must keep executable, declared once for every reader in
# the tier: each repo's pre-commit hook and scripts/fix-permissions.sh.
#
# Why a file of its own. The Gradle wrapper carries no extension, so the
# family-wide *.sh rule never reaches it, and a commit authored on Windows
# lands it mode 0644. Every JVM repo has one, so the rule belongs to the tier;
# written into each hook, it drifts the first time one copy is edited.
#
# Sourced only. Executing it on its own does nothing and is not an error.

# Executables matched by file name at any depth. None carries an extension to
# be recognised by.
EXECUTABLE_FILE_NAMES=('gradlew')

# The base fix runner already heals *.sh and .githooks/ scripts and takes
# extra git pathspecs on top. A bare pathspec matches at the root only, so each
# name also gets a nested form.
EXECUTABLE_PATHSPECS=()
for executable_file_name in "${EXECUTABLE_FILE_NAMES[@]}"; do
    EXECUTABLE_PATHSPECS+=("${executable_file_name}" "*/${executable_file_name}")
done

# The pre-commit engine takes one ERE that replaces its *.sh default, so this
# form restates the family-wide part. Dotfiles under .githooks/ stay out, as
# the base fix runner keeps them out: git hooks never start with a dot.
executable_name_alternation="$(IFS='|'; printf '%s' "${EXECUTABLE_FILE_NAMES[*]}")"
EXECUTABLE_PATHSPEC_REGEX="(\.sh$|^\.githooks/[^.]|(^|/)(${executable_name_alternation})$)"

unset executable_file_name executable_name_alternation
