#!/usr/bin/env bash
#
# JVM repos have a text type of their own beyond the Markdown the base runner
# trims: Gradle scripts. This runner adds only that, by handing the base runner
# this tier's own declaration of it - the same file every JVM repo's pre-commit
# hook passes - and otherwise defers entirely to the base runner: the shared
# engine and the keep-window-open behaviour.
#
# A consuming JVM repo exports CONSUMER_REPO_PATH so this trims THAT repo
# instead of Common-Java.

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# shellcheck source=scripts/imports/_require-common-automation-file.sh
source "${script_dir}/imports/_require-common-automation-file.sh"

# shellcheck source=scripts/imports/_consumer-repo.sh
source "${script_dir}/imports/_consumer-repo.sh"

require_common_automation_file scripts/fix-whitespace.sh

exec "${common_automation_root}/scripts/fix-whitespace.sh" \
    "${common_java_root}/.github/lib/trimmed-file-types.sh"
