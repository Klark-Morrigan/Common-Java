import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Drives the shared enforce-no-trailing-whitespace gate's rule. Which files it reads, and the java
// plugin it needs, are the line-gate helper's and are covered by that helper's suite.
class EnforceNoTrailingWhitespaceGateIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest("enforceNoTrailingWhitespace", "lint/formatting/enforce-no-trailing-whitespace");

    // A space before the CRLF ending: whitespace at the end of the line's text, which a reader of
    // the raw bytes could mistake for part of the line ending.
    private static final String SPACE_BEFORE_A_CRLF_ENDING = "class Sample { \r\n}\r\n";

    @Test
    void passesWhenNoLineEndsInWhitespace(@TempDir Path projectDir) {

        // Whitespace inside a line is not at its end, so it is no concern of this rule.
        project(projectDir)
            .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "clean-line-ends")
            .runExpectingGateToPass();
    }

    @Test
    void failsOnABlankLineCarryingTheIndentAboveIt(@TempDir Path projectDir) {

        // The shape nearly every instance takes: an empty line between members that kept the
        // indent an editor put there when Enter was pressed.
        var result = project(projectDir)
            .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "blank-line-carrying-indent")
            .runExpectingGateToFail();

        assertThat(result.getOutput())
            .contains("Sample.java:5: line ends in whitespace");
    }

    @Test
    void failsOnACodeLineEndingInASpace(@TempDir Path projectDir) {

        var result = project(projectDir)
            .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "code-line-ending-in-a-space")
            .runExpectingGateToFail();

        assertThat(result.getOutput())
            .contains("Sample.java:3: line ends in whitespace");
    }

    @Test
    void failsOnALineEndingInATab(@TempDir Path projectDir) {

        // A tab is whitespace the same way a space is, and is likelier still to be invisible.
        var result = project(projectDir)
            .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "line-ending-in-a-tab")
            .runExpectingGateToFail();

        assertThat(result.getOutput())
            .contains("Sample.java:3: line ends in whitespace");
    }

    @Test
    void failsOnASpaceBeforeAWindowsLineEnding(@TempDir Path projectDir) {

        var result = project(projectDir)
            .holdingBytes(
                GateProject.MAIN_JAVA_ROOT + "/Sample.java",
                SPACE_BEFORE_A_CRLF_ENDING.getBytes(StandardCharsets.UTF_8))
            .runExpectingGateToFail();

        assertThat(result.getOutput())
            .contains("Sample.java:1: line ends in whitespace");
    }

    private static GateProject project(Path projectDir) {

        return GateProject
            .driving(GATE, projectDir)
            .applyingTheJavaPlugin();
    }
}
