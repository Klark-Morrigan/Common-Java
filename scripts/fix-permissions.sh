#!/usr/bin/env bash
# JVM repos need everything the base Common-Automation runner heals (tracked
# *.sh and .githooks/ scripts) plus this tier's own executables. This runner
# hands the base runner the tier's declaration file - the same one every JVM
# hook passes - and otherwise defers to it entirely.
#
# A consuming JVM repo exports CONSUMER_REPO_PATH so this heals THAT repo
# instead of Common-Java.

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/imports/_common-automation-root.sh
source "${script_dir}/imports/_common-automation-root.sh"
# shellcheck source=scripts/imports/_consumer-repo.sh
source "${script_dir}/imports/_consumer-repo.sh"

exec "${common_automation_root}/scripts/fix-permissions.sh" \
    "${common_java_root}/.github/lib/executable-paths.sh"
