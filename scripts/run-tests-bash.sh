#!/usr/bin/env bash
# Runs the bash-test half of this repo's CI locally: every *.bats suite
# (reports 0 tests when none). Thin shim to Common-Automation's
# _run-tests-bash.sh. The lint half is run-lint-yaml-and-bash.sh;
# run-ci-yaml-and-bash.sh runs both.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/imports/_common-automation-root.sh
source "${script_dir}/imports/_common-automation-root.sh"
# shellcheck source=scripts/imports/_target-repo.sh
source "${script_dir}/imports/_target-repo.sh"

exec "${common_automation_root}/scripts/_run-tests-bash.sh"
