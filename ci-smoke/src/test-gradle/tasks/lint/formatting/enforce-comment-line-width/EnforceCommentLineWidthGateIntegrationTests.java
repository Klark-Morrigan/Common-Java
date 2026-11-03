import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Drives the shared enforce-comment-line-width gate's rule. Which files it reads, and the java plugin
// it needs, are the line-gate helper's and are covered by that helper's suite.
class EnforceCommentLineWidthGateIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest("enforceCommentLineWidth", "lint/formatting/enforce-comment-line-width");

    @Test
    void passesWhenEveryCommentFitsTheLimit(@TempDir Path projectDir) {

        // A comment at exactly the limit, in plain and in halfwidth characters, and longer lines
        // holding no comment: a '//' or '/*' in a string or a text block, and code straight after
        // a block comment closes.
        project(projectDir)
            .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "comments-within-the-limit")
            .runExpectingGateToPass();
    }

    @Test
    void failsOnALineCommentOneColumnPastTheLimit(@TempDir Path projectDir) {

        var result = project(projectDir)
            .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "line-comment-past-the-limit")
            .runExpectingGateToFail();

        assertThat(result.getOutput())
            .contains("Sample.java:3: comment line is 121 columns; the limit is 120");
    }

    @Test
    void failsOnALineInsideABlockComment(@TempDir Path projectDir) {

        // The line opens no comment of its own: it is comment because the block above it is open.
        var result = project(projectDir)
            .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "block-comment-past-the-limit")
            .runExpectingGateToFail();

        assertThat(result.getOutput())
            .contains("Sample.java:4: comment line is 130 columns; the limit is 120");
    }

    @Test
    void failsOnACodeLineWhoseTrailingCommentRunsPastTheLimit(@TempDir Path projectDir) {

        var result = project(projectDir)
            .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "trailing-comment-past-the-limit")
            .runExpectingGateToFail();

        assertThat(result.getOutput())
            .contains("Sample.java:3: comment line is 130 columns; the limit is 120");
    }

    @Test
    void countsWideCharactersAsTwoColumns(@TempDir Path projectDir) {

        // 67 characters, 60 of them Chinese text and a fullwidth comma, which an editor draws
        // two columns wide each.
        var result = project(projectDir)
            .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "wide-characters-past-the-limit")
            .runExpectingGateToFail();

        assertThat(result.getOutput())
            .contains("Sample.java:3: comment line is 127 columns; the limit is 120");
    }

    @Test
    void reportsEveryLongCommentLineInOneRun(@TempDir Path projectDir) {

        var result = project(projectDir)
            .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "two-comments-past-the-limit")
            .runExpectingGateToFail();

        assertThat(result.getOutput())
            .contains("Sample.java:3: comment line is 125 columns; the limit is 120")
            .contains("Sample.java:6: comment line is 135 columns; the limit is 120");
    }

    private static GateProject project(Path projectDir) {

        return GateProject
            .driving(GATE, projectDir)
            .applyingTheJavaPlugin();
    }
}
