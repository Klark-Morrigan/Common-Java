#!/usr/bin/env bash

# Local mirror of _ci-gradle.yml: runs a Gradle build/test with the same
# tasks and flags the reusable workflow uses, so a green local run means a
# green CI run.
#
# Target-aware, the JVM counterpart to Common-Automation's
# run-ci-yaml-and-bash.sh and Common-DotNet's run-ci-dotnet.sh:
#   - A consumer repo ships a same-named run-ci-gradle.sh shim that sets
#     COMMON_JAVA_TARGET_REPO and execs this; the build runs at that repo's
#     root (its gradlew), mirroring its own ci-gradle.yml (default: test jar).
#   - With no target this runs against Common-Java itself. Common-Java's only
#     Gradle project is the CI smoke project under ci-smoke/, which exercises
#     gradle/java-conventions.gradle - the same build ci.yml runs - so the
#     self-run builds that (default: build), a local mirror of that CI gate.
#
# A Gradle build is a single gradlew invocation, so - unlike the bash root -
# there are no separate underscored sub-runners to orchestrate: this script
# is both the entry and the engine. It holds no knowledge of any consumer's
# build: the install root a Gradle build might need (e.g. via an env var its
# build script reads) is the build's concern - if missing, the build fails
# with its own message.
#
# Common-Automation is expected as a sibling checkout for the shared
# double-click hold-window helper.

set -euo pipefail

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
    default_tasks=(test jar)
else
    # Self build: Common-Java's only Gradle project is the CI smoke project.
    build_dir="${common_java_root}/ci-smoke"
    default_tasks=(build)
fi

# No tasks given -> the default for this build, kept in step with the
# workflow that runs it (_ci-gradle.yml's gradle-tasks for a consumer, ci.yml's
# `build` for the self/smoke case).
if [[ "$#" -eq 0 ]]; then
    set -- "${default_tasks[@]}"
fi

if [[ ! -f "${build_dir}/gradlew" ]]; then
    echo "No gradlew in ${build_dir}; nothing to build." >&2
    exit 1
fi

echo "=== gradlew $* (in ${build_dir}) ==="
cd "${build_dir}"
# --no-daemon --console=plain matches _ci-gradle.yml so local output and
# process model line up with CI.
./gradlew "$@" --no-daemon --console=plain
echo
echo "Gradle build passed."
