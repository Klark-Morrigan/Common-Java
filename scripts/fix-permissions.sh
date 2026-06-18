#!/usr/bin/env bash
# Re-stages +x on tracked files that must be executable in this repo and
# would otherwise land mode 0644 when authored on Windows: every *.sh (the
# family-wide rule), the gradlew wrappers, and the .githooks/ scripts (the
# pre-commit hook must be executable to fire on a fresh Linux clone). The
# shared .sh-only engine and the CI gate cover only *.sh, so this repo widens
# the set - gradlew has no .sh extension and the hooks live outside any
# scanned glob.
#
# Reuses Common-Automation's fix engine for the actual detection and fix
# (git index mode 100644 -> git update-index --chmod=+x) so this repo cannot
# drift from the canonical +x rule; it only passes a wider pathspec set. The
# engine is .sh-specific only in its no-arg / gate modes - given explicit
# pathspecs it checks exactly those, regardless of extension. Common-Automation
# is expected as a sibling checkout under the same parent directory.

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"
common_automation_root="$(cd "${repo_root}/../Common-Automation" && pwd)"

# shellcheck source=/dev/null
source "${common_automation_root}/scripts/_hold-window.sh"
trap hold_window_open EXIT

# shellcheck source=/dev/null
source "${common_automation_root}/.github/lib/fix-sh-executable.sh"

echo "=== fixing +x on tracked .sh, gradlew, and .githooks files in ${repo_root} ==="
fixed="$(cd "${repo_root}" && fix_sh_executable '*.sh' 'gradlew' '*/gradlew' '.githooks/*')"
if [[ -n "${fixed}" ]]; then
    echo "${fixed}"
    echo "Done. Review staged mode changes with: git status"
else
    echo "Nothing to fix - all tracked .sh, gradlew, and .githooks files already have +x."
fi
