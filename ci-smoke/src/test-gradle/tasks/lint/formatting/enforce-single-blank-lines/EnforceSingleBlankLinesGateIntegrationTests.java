import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Drives the shared enforce-single-blank-lines gate's rule. Which files it reads, and the java
// plugin it needs, are the line-gate helper's and are covered by that helper's suite.
class EnforceSingleBlankLinesGateIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest("enforceSingleBlankLines", "lint/formatting/enforce-single-blank-lines");

    @Test
    void passesWhenBlankLinesAreSingle(@TempDir Path projectDir) {

        // Several single blank lines, one of them holding only spaces: a run ends at the first line
        // with text on it.
        project(projectDir)
            .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "single-blank-separators")
            .runExpectingGateToPass();
    }

    @Test
    void failsOnTwoConsecutiveBlankLines(@TempDir Path projectDir) {

        var result = project(projectDir)
            .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "two-consecutive-blank-lines")
            .runExpectingGateToFail();

        assertThat(result.getOutput())
            .contains("Sample.java:5: consecutive blank line");
    }

    @Test
    void treatsAWhitespaceOnlyLineAsBlank(@TempDir Path projectDir) {

        // An empty line followed by a spaces-only line is two blanks in a row.
        var result = project(projectDir)
            .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "whitespace-only-line-counts-as-blank")
            .runExpectingGateToFail();

        assertThat(result.getOutput())
            .contains("Sample.java:5: consecutive blank line");
    }

    private static GateProject project(Path projectDir) {

        return GateProject
            .driving(GATE, projectDir)
            .applyingTheJavaPlugin();
    }
}
