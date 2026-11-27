#!/usr/bin/env bash
#
# One-time per-clone setup: wires this repo's .githooks/ into git (via
# core.hooksPath) so the pre-commit hook fires on commit. Without it,
# .git/hooks/ stays empty and the hook does nothing.
#
# Thin shim to Common-Automation's setup-hooks engine - the same single-source
# reuse as the other scripts/ shims.

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# shellcheck source=scripts/imports/_require-common-automation-file.sh
source "${script_dir}/imports/_require-common-automation-file.sh"

# shellcheck source=scripts/imports/_consumer-repo.sh
source "${script_dir}/imports/_consumer-repo.sh"

require_common_automation_file scripts/setup-hooks.sh

exec "${common_automation_root}/scripts/setup-hooks.sh"
