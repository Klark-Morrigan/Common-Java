#!/usr/bin/env bash

# Local mirror of _ci-gradle.yml: runs a Gradle build/test with the same
# flags the reusable workflow uses, so a green local run means a green CI run.
#
# Target-aware, the JVM counterpart to Common-Automation's
# run-ci-yaml-and-bash.sh:
#   - A consumer repo ships a same-named run-ci-gradle.sh shim that sets
#     COMMON_JAVA_TARGET_REPO and execs this with its tasks; the build runs at
#     that repo's root (its gradlew). The tasks are required: only the
#     consumer knows which ones its own ci-gradle.yml runs.
#   - With no target this runs against Common-Java itself. Common-Java's only
#     Gradle project is the CI smoke project under ci-smoke/, which exercises
#     the shared Gradle scripts - the same build ci-gradle.yml runs - so the
#     self-run builds that (default: build), a local mirror of that CI gate.
#
# A Gradle build is a single gradlew invocation, so - unlike the bash root -
# there are no separate underscored sub-runners to orchestrate: this script
# is both the entry and the engine. It holds no knowledge of any consumer's
# build: whatever a build needs from its machine is the build's concern - if
# missing, the build fails with its own message.

set -euo pipefail

# What ci-gradle.yml runs for the self build.
readonly -a SELF_BUILD_TASKS=(build)

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
common_java_root="$(cd "${script_dir}/.." && pwd)"

# Reuse Common-Automation's hold-window helper so a double-clicked launch
# behaves like every other runner in the family. The imports/ adapter owns
# the cross-repo resolution, the EXIT trap, and a soft-dependency guard (a
# missing Common-Automation degrades to "no pause" rather than failing).
# shellcheck source=scripts/imports/_hold-window.sh
source "${script_dir}/imports/_hold-window.sh"

if [[ -n "${COMMON_JAVA_TARGET_REPO:-}" ]]; then
    # Consumer build: the Gradle project is at the target repo's root.
    build_dir="${COMMON_JAVA_TARGET_REPO}"
    if [[ "$#" -eq 0 ]]; then
        echo "No Gradle tasks given for ${build_dir}; pass the tasks its ci-gradle.yml runs." >&2
        exit 2
    fi
else
    # Self build: Common-Java's only Gradle project is the CI smoke project.
    build_dir="${common_java_root}/ci-smoke"
    if [[ "$#" -eq 0 ]]; then
        set -- "${SELF_BUILD_TASKS[@]}"
    fi
fi

if [[ ! -f "${build_dir}/gradlew" ]]; then
    echo "No gradlew in ${build_dir}; nothing to build." >&2
    exit 1
fi

echo "=== gradlew $* (in ${build_dir}) ==="
cd "${build_dir}"
# --no-daemon --console=plain --warning-mode all matches _ci-gradle.yml so local
# output and process model line up with CI - including which deprecations are
# named, so one is not first seen on a runner.
./gradlew "$@" --no-daemon --console=plain --warning-mode all
echo
echo "Gradle build passed."
