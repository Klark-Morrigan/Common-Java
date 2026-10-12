#!/usr/bin/env bash
# Local equivalent of this repo's ci-yaml.yml + ci-bash.yml workflows:
# runs the full lint suite and the bats tests, then reports a combined
# pass/fail. Thin shim to Common-Automation's run-ci-yaml-and-bash.sh
# orchestrator. To run a single half, see run-lint-yaml-and-bash.sh /
# run-tests-bash.sh.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/imports/_common-automation-root.sh
source "${script_dir}/imports/_common-automation-root.sh"
# shellcheck source=scripts/imports/_target-repo.sh
source "${script_dir}/imports/_target-repo.sh"

exec "${common_automation_root}/scripts/run-ci-yaml-and-bash.sh"
