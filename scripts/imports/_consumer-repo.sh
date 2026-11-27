#!/usr/bin/env bash
#
# Names the repo a shim works on:
# the consumer's CONSUMER_REPO_PATH,
# else Common-Java itself.
#
# Exported, since Common-Automation's runners read the same variable,
# so a consumer names its repo once whichever tier's runner does the work.
#
# Also sets common_java_root,
# for a shim that reaches one of this repo's own files.

common_java_root="$(cd "${BASH_SOURCE[0]%/*}/../.." && pwd)"
export CONSUMER_REPO_PATH="${CONSUMER_REPO_PATH:-${common_java_root}}"
