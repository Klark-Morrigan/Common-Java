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
        new GateUnderTest("alignJavadocParams", "format/align-javadoc-params");

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

    @Test
    void realignsDescriptionsWhenParamColumnsDisagree(@TempDir Path projectDir) {

        var project = formatting(projectDir, MAIN_SAMPLE_PATH, "drifted-columns");

        project.runExpectingGateToPass();

        assertThat(project.readText(MAIN_SAMPLE_PATH))
            .isEqualTo(GateProject.loadFixture("java/drifted-columns-aligned"));
    }

    @Test
    void pullsAWrappedContinuationOntoTheDescriptionColumn(@TempDir Path projectDir) {

        var project = formatting(projectDir, MAIN_SAMPLE_PATH, "drifted-continuation");

        project.runExpectingGateToPass();

        assertThat(project.readText(MAIN_SAMPLE_PATH))
            .isEqualTo(GateProject.loadFixture("java/drifted-continuation-aligned"));
    }

    @Test
    void movesOnlyTheContinuationOfALoneParam(@TempDir Path projectDir) {

        // A single @param has no sibling to disagree with, so the column it already sets is the
        // answer: its own line must come through untouched and only the wrapped line moves.
        var project = formatting(projectDir, MAIN_SAMPLE_PATH, "lone-param-drifted-continuation");

        project.runExpectingGateToPass();

        assertThat(project.readText(MAIN_SAMPLE_PATH))
            .isEqualTo(GateProject.loadFixture("java/lone-param-drifted-continuation-aligned"));
    }

    @Test
    void leavesATagAfterTheParamsWhereItIs(@TempDir Path projectDir) {

        // A tag ends the description above it, so an @return is never pulled onto the @param
        // column as if it were a wrapped line.
        var project = formatting(projectDir, MAIN_SAMPLE_PATH, "return-after-drifted-params");

        project.runExpectingGateToPass();

        assertThat(project.readText(MAIN_SAMPLE_PATH))
            .isEqualTo(GateProject.loadFixture("java/return-after-drifted-params-aligned"));
    }

    @Test
    void leavesABlockAloneWhenEveryParamUsesOneSpace(@TempDir Path projectDir) {

        // Nobody aligned this block on purpose, so there is no column to restore.
        var project = formatting(projectDir, MAIN_SAMPLE_PATH, "single-space-params");

        project.runExpectingGateToPass();

        assertThat(project.readText(MAIN_SAMPLE_PATH))
            .isEqualTo(GateProject.loadFixture("java/single-space-params"));
    }

    @Test
    void leavesABlockAloneWhenDescriptionsAlreadyAgree(@TempDir Path projectDir) {

        // Padded on purpose and still in true: the second guard, which is what keeps the pass from
        // re-cutting every aligned block to its own preferred width.
        var project = formatting(projectDir, MAIN_SAMPLE_PATH, "already-aligned-params");

        var result = project.runExpectingGateToPass();

        assertThat(project.readText(MAIN_SAMPLE_PATH))
            .isEqualTo(GateProject.loadFixture("java/already-aligned-params"));
        assertThat(result.getOutput())
            .contains(NOTHING_TO_ALIGN);
    }

    @Test
    void scopesADocBlockThatOpensAndClosesOnOneLine(@TempDir Path projectDir) {

        // A one-line /** ... */ must close where it opened. Left open, it would run on to the next
        // comment's terminator and swallow the block below it.
        var project = formatting(projectDir, MAIN_SAMPLE_PATH, "one-line-block-before-drifted-block");

        project.runExpectingGateToPass();

        assertThat(project.readText(MAIN_SAMPLE_PATH))
            .isEqualTo(GateProject.loadFixture("java/one-line-block-before-drifted-block-aligned"));
    }

    @Test
    void keepsTheLineEndingsTheFileArrivedWith(@TempDir Path projectDir) {

        // A pass that rewrote a CRLF checkout's block to LF would turn one realigned column into a
        // whole-file diff.
        var project = GateProject
            .driving(FORMATTER, projectDir)
            .applyingTheJavaPlugin()
            .holdingText(MAIN_SAMPLE_PATH, asCrlf(GateProject.loadFixture("java/drifted-continuation")));

        project.runExpectingGateToPass();

        assertThat(project.readText(MAIN_SAMPLE_PATH))
            .isEqualTo(asCrlf(GateProject.loadFixture("java/drifted-continuation-aligned")));
    }

    @Test
    void alignsASourceSetDeclaredAfterTheFormatter(@TempDir Path projectDir) {

        // A consumer declares its own source sets after applying the shared scripts.
        var project = formatting(projectDir, TOOLING_SAMPLE_PATH, "drifted-columns")
            .configuringTheGate(DECLARES_A_TOOLING_SOURCE_SET);

        project.runExpectingGateToPass();

        assertThat(project.readText(TOOLING_SAMPLE_PATH))
            .isEqualTo(GateProject.loadFixture("java/drifted-columns-aligned"));
    }

    @Test
    void skipsASourceSetHeldOutOfTheFormatter(@TempDir Path projectDir) {

        var project = formatting(projectDir, TEST_SAMPLE_PATH, "drifted-columns")
            .withBuildPreamble(EXEMPTS_THE_TEST_SOURCE_SET);

        var result = project.runExpectingGateToPass();

        assertThat(project.readText(TEST_SAMPLE_PATH))
            .isEqualTo(GateProject.loadFixture("java/drifted-columns"));
        assertThat(result.getOutput())
            .contains(NOTHING_TO_ALIGN);
    }

    @Test
    void skipsASourceSetHeldOutAfterTheFormatterIsApplied(@TempDir Path projectDir) {

        var project = formatting(projectDir, TEST_SAMPLE_PATH, "drifted-columns")
            .configuringTheGate(EXEMPTS_THE_TEST_SOURCE_SET);

        project.runExpectingGateToPass();

        assertThat(project.readText(TEST_SAMPLE_PATH))
            .isEqualTo(GateProject.loadFixture("java/drifted-columns"));
    }

    @Test
    void namesTheFilesItRewrote(@TempDir Path projectDir) {

        // The pass is read as a diff, so its report has to say which files to look at.
        var result = formatting(projectDir, MAIN_SAMPLE_PATH, "drifted-columns")
            .runExpectingGateToPass();

        assertThat(result.getOutput())
            .contains("re-aligned 1 file(s)")
            .contains("Sample.java (1)");
    }

    @Test
    void passesWhenThereIsNoSourceAtAll(@TempDir Path projectDir) {

        var result = GateProject
            .driving(FORMATTER, projectDir)
            .applyingTheJavaPlugin()
            .runExpectingGateToPass();

        assertThat(result.getOutput())
            .contains(NOTHING_TO_ALIGN);
    }

    @Test
    void staysOutOfCheck(@TempDir Path projectDir) {

        // It rewrites source, so a build must never run it unasked.
        var project = formatting(projectDir, MAIN_SAMPLE_PATH, "drifted-columns")
            .runningTask("check");

        var result = project.runExpectingSuccess();

        assertThat(result.task(FORMATTER.taskPath()))
            .isNull();
        assertThat(project.readText(MAIN_SAMPLE_PATH))
            .isEqualTo(GateProject.loadFixture("java/drifted-columns"));
    }

    // The java plugin, because the pass reads source sets rather than guessing at directory names.
    private static GateProject formatting(Path projectDir, String samplePath, String fixtureName) {

        return GateProject
            .driving(FORMATTER, projectDir)
            .applyingTheJavaPlugin()
            .holdingFixture(samplePath, "java/" + fixtureName);
    }

    // Fixtures are stored LF (.gitattributes pins this repo's text files), so a CRLF case is made
    // from one rather than checked in as a second copy that git would normalise back anyway.
    private static String asCrlf(String text) {
        return text.replace("\n", "\r\n");
    }
}
