#!/usr/bin/env bash
# Names the repo a shim works on: the consuming repo in COMMON_JAVA_TARGET_REPO,
# else Common-Java itself. Exported as COMMON_AUTOMATION_TARGET_REPO, the name
# Common-Automation's runners read, so a consumer names its repo once whichever
# tier's runner does the work.
#
# Also sets common_java_root, for a shim that hands the base runner one of this
# repo's own files.
common_java_root="$(cd "${BASH_SOURCE[0]%/*}/../.." && pwd)"
export COMMON_AUTOMATION_TARGET_REPO="${COMMON_JAVA_TARGET_REPO:-${common_java_root}}"
