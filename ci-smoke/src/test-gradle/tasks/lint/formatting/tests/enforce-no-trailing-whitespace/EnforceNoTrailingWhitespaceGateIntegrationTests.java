import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Integration test for the shared enforce-no-trailing-whitespace gate: it
// applies the real gate script into a throwaway project and runs the task,
// asserting the build outcome and the message a developer sees. Lives in the
// Common-Java ci-smoke project - the gate script is shared by every consumer;
// the tests live here beside it.
//
// Violating fixtures are .txt files under java/ and kotlin/ subfolders rather
// than sources. The gate scans every source set this project declares, and this
// tree is one of them, so a violating line kept as source here would be a real
// violation of the gate under test. Default package: the grouping folder is the
// source root, and its kebab name cannot be a Java package.
class EnforceNoTrailingWhitespaceGateIntegrationTests {

    private static final String TASK_PATH = ":enforceNoTrailingWhitespace";

    // A Kotlin tree reaches the gate the way it reaches the compiler: because a
    // source set declares it. The Kotlin plugin does that in a real Kotlin build;
    // declaring it by hand exercises the same contract without this fixture
    // having to resolve that plugin.
    private static final String KOTLIN_TREE_ON_TEST_SOURCE_SET =
        "sourceSets.test.java.srcDir 'src/test/kotlin'\n";

    // A source set of a build's own, named nothing the java plugin knows about -
    // developer tooling, a fixture tree, whatever a project needs.
    private static final String DECLARES_A_TOOLING_SOURCE_SET =
        "sourceSets { tooling { java.srcDirs = ['src/tooling/java'] } }\n";

    // The knob that holds a source set out of the formatter, which holds it out
    // of this gate for the same reason.
    private static final String EXEMPTS_THE_TOOLING_SOURCE_SET =
        "ext.formatterExcludedSourceSets = ['tooling']\n";

    @Test
    void passesWhenNoLineEndsInWhitespaceInJava(@TempDir Path projectDir)
            throws IOException {

        writeJavaSource(projectDir, "clean-line-ends");

        var result = runGate(projectDir, false);

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void failsOnABlankLineCarryingTheIndentAboveItInJava(@TempDir Path projectDir)
            throws IOException {

        // The shape nearly every instance takes: an empty line between members
        // that kept the indent an editor put there when Enter was pressed. It is
        // what a reader never sees and every diff over that line does.
        writeJavaSource(projectDir, "blank-line-carrying-indent");

        var result = runGate(projectDir, true);

        assertThat(result.getOutput())
            .contains("line ends in whitespace");
    }

    @Test
    void failsOnACodeLineEndingInASpaceInJava(@TempDir Path projectDir)
            throws IOException {

        // The other shape: a line with code on it that trails a space, which no
        // amount of reading the file will reveal.
        writeJavaSource(projectDir, "code-line-ending-in-a-space");

        var result = runGate(projectDir, true);

        assertThat(result.getOutput())
            .contains("line ends in whitespace");
    }

    @Test
    void failsOnALineEndingInATabInJava(@TempDir Path projectDir)
            throws IOException {

        // A tab is whitespace the same way a space is, and is likelier still to
        // be invisible - so the rule is stated over whitespace rather than over
        // the space that happens to be the common case.
        writeJavaSource(projectDir, "line-ending-in-a-tab");

        var result = runGate(projectDir, true);

        assertThat(result.getOutput())
            .contains("line ends in whitespace");
    }

    @Test
    void passesWhenNoLineEndsInWhitespaceInKotlin(@TempDir Path projectDir)
            throws IOException {

        writeKotlinSource(projectDir, "clean-line-ends");

        var result = runGate(projectDir, false, KOTLIN_TREE_ON_TEST_SOURCE_SET);

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void failsOnABlankLineCarryingTheIndentAboveItInKotlin(@TempDir Path projectDir)
            throws IOException {

        // Doubles as the proof the scan reaches .kt, not only .java.
        writeKotlinSource(projectDir, "blank-line-carrying-indent");

        var result = runGate(projectDir, true, KOTLIN_TREE_ON_TEST_SOURCE_SET);

        assertThat(result.getOutput())
            .contains("line ends in whitespace");
    }

    @Test
    void passesWhenThereIsNoTestTree(@TempDir Path projectDir)
            throws IOException {

        var result = runGate(projectDir, false);

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void failsOnTrailingWhitespaceInASourceSetTheBuildDeclares(@TempDir Path projectDir)
            throws IOException {

        // The reason the gate reads source sets rather than a list of tree names.
        // Named trees cover main and test and silently stop there, so a source
        // set a build adds for itself accrues violations while the gate reports
        // success.
        writeSource(
            projectDir,
            "src/tooling/java",
            "Tool.java",
            "java/blank-line-carrying-indent");

        var result = runGate(projectDir, true, DECLARES_A_TOOLING_SOURCE_SET);

        assertThat(result.getOutput())
            .contains("line ends in whitespace");
    }

    @Test
    void passesWhenTheOffendingSourceSetIsExemptFromTheFormatter(@TempDir Path projectDir)
            throws IOException {

        // A source set held out of the formatter is held out of this too: it is
        // exempt because its shape is not the repo's to choose, and how its
        // lines end is part of that shape.
        writeSource(
            projectDir,
            "src/tooling/java",
            "Tool.java",
            "java/blank-line-carrying-indent");

        var result = runGate(
            projectDir,
            false,
            DECLARES_A_TOOLING_SOURCE_SET + EXEMPTS_THE_TOOLING_SOURCE_SET);

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    private BuildResult runGate(Path projectDir, boolean expectFailure)
            throws IOException {

        return runGate(projectDir, expectFailure, "");
    }

    private BuildResult runGate(
            Path projectDir,
            boolean expectFailure,
            String extraConfiguration)
                throws IOException {

        // An explicit settings file stops Gradle walking up into a real build.
        Files.writeString(
            projectDir.resolve("settings.gradle"),
            "rootProject.name = 'gate-fixture'\n");

        // The java plugin, because the gate finds its trees by reading the
        // project's source sets - which is also how it reaches one a build
        // declares for itself. Applied before the gate, as a real consumer's
        // conventions file does.
        Files.writeString(
            projectDir.resolve("build.gradle"),
            "apply plugin: 'java'\n"
                + extraConfiguration
                + "apply from: '" + scriptPath() + "'\n");

        var runner = GradleRunner
            .create()
            .withProjectDir(projectDir.toFile())
            .withArguments("enforceNoTrailingWhitespace");

        return expectFailure
            ? runner.buildAndFail()
            : runner.build();
    }

    private void writeJavaSource(Path projectDir, String fixture)
            throws IOException {

        writeSource(projectDir, "src/test/java", "Sample.java", "java/" + fixture);
    }

    private void writeKotlinSource(Path projectDir, String fixture)
            throws IOException {

        writeSource(projectDir, "src/test/kotlin", "Sample.kt", "kotlin/" + fixture);
    }

    private void writeSource(Path projectDir, String tree, String fileName, String fixture)
            throws IOException {

        var dir = projectDir.resolve(tree);

        Files.createDirectories(dir);
        Files.writeString(dir.resolve(fileName), loadFixture(fixture));
    }

    // The gate script path is handed in by the test task so the test does not
    // assume a working directory; forward slashes keep it valid inside the
    // generated build script on Windows.
    private String scriptPath() {
        return System.getProperty("trailing.whitespace.gate.script.path").replace('\\', '/');
    }

    // The fixture name carries its language subfolder (java/ or kotlin/), which
    // resolves under the classpath root the source set exposes for resources.
    private String loadFixture(String name) throws IOException {

        try (InputStream in = getClass().getResourceAsStream("/" + name + ".txt")) {

            if (in == null) {
                throw new IllegalStateException("Missing fixture: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
