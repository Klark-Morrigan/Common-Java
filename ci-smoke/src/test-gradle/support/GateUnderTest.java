/**
 * Which gate a suite drives: the task it runs, and the system property the test task hands it the
 * script's path in.
 *
 * A record rather than two loose strings at every call, because they are both strings and a suite
 * names them together once. Lives in the shared support root beside {@link GateProject}, in the
 * default package every gate suite already sits in.
 */
record GateUnderTest(String taskName, String scriptPathSystemProperty) {

    /**
     * The gate script's path, handed in by the test task so a suite does not assume a working
     * directory. Forward slashes keep it valid inside the generated build script on Windows.
     */
    String scriptPath() {
        return System.getProperty(scriptPathSystemProperty).replace('\\', '/');
    }
}
