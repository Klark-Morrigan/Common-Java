import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The throwaway project a gate suite drives its gate in: a settings file, a build file applying the
 * one script under test, the sources the gate reads, and the run itself.
 *
 * Every gate suite was written around the same four steps, so they were written a dozen times over.
 * What actually varies is which gate, what the build says before and after it is applied, and where
 * a fixture lands - so those are what a caller states, and nothing else.
 *
 * Fluent because the steps are optional and independent: a suite states only the ones its gate
 * needs, rather than passing empty strings past the ones it does not.
 *
 * Everything a caller stages lands on disk as it is stated, so a suite whose cases run a gate twice
 * over what a first run left may state the project afresh each time rather than carry one between
 * them.
 */
final class GateProject {

    private final GateUnderTest gate;
    private final Path projectDir;

    private String buildPreamble = "";
    private String gateConfiguration = "";

    private GateProject(GateUnderTest gate, Path projectDir) {
        this.gate = gate;
        this.projectDir = projectDir;
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

        } catch (IOException cannotRead) {
            throw new UncheckedIOException(cannotRead);
        }
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

        } catch (IOException cannotWrite) {
            throw new UncheckedIOException(cannotWrite);
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

        } catch (IOException cannotCreate) {
            throw new UncheckedIOException(cannotCreate);
        }
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

    private GradleRunner createRunner() {

        // An explicit settings file stops Gradle walking up into a real build.
        holdingText("settings.gradle", "rootProject.name = 'gate-fixture'\n");
        holdingText(
            "build.gradle",
            buildPreamble + "apply from: '" + gate.scriptPath() + "'\n" + gateConfiguration);

        return GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments(gate.taskName());
    }
}
