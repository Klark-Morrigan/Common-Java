#!/usr/bin/env bash

# Local mirror of _ci-gradle.yml: runs a Gradle build/test with the same
# flags the reusable workflow uses, so a green local run means a green CI run.
#
# It builds one of two repos:
#   - A consumer repo ships its own run-ci-gradle.sh that sets
#     CONSUMER_REPO_PATH and runs this with its tasks; the build runs at
#     that repo's root (its gradlew). The tasks are required: only the
#     consumer knows which ones its own ci-gradle.yml runs.
#   - With no consumer this builds Common-Java itself. Common-Java's only
#     Gradle project is the CI smoke project under ci-smoke/, which exercises
#     the shared Gradle scripts - the same build ci-gradle.yml runs - so the
#     self build runs that (default: build).
#
# Unlike the other scripts here, this one does the work itself rather than
# handing it to Common-Automation: a Gradle build is one gradlew call, so
# there is nothing to share. It knows nothing of any consumer's build:
# whatever a build needs from its machine is the build's concern - if
# missing, the build fails with its own message.

set -euo pipefail

# What ci-gradle.yml runs for the self build.
readonly -a SELF_BUILD_TASKS=(build)

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# shellcheck source=scripts/imports/_consumer-repo.sh
source "${script_dir}/imports/_consumer-repo.sh"

# Holds a double-clicked window open on exit, as every other script here
# does. Optional: without Common-Automation the window simply closes.
# shellcheck source=scripts/imports/_hold-window.sh
source "${script_dir}/imports/_hold-window.sh"

# The import defaults CONSUMER_REPO_PATH to Common-Java, so a match means no
# consumer was named.
if [[ "${CONSUMER_REPO_PATH}" != "${common_java_root}" ]]; then

    # Consumer build: the Gradle project is at the consumer repo's root.
    build_dir="${CONSUMER_REPO_PATH}"
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

# The flags match _ci-gradle.yml so local output, process model and cached
# task results line up with CI - including which deprecations are named, so
# one is not first seen on a runner.
./gradlew "$@" --no-daemon --console=plain --warning-mode all --build-cache

echo
echo "Gradle build passed."
