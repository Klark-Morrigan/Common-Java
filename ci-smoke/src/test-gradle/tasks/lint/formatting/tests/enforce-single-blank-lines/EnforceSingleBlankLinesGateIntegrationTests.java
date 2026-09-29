import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Drives the shared enforce-single-blank-lines gate.
class EnforceSingleBlankLinesGateIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest("enforceSingleBlankLines", "single.blank.gate.script.path");

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
    void passesWhenBlankLinesAreSingleInJava(@TempDir Path projectDir) {

        writeJavaSource(projectDir, "single-blank-separators");

        var result = runGate(projectDir, false);

        assertThat(result.task(GATE.taskPath()).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void failsOnTwoConsecutiveBlankLinesInJava(@TempDir Path projectDir) {

        writeJavaSource(projectDir, "two-consecutive-blank-lines");

        var result = runGate(projectDir, true);

        assertThat(result.getOutput())
            .contains("consecutive blank line");
    }

    @Test
    void treatsWhitespaceOnlyLineAsBlankInJava(@TempDir Path projectDir) {

        // An empty line followed by a spaces-only line is two blanks in a row:
        // the gate trims before testing emptiness, so the spaces-only line must
        // count toward the run rather than reset it.
        writeJavaSource(projectDir, "whitespace-only-line-counts-as-blank");

        var result = runGate(projectDir, true);

        assertThat(result.getOutput())
            .contains("consecutive blank line");
    }

    @Test
    void passesWhenBlankLinesAreSingleInKotlin(@TempDir Path projectDir) {

        writeKotlinSource(projectDir, "single-blank-separators");

        var result = runGate(projectDir, false, KOTLIN_TREE_ON_TEST_SOURCE_SET);

        assertThat(result.task(GATE.taskPath()).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void failsOnTwoConsecutiveBlankLinesInKotlin(@TempDir Path projectDir) {

        // Doubles as the proof the scan reaches .kt, not only .java.
        writeKotlinSource(projectDir, "two-consecutive-blank-lines");

        var result = runGate(projectDir, true, KOTLIN_TREE_ON_TEST_SOURCE_SET);

        assertThat(result.getOutput())
            .contains("consecutive blank line");
    }

    @Test
    void treatsWhitespaceOnlyLineAsBlankInKotlin(@TempDir Path projectDir) {

        writeKotlinSource(projectDir, "whitespace-only-line-counts-as-blank");

        var result = runGate(projectDir, true, KOTLIN_TREE_ON_TEST_SOURCE_SET);

        assertThat(result.getOutput())
            .contains("consecutive blank line");
    }

    @Test
    void passesWhenThereIsNoTestTree(@TempDir Path projectDir) {

        var result = runGate(projectDir, false);

        assertThat(result.task(GATE.taskPath()).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void failsOnTwoConsecutiveBlankLinesInASourceSetTheBuildDeclares(@TempDir Path projectDir) {

        // The reason the gate reads source sets rather than a list of tree names.
        // Named trees cover main and test and silently stop there, so a source
        // set a build adds for itself accrues violations while the gate reports
        // success - which is how a developer-tooling tree came to hold thirteen.
        writeSource(
            projectDir,
            "src/tooling/java/Tool.java",
            "java/two-consecutive-blank-lines");

        var result = runGate(projectDir, true, DECLARES_A_TOOLING_SOURCE_SET);

        assertThat(result.getOutput())
            .contains("consecutive blank line");
    }

    @Test
    void passesWhenTheOffendingSourceSetIsExemptFromTheFormatter(@TempDir Path projectDir) {

        // A source set held out of the formatter is held out of this too: it is
        // exempt because its shape is not the repo's to choose, and blank lines
        // are part of that shape.
        writeSource(
            projectDir,
            "src/tooling/java/Tool.java",
            "java/two-consecutive-blank-lines");

        var result = runGate(
            projectDir,
            false,
            DECLARES_A_TOOLING_SOURCE_SET + EXEMPTS_THE_TOOLING_SOURCE_SET);

        assertThat(result.task(GATE.taskPath()).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    private BuildResult runGate(Path projectDir, boolean expectFailure) {
        return runGate(projectDir, expectFailure, "");
    }

    // The java plugin, because the gate finds its trees by reading the project's source sets -
    // which is also how it reaches one a build declares for itself. Applied before the gate, as a
    // real consumer's conventions file does.
    private BuildResult runGate(
            Path projectDir,
            boolean expectFailure,
            String extraConfiguration) {

        var project = GateProject
            .driving(GATE, projectDir)
            .applyingTheJavaPlugin()
            .withBuildPreamble(extraConfiguration);

        return expectFailure
            ? project.runExpectingFailure()
            : project.runExpectingSuccess();
    }

    private void writeJavaSource(Path projectDir, String fixture) {
        writeSource(projectDir, "src/test/java/Sample.java", "java/" + fixture);
    }

    private void writeKotlinSource(Path projectDir, String fixture) {
        writeSource(projectDir, "src/test/kotlin/Sample.kt", "kotlin/" + fixture);
    }

    private void writeSource(Path projectDir, String filePath, String fixture) {
        GateProject
            .driving(GATE, projectDir)
            .holdingFixture(filePath, fixture);
    }
}
