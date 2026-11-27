#!/usr/bin/env bash
#
# Runs the bash-test half of this repo's CI locally: every *.bats suite
# (reports 0 tests when none). Thin shim to Common-Automation's
# _run-tests-bash.sh. The lint half is run-lint-yaml-and-bash.sh;
# run-ci-yaml-and-bash.sh runs both.

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# shellcheck source=scripts/imports/_require-common-automation-file.sh
source "${script_dir}/imports/_require-common-automation-file.sh"

# shellcheck source=scripts/imports/_consumer-repo.sh
source "${script_dir}/imports/_consumer-repo.sh"

require_common_automation_file scripts/_run-tests-bash.sh

exec "${common_automation_root}/scripts/_run-tests-bash.sh"
