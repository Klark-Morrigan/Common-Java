#!/usr/bin/env bash
# Publishes this repo's GitHub Actions release tags - an immutable vX.Y.Z plus a
# floating major vX - on the tip of origin/master, so consumers can pin this
# repo's reusable workflows by tag. Thin shim to Common-Automation's
# publish-version-tags.sh, which holds the engine. The engine works on the
# current git repo's origin/master rather than on COMMON_AUTOMATION_TARGET_REPO,
# so this cds into this repo first and forwards the version argument ("$@"; the
# engine prompts when it is omitted).
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/imports/_common-automation-root.sh
source "${script_dir}/imports/_common-automation-root.sh"

cd "${script_dir}/.."
exec "${common_automation_root}/scripts/publish-version-tags.sh" "$@"
