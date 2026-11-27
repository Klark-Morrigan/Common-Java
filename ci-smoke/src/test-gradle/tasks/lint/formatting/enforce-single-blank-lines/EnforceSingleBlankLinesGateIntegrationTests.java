import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Drives the shared enforce-single-blank-lines gate's rule. Which files it reads, and the java
// plugin it needs, are the line-gate helper's and are covered by that helper's suite.
class EnforceSingleBlankLinesGateIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest(
            "enforceSingleBlankLines",
            "lint/formatting/enforce-single-blank-lines");

    @Nested
    class EnforceSingleBlankLines {

        // Several single blank lines, one of them holding only spaces: a run ends at the first line
        // with text on it.
        @Test
        void passesWhenBlankLinesAreSingle(@TempDir Path projectDir) {

            // ARRANGE
            var project = project(projectDir)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT,
                    "single-blank-separators");

            // ACT
            project.runExpectingGateToPass();
        }

        @Test
        void failsOnTwoConsecutiveBlankLines(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "Sample.java:5: consecutive blank line";

            var project = project(projectDir)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT,
                    "two-consecutive-blank-lines");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // A file saved with Windows line endings has the same blank lines as one saved without.
        @Test
        void failsOnTwoConsecutiveBlankLinesEndingInCarriageReturns(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "Sample.java:5: consecutive blank line";

            var crlfText = GateProject
                .loadFixture("java/two-consecutive-blank-lines")
                .replace("\n", "\r\n");

            var project = project(projectDir)
                .holdingText(
                    GateProject.MAIN_JAVA_ROOT + "/Sample.java",
                    crlfText);

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // Blank lines trailing the last brace are a run like any other.
        @Test
        void failsOnTwoBlankLinesEndingTheFile(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "Sample.java:4: consecutive blank line";

            var project = project(projectDir)
                .holdingText(
                    GateProject.MAIN_JAVA_ROOT + "/Sample.java",
                    "class Sample {\n}\n\n\n");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // An empty line followed by a spaces-only line is two blanks in a row.
        @Test
        void treatsAWhitespaceOnlyLineAsBlank(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "Sample.java:5: consecutive blank line";

            var project = project(projectDir)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT,
                    "whitespace-only-line-counts-as-blank");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }
    }

    private static GateProject project(Path projectDir) {

        return GateProject
            .driving(GATE, projectDir)
            .applyingTheJavaPlugin();
    }
}
