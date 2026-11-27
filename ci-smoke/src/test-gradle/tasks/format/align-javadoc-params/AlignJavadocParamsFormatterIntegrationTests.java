import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Drives the shared align-javadoc-params formatter, asserting on the source text it leaves behind.
//
// Unlike the lint gates, this pass rewrites files rather than reporting on them, so a case is a
// before/after fixture pair and the assertion is on the resulting text. Cases the pass must leave
// alone assert against their input fixture, which is what stops the formatter from quietly
// acquiring an opinion about prose it was never meant to restyle.
//
// There is no Kotlin case even though the pass scans .kt. Reaching a .kt file through a source set
// needs the Kotlin plugin to contribute one, and pulling a compiler plugin into a fixture build is a
// cost no other suite here pays; the KDoc half is exercised by the Kotlin consumers instead.
class AlignJavadocParamsFormatterIntegrationTests {

    private static final GateUnderTest FORMATTER =
        new GateUnderTest(
            "alignJavadocParams",
            "format/align-javadoc-params");

    private static final String MAIN_SAMPLE_PATH = GateProject.MAIN_JAVA_ROOT + "/Sample.java";
    private static final String TEST_SAMPLE_PATH = GateProject.TEST_JAVA_ROOT + "/Sample.java";

    // A source set of a build's own, named nothing the java plugin knows about.
    private static final String TOOLING_JAVA_ROOT = "src/tooling/java";
    private static final String TOOLING_SAMPLE_PATH = TOOLING_JAVA_ROOT + "/Sample.java";

    private static final String DECLARES_A_TOOLING_SOURCE_SET =
        "sourceSets { tooling { java.srcDirs = ['" + TOOLING_JAVA_ROOT + "'] } }\n";

    // The exemption list is shared with Spotless, so a case holding a set out also covers the pass
    // reading the list at all.
    private static final String EXEMPTS_THE_TEST_SOURCE_SET =
        "ext.formatterExcludedSourceSets = ['test']\n";

    private static final String NOTHING_TO_ALIGN = "every @param column already aligned";

    @Nested
    class AlignJavadocParams {

        @Test
        void realignsDescriptionsWhenParamColumnsDisagree(@TempDir Path projectDir) {

            // ARRANGE
            var expectedText = GateProject.loadFixture("java/drifted-columns-aligned");
            var project = formatting(
                projectDir,
                MAIN_SAMPLE_PATH,
                "drifted-columns");

            // ACT
            project.runExpectingGateToPass();
            var actualText = project.readText(MAIN_SAMPLE_PATH);

            // ASSERT
            assertThat(actualText)
                .isEqualTo(expectedText);
        }

        @Test
        void pullsAWrappedContinuationOntoTheDescriptionColumn(@TempDir Path projectDir) {

            // ARRANGE
            var expectedText = GateProject.loadFixture("java/drifted-continuation-aligned");
            var project = formatting(
                projectDir,
                MAIN_SAMPLE_PATH,
                "drifted-continuation");

            // ACT
            project.runExpectingGateToPass();
            var actualText = project.readText(MAIN_SAMPLE_PATH);

            // ASSERT
            assertThat(actualText)
                .isEqualTo(expectedText);
        }

        // A single @param has no sibling to disagree with, so the column it already sets is the answer:
        // its own line must come through untouched and only the wrapped line moves.
        @Test
        void movesOnlyTheContinuationOfALoneParam(@TempDir Path projectDir) {

            // ARRANGE
            var expectedText = GateProject.loadFixture(
                "java/lone-param-drifted-continuation-aligned");

            var project = formatting(
                projectDir,
                MAIN_SAMPLE_PATH,
                "lone-param-drifted-continuation");

            // ACT
            project.runExpectingGateToPass();
            var actualText = project.readText(MAIN_SAMPLE_PATH);

            // ASSERT
            assertThat(actualText)
                .isEqualTo(expectedText);
        }

        // A tag ends the description above it, so an @return is never pulled onto the @param column as if
        // it were a wrapped line.
        @Test
        void leavesATagAfterTheParamsWhereItIs(@TempDir Path projectDir) {

            // ARRANGE
            var expectedText = GateProject.loadFixture(
                "java/return-after-drifted-params-aligned");

            var project = formatting(
                projectDir,
                MAIN_SAMPLE_PATH,
                "return-after-drifted-params");

            // ACT
            project.runExpectingGateToPass();
            var actualText = project.readText(MAIN_SAMPLE_PATH);

            // ASSERT
            assertThat(actualText)
                .isEqualTo(expectedText);
        }

        // Nobody aligned this block on purpose, so there is no column to restore.
        @Test
        void leavesABlockAloneWhenEveryParamUsesOneSpace(@TempDir Path projectDir) {

            // ARRANGE
            var expectedText = GateProject.loadFixture("java/single-space-params");
            var project = formatting(
                projectDir,
                MAIN_SAMPLE_PATH,
                "single-space-params");

            // ACT
            project.runExpectingGateToPass();
            var actualText = project.readText(MAIN_SAMPLE_PATH);

            // ASSERT
            assertThat(actualText)
                .isEqualTo(expectedText);
        }

        // Padded on purpose and still in true: the second guard, which is what keeps the pass from
        // re-cutting every aligned block to its own preferred width.
        @Test
        void leavesABlockAloneWhenDescriptionsAlreadyAgree(@TempDir Path projectDir) {

            // ARRANGE
            var expectedText = GateProject.loadFixture("java/already-aligned-params");
            var project = formatting(
                projectDir,
                MAIN_SAMPLE_PATH,
                "already-aligned-params");

            // ACT
            var actualResult = project.runExpectingGateToPass();
            var actualText = project.readText(MAIN_SAMPLE_PATH);

            // ASSERT
            assertThat(actualText)
                .isEqualTo(expectedText);

            assertThat(actualResult.getOutput())
                .contains(NOTHING_TO_ALIGN);
        }

        // A one-line /** ... */ must close where it opened. Left open, it would run on to the next
        // comment's terminator and swallow the block below it.
        @Test
        void scopesADocBlockThatOpensAndClosesOnOneLine(@TempDir Path projectDir) {

            // ARRANGE
            var expectedText = GateProject.loadFixture(
                "java/one-line-block-before-drifted-block-aligned");

            var project = formatting(
                projectDir,
                MAIN_SAMPLE_PATH,
                "one-line-block-before-drifted-block");

            // ACT
            project.runExpectingGateToPass();
            var actualText = project.readText(MAIN_SAMPLE_PATH);

            // ASSERT
            assertThat(actualText)
                .isEqualTo(expectedText);
        }

        // A pass that rewrote a CRLF checkout's block to LF would turn one realigned column into a
        // whole-file diff.
        @Test
        void keepsTheLineEndingsTheFileArrivedWith(@TempDir Path projectDir) {

            // ARRANGE
            var expectedText = asCrlf(GateProject.loadFixture(
                "java/drifted-continuation-aligned"));

            var project = GateProject
                .driving(FORMATTER, projectDir)
                .applyingTheJavaPlugin()
                .holdingText(
                    MAIN_SAMPLE_PATH,
                    asCrlf(GateProject.loadFixture("java/drifted-continuation")));

            // ACT
            project.runExpectingGateToPass();
            var actualText = project.readText(MAIN_SAMPLE_PATH);

            // ASSERT
            assertThat(actualText)
                .isEqualTo(expectedText);
        }

        // A consumer declares its own source sets after applying the shared scripts.
        @Test
        void alignsASourceSetDeclaredAfterTheFormatter(@TempDir Path projectDir) {

            // ARRANGE
            var expectedText = GateProject.loadFixture("java/drifted-columns-aligned");

            var project = formatting(
                    projectDir,
                    TOOLING_SAMPLE_PATH,
                    "drifted-columns")
                .configuringTheGate(DECLARES_A_TOOLING_SOURCE_SET);

            // ACT
            project.runExpectingGateToPass();
            var actualText = project.readText(TOOLING_SAMPLE_PATH);

            // ASSERT
            assertThat(actualText)
                .isEqualTo(expectedText);
        }

        @Test
        void skipsASourceSetHeldOutOfTheFormatter(@TempDir Path projectDir) {

            // ARRANGE
            var expectedText = GateProject.loadFixture("java/drifted-columns");

            var project = formatting(projectDir, TEST_SAMPLE_PATH, "drifted-columns")
                .withBuildPreamble(EXEMPTS_THE_TEST_SOURCE_SET);

            // ACT
            var actualResult = project.runExpectingGateToPass();
            var actualText = project.readText(TEST_SAMPLE_PATH);

            // ASSERT
            assertThat(actualText)
                .isEqualTo(expectedText);

            assertThat(actualResult.getOutput())
                .contains(NOTHING_TO_ALIGN);
        }

        @Test
        void skipsASourceSetHeldOutAfterTheFormatterIsApplied(@TempDir Path projectDir) {

            // ARRANGE
            var expectedText = GateProject.loadFixture("java/drifted-columns");

            var project = formatting(projectDir, TEST_SAMPLE_PATH, "drifted-columns")
                .configuringTheGate(EXEMPTS_THE_TEST_SOURCE_SET);

            // ACT
            project.runExpectingGateToPass();
            var actualText = project.readText(TEST_SAMPLE_PATH);

            // ASSERT
            assertThat(actualText)
                .isEqualTo(expectedText);
        }

        // The pass is read as a diff, so its report has to say which files to look at.
        @Test
        void namesTheFilesItRewrote(@TempDir Path projectDir) {

            // ARRANGE
            var expectedSummary = "re-aligned 1 file(s)";
            var expectedFileLine = "Sample.java (1)";
            var project = formatting(projectDir, MAIN_SAMPLE_PATH, "drifted-columns");

            // ACT
            var actualResult = project.runExpectingGateToPass();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedSummary)
                .contains(expectedFileLine);
        }

        @Test
        void passesWhenThereIsNoSourceAtAll(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(FORMATTER, projectDir)
                .applyingTheJavaPlugin();

            // ACT
            var actualResult = project.runExpectingGateToPass();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(NOTHING_TO_ALIGN);
        }

        // It rewrites source, so a build must never run it unasked.
        @Test
        void staysOutOfCheck(@TempDir Path projectDir) {

            // ARRANGE
            var expectedText = GateProject.loadFixture("java/drifted-columns");

            var project = formatting(projectDir, MAIN_SAMPLE_PATH, "drifted-columns")
                .runningTask("check");

            // ACT
            var actualResult = project.runExpectingSuccess();
            var actualText = project.readText(MAIN_SAMPLE_PATH);

            // ASSERT
            assertThat(actualResult.task(FORMATTER.taskPath()))
                .isNull();

            assertThat(actualText)
                .isEqualTo(expectedText);
        }
    }

    // The java plugin, because the pass reads source sets rather than guessing at directory names.
    private static GateProject formatting(
            Path projectDir,
            String samplePath,
            String fixtureName) {

        return GateProject
            .driving(FORMATTER, projectDir)
            .applyingTheJavaPlugin()
            .holdingFixture(
                samplePath,
                "java/" + fixtureName);
    }

    // Fixtures are stored LF (.gitattributes pins this repo's text files), so a CRLF case is made
    // from one rather than checked in as a second copy that git would normalise back anyway.
    private static String asCrlf(String text) {
        return text.replace("\n", "\r\n");
    }
}
