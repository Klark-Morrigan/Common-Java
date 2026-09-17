#!/usr/bin/env bash
# JVM repos have a text type of their own beyond the Markdown the base runner
# trims: Gradle scripts. This runner adds only that, by handing the base runner
# this tier's own declaration of it - the same file every JVM repo's pre-commit
# hook passes - and otherwise defers entirely to the base runner: the target-
# repo indirection, the shared engine, and the keep-window-open behaviour.
#
# A consuming Java repo exports COMMON_JAVA_TARGET_REPO so this trims THAT repo
# instead of Common-Java; it is translated to the base runner's
# COMMON_AUTOMATION_TARGET_REPO here, exactly as fix-permissions.sh does. Both
# Common-Java and Common-Automation are expected as sibling checkouts under the
# same parent directory.

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
common_java_root="$(cd "${script_dir}/.." && pwd)"
common_automation_root="$(cd "${common_java_root}/../Common-Automation" && pwd)"
target_repo="${COMMON_JAVA_TARGET_REPO:-${common_java_root}}"

COMMON_AUTOMATION_TARGET_REPO="${target_repo}" \
    exec "${common_automation_root}/scripts/fix-whitespace.sh" \
    "${common_java_root}/.github/lib/trimmed-file-types.sh"
