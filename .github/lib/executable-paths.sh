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
# Sourced only, through Common-Automation's source_tier_declarations, which
# loads the family-wide set (*.sh and git hooks) first, so this widens it
# rather than standing in for it. Executing it on its own does nothing and is
# not an error.

# Executables matched by file name at any depth. None carries an extension to
# be recognised by.
EXECUTABLE_FILE_NAMES=('gradlew')

# A bare pathspec matches at the root only, so each name also gets a nested
# form.
for executable_file_name in "${EXECUTABLE_FILE_NAMES[@]}"; do
    EXECUTABLE_PATHSPECS+=("${executable_file_name}" "*/${executable_file_name}")
done

unset executable_file_name
