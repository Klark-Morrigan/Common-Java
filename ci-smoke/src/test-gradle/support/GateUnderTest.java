/**
 * Which script a suite drives: the task it runs, and the script's path under Common-Java's
 * gradle/tasks, without the .gradle.
 *
 * A record rather than two loose strings at every call, because they are both strings and a suite
 * names them together once. The script path is the suite folder's own path under
 * src/test-gradle/tasks, which verifyEveryGateHasASuite holds every suite to.
 */
record GateUnderTest(
    String taskName,
    String scriptName) {

    // The test task hands in where the scripts live, so a suite does not assume a working directory.
    private static final String SCRIPTS_ROOT_PROPERTY = "gate.scripts.root";

    /**
     * The gate's task as a build result names it, root project and all - derived from the name so a
     * suite cannot state one and look up another.
     */
    String taskPath() {
        return ":" + taskName;
    }

    /**
     * The script's absolute path, with forward slashes so it stays valid inside a generated build
     * script on Windows.
     */
    String scriptPath() {

        var scriptsRoot = System.getProperty(SCRIPTS_ROOT_PROPERTY);

        // Run outside the test task, as from an editor, the property is unset; stated here, where it
        // is the cause, rather than as a "null/..." path the generated build fails to apply.
        if (scriptsRoot == null) {

            throw new IllegalStateException("System property '" + SCRIPTS_ROOT_PROPERTY + "' is unset: run "
                + "the suites through ci-smoke's testGradle task, which hands in where the scripts live");
        }

        return (scriptsRoot + "/" + scriptName + ".gradle")
            .replace('\\', '/');
    }
}
