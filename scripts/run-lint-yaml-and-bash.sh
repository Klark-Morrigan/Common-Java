#!/usr/bin/env bash
# Runs the lint half of this repo's CI locally: shellcheck,
# check-sh-executable, yamllint, actionlint and action-validator (each
# auto-skips when its surface is absent). Thin shim to Common-Automation's
# _run-lint-yaml-and-bash.sh. The bats test half is run-tests-bash.sh;
# run-ci-yaml-and-bash.sh runs both.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/imports/_common-automation-root.sh
source "${script_dir}/imports/_common-automation-root.sh"
# shellcheck source=scripts/imports/_target-repo.sh
source "${script_dir}/imports/_target-repo.sh"

exec "${common_automation_root}/scripts/_run-lint-yaml-and-bash.sh"
