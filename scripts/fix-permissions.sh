#!/usr/bin/env bash
# JVM repos need everything the base Common-Automation runner heals (tracked
# *.sh and .githooks/ scripts) plus this tier's own executables. This runner
# adds only those and otherwise defers entirely to the base runner - the shared
# pathspec set and the keep-window-open behaviour.
#
# A consuming JVM repo exports COMMON_JAVA_TARGET_REPO so this heals THAT repo
# instead of Common-Java.

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/imports/_common-automation-root.sh
source "${script_dir}/imports/_common-automation-root.sh"
# shellcheck source=scripts/imports/_target-repo.sh
source "${script_dir}/imports/_target-repo.sh"
# shellcheck source=.github/lib/executable-paths.sh
source "${common_java_root}/.github/lib/executable-paths.sh"

exec "${common_automation_root}/scripts/fix-permissions.sh" "${EXECUTABLE_PATHSPECS[@]}"
