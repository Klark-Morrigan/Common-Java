#!/usr/bin/env bash
# Java/Gradle repos need everything the base Common-Automation runner heals
# (tracked *.sh and .githooks/ scripts) plus the gradlew wrappers, which carry
# no .sh extension and so fall outside the shared default set. This runner adds
# only that Java-specific extra and otherwise defers entirely to the base
# runner - the single source of the shared pathspec set, the target-repo
# indirection, and the keep-window-open behaviour.
#
# A consuming Java repo exports COMMON_JAVA_TARGET_REPO so this heals THAT repo
# instead of Common-Java; it is translated to the base runner's
# COMMON_AUTOMATION_TARGET_REPO here. Both Common-Java and Common-Automation are
# expected as sibling checkouts under the same parent directory.

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
common_java_root="$(cd "${script_dir}/.." && pwd)"
common_automation_root="$(cd "${common_java_root}/../Common-Automation" && pwd)"
target_repo="${COMMON_JAVA_TARGET_REPO:-${common_java_root}}"

COMMON_AUTOMATION_TARGET_REPO="${target_repo}" \
    exec "${common_automation_root}/scripts/fix-permissions.sh" 'gradlew' '*/gradlew'
