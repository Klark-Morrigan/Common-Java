import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The throwaway project a gate suite drives its gate in: a settings file, a build file applying the
 * one script under test, the sources the gate reads, and the run itself.
 *
 * Every gate suite runs the same four steps. What actually varies is which gate, what the build says
 * before and after it is applied, and where a fixture lands - so those are what a caller states, and
 * nothing else.
 *
 * Fluent because the steps are optional and independent: a suite states only the ones its gate
 * needs, rather than passing empty strings past the ones it does not.
 *
 * Everything a caller stages lands on disk as it is stated, so a suite whose cases run a gate twice
 * over what a first run left may state the project afresh each time rather than carry one between
 * them.
 *
 * What every suite under src/test-gradle/tasks shares is stated here rather than in each header. A
 * suite applies the real script into this project and runs a real build, asserting the outcome and
 * the message a developer sees; it lives in Common-Java's ci-smoke beside the scripts every consumer
 * applies. Each suite folder is a source root, so a suite sits in the default package: a kebab-case
 * folder name cannot be a Java package.
 */
final class GateProject {

    // The source roots a case poses its sample under. The root is what puts a sample under a gate's
    // scan, so a case names one of these rather than spelling the path.
    static final String MAIN_JAVA_ROOT = "src/main/java";
    static final String MAIN_KOTLIN_ROOT = "src/main/kotlin";
    static final String TEST_JAVA_ROOT = "src/test/java";
    static final String TEST_KOTLIN_ROOT = "src/test/kotlin";
    static final String TEST_FIXTURES_JAVA_ROOT = "src/testFixtures/java";

    // The one sample a case poses, per language. The name is arbitrary.
    private static final String JAVA_SAMPLE_NAME = "Sample.java";
    private static final String KOTLIN_SAMPLE_NAME = "Sample.kt";

    private final GateUnderTest gate;
    private final Path projectDir;

    private String buildPreamble = "";
    private String gateConfiguration = "";

    // The gate's own task unless a case says otherwise, that being what all but a handful of them
    // are about: a rule is asserted by running the task that asserts it.
    private String requestedTask;

    private GateProject(GateUnderTest gate, Path projectDir) {

        this.gate = gate;
        this.projectDir = projectDir;
        this.requestedTask = gate.taskName();
    }

    /**
     * @param gate       the gate to drive
     * @param projectDir the temporary directory the project is written into
     */
    static GateProject driving(GateUnderTest gate, Path projectDir) {
        return new GateProject(gate, projectDir);
    }

    /**
     * The text of a fixture, loaded from the .txt files filed beside the suite. The name carries its
     * language subfolder ('java/...', 'kotlin/...', 'markdown/...'), which resolves under the
     * classpath root the source set exposes for resources.
     *
     * Fixtures are .txt rather than source files because most of them hold the very lines a gate
     * rejects: filed as .java or .kt, they would be compiled, and read by the gates scanning this
     * repository's own trees.
     *
     * @param fixtureName the fixture's path under its suite folder, without the .txt
     * @return the fixture's text
     */
    static String loadFixture(String fixtureName) {

        try (InputStream fixture =
                GateProject.class.getResourceAsStream("/" + fixtureName + ".txt")) {

            if (fixture == null) {
                throw new IllegalStateException("Missing fixture: " + fixtureName);
            }

            return new String(fixture.readAllBytes(), StandardCharsets.UTF_8);

        } catch (IOException ioException) {
            throw new UncheckedIOException(ioException);
        }
    }

    /**
     * The block a consuming build configures a gate in, one statement per line.
     *
     * @param extension  the gate's extension name, as the build calls it
     * @param statements the declarations inside the block, each a whole call
     * @return the block's text, ready for {@link #configuringTheGate}
     */
    static String buildConfigurationBlock(String extension, String... statements) {

        var block = new StringBuilder(extension).append(" {\n");

        for (var statement : statements) {
            block.append("    ").append(statement).append('\n');
        }

        return block.append("}\n").toString();
    }

    /**
     * Applies the java plugin before the gate, as a real consumer's conventions file does. For a
     * gate that finds its trees by reading the project's source sets.
     */
    GateProject applyingTheJavaPlugin() {
        return withBuildPreamble("apply plugin: 'java'\n");
    }

    /**
     * @param preamble build-script text placed before the gate is applied - plugins, repositories,
     *                 a source set a case declares - which is where anything the gate reads as it
     *                 is applied has to stand
     */
    GateProject withBuildPreamble(String preamble) {

        // Added to rather than replacing what is there, since a caller states the preamble in as
        // many pieces as it has: a plugin and the source set that plugin lets it declare are two
        // calls, and the second must not take the first away.
        buildPreamble += preamble;
        return this;
    }

    /**
     * @param configuration build-script text placed after the gate is applied, for a gate the
     *                      consumer has to tell what its rule is. After, because the extension a
     *                      consumer configures does not exist until the gate has declared it
     */
    GateProject configuringTheGate(String configuration) {

        gateConfiguration = configuration;
        return this;
    }

    /**
     * Writes a fixture into the project.
     *
     * @param filePath    where it goes, relative to the project root
     * @param fixtureName the fixture to load, as {@link #loadFixture} names one
     */
    GateProject holdingFixture(String filePath, String fixtureName) {
        return holdingText(filePath, loadFixture(fixtureName));
    }

    /**
     * Writes a Java fixture as the one sample under a source root.
     *
     * @param sourceRoot  the root it goes under, one of the roots this class names
     * @param fixtureName the fixture's name under its suite's java/ folder, without the .txt
     */
    GateProject holdingJavaSample(String sourceRoot, String fixtureName) {
        return holdingFixture(sourceRoot + "/" + JAVA_SAMPLE_NAME, "java/" + fixtureName);
    }

    /**
     * Writes a Kotlin fixture as the one sample under a source root.
     *
     * @param sourceRoot  the root it goes under, one of the roots this class names
     * @param fixtureName the fixture's name under its suite's kotlin/ folder, without the .txt
     */
    GateProject holdingKotlinSample(String sourceRoot, String fixtureName) {
        return holdingFixture(sourceRoot + "/" + KOTLIN_SAMPLE_NAME, "kotlin/" + fixtureName);
    }

    /**
     * Writes a Java fixture where a gate reading the test tree finds it.
     *
     * @param fixtureName the fixture's name under its suite's java/ folder, without the .txt
     */
    GateProject holdingJavaTestSample(String fixtureName) {
        return holdingJavaSample(TEST_JAVA_ROOT, fixtureName);
    }

    /**
     * Writes a Kotlin fixture where a gate reading the test tree finds it.
     *
     * @param fixtureName the fixture's name under its suite's kotlin/ folder, without the .txt
     */
    GateProject holdingKotlinTestSample(String fixtureName) {
        return holdingKotlinSample(TEST_KOTLIN_ROOT, fixtureName);
    }

    /**
     * Reads a file back out of the project, for a case about what a task wrote rather than what it
     * reported.
     *
     * @param filePath the file, relative to the project root
     * @return its text
     */
    String readText(String filePath) {

        try {
            return Files.readString(projectDir.resolve(filePath));

        } catch (IOException ioException) {
            throw new UncheckedIOException(ioException);
        }
    }

    /**
     * Writes a file into the project, for a suite whose content is short enough to state outright
     * rather than file as a fixture.
     *
     * @param filePath where it goes, relative to the project root
     * @param content  what it holds
     */
    GateProject holdingText(String filePath, String content) {

        try {
            var file = projectDir.resolve(filePath);

            Files.createDirectories(file.getParent());
            Files.writeString(file, content);

        } catch (IOException ioException) {
            throw new UncheckedIOException(ioException);
        }

        return this;
    }

    /**
     * Writes a file of raw bytes into the project, for a gate that reads something no editor
     * produces. A case posing one states the bytes, there being nothing to read in a fixture file
     * of them.
     *
     * @param filePath where it goes, relative to the project root
     * @param content  what it holds
     */
    GateProject holdingBytes(String filePath, byte[] content) {

        try {
            var file = projectDir.resolve(filePath);

            Files.createDirectories(file.getParent());
            Files.write(file, content);

        } catch (IOException ioException) {
            throw new UncheckedIOException(ioException);
        }

        return this;
    }

    /**
     * Makes a directory in the project, for a case whose fixture needs one to exist without
     * anything in it - a link target being the common one.
     *
     * @param directoryPath where it goes, relative to the project root
     */
    GateProject holdingDirectory(String directoryPath) {

        try {
            Files.createDirectories(projectDir.resolve(directoryPath));

        } catch (IOException ioException) {
            throw new UncheckedIOException(ioException);
        }

        return this;
    }

    /**
     * Runs a task other than the gate, for a case about what the gate is wired into rather than
     * about the rule it asserts - whether asking for the lifecycle brings the gate with it.
     *
     * @param taskName the task to ask for
     */
    GateProject runningTask(String taskName) {

        requestedTask = taskName;
        return this;
    }

    /**
     * @return the result of a run the gate is expected to fail
     */
    BuildResult runExpectingFailure() {
        return createRunner().buildAndFail();
    }

    /**
     * @return the result of a run the gate is expected to pass
     */
    BuildResult runExpectingSuccess() {
        return createRunner().build();
    }

    /**
     * Runs a build the gate is expected to pass, and checks the gate itself ran and passed. A build
     * succeeding is not enough on its own: a gate skipped, or never scheduled, judges nothing and
     * still leaves the build green.
     *
     * @return the result, for a case with more to read off it
     */
    BuildResult runExpectingGateToPass() {

        var result = runExpectingSuccess();

        assertThat(result.task(gate.taskPath()).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);

        return result;
    }

    /**
     * Runs a build the gate is expected to fail, and checks the gate itself is what failed. A build
     * failing is not enough on its own: a configuration error or another task failing leaves the
     * build red without the gate having judged anything.
     *
     * @return the result, for the case to read the gate's message off
     */
    BuildResult runExpectingGateToFail() {

        var result = runExpectingFailure();

        assertThat(result.task(gate.taskPath()))
            .as("the gate ran")
            .isNotNull();

        assertThat(result.task(gate.taskPath()).getOutcome())
            .isEqualTo(TaskOutcome.FAILED);

        return result;
    }

    private GradleRunner createRunner() {

        // An explicit settings file stops Gradle walking up into a real build.
        holdingText("settings.gradle", "rootProject.name = 'gate-fixture'\n");
        holdingText(
            "build.gradle",
            buildPreamble + "apply from: '" + gate.scriptPath() + "'\n" + gateConfiguration);

        return GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments(requestedTask);
    }
}
