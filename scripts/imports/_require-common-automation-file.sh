#!/usr/bin/env bash
#
# Stops a script that needs a file from Common-Automation when that file is missing.
#
# Without the check, the script's exec or source fails with bash's bare "No such file or directory" and status 127,
# which names neither the missing repo nor how to point at it.
#
# shellcheck source=scripts/imports/_common-automation-root.sh
source "${BASH_SOURCE[0]%/*}/_common-automation-root.sh"

# Exits rather than returns, so a caller running without `set -e` still stops here.
require_common_automation_file() {

    local required_file="${common_automation_root}/$1"

    if [[ ! -f "${required_file}" ]]; then
        printf '%s %s\n' "Common-Automation not found: ${required_file} is missing." \
            "Clone it beside Common-Java, or set COMMON_AUTOMATION_ROOT to its folder." >&2
        exit 1
    fi
}
