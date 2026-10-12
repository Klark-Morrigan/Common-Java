#!/usr/bin/env bash
# One-time per-clone setup: wires this repo's .githooks/ into git (via
# core.hooksPath) so the pre-commit hook fires on commit. Without it,
# .git/hooks/ stays empty and the hook does nothing.
#
# Thin shim to Common-Automation's setup-hooks engine - the same single-source
# reuse as the other scripts/ shims.

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/imports/_common-automation-root.sh
source "${script_dir}/imports/_common-automation-root.sh"
# shellcheck source=scripts/imports/_target-repo.sh
source "${script_dir}/imports/_target-repo.sh"

exec "${common_automation_root}/scripts/setup-hooks.sh"
