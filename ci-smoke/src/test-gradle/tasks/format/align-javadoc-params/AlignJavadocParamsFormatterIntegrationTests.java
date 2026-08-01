import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Integration test for the shared align-javadoc-params formatter: it applies the
// real script into a throwaway project, runs the task, and asserts on the source
// text left behind. Lives in the Common-Java ci-smoke project - the script is
// shared by every consumer; the tests live here beside it.
//
// Unlike the lint gates, this pass rewrites files rather than reporting on them,
// so a case is a before/after fixture pair and the assertion is on the resulting
// text. Cases the pass must leave alone assert against their input fixture, which
// is what stops the formatter from quietly acquiring an opinion about prose it
// was never meant to restyle.
//
// Fixtures load from .txt files under a java/ subfolder, so this tree's own
// sources are never mistaken for them. Default package: the grouping folder is
// the source root, and its kebab name cannot be a Java package.
//
// There is no Kotlin case even though the pass scans .kt. Reaching a .kt file
// through a source set needs the Kotlin plugin to contribute one, and pulling a
// compiler plugin into a fixture build is a cost no other test here pays; the
// KDoc half is exercised by the Kotlin consumers instead.
class AlignJavadocParamsFormatterIntegrationTests {
    private static final String MAIN_SOURCE_PATH = "src/main/java/Sample.java";
    private static final String TEST_SOURCE_PATH = "src/test/java/Sample.java";

    @Test
    void realignsDescriptionsWhenParamColumnsDisagree(@TempDir Path projectDir) throws IOException {
        writeSource(projectDir, MAIN_SOURCE_PATH, "drifted-columns");

        runFormatter(projectDir);

        assertThat(readSource(projectDir, MAIN_SOURCE_PATH))
                .isEqualTo(loadFixture("java/drifted-columns-aligned"));
    }

    @Test
    void pullsAWrappedContinuationOntoTheDescriptionColumn(@TempDir Path projectDir)
            throws IOException {
        writeSource(projectDir, MAIN_SOURCE_PATH, "drifted-continuation");

        runFormatter(projectDir);

        assertThat(readSource(projectDir, MAIN_SOURCE_PATH))
                .isEqualTo(loadFixture("java/drifted-continuation-aligned"));
    }

    @Test
    void movesOnlyTheContinuationOfALoneParam(@TempDir Path projectDir) throws IOException {
        // A single @param has no sibling to disagree with, so the column it already
        // sets is the answer: its own line must come through untouched and only the
        // wrapped line moves.
        writeSource(projectDir, MAIN_SOURCE_PATH, "lone-param-drifted-continuation");

        runFormatter(projectDir);

        assertThat(readSource(projectDir, MAIN_SOURCE_PATH))
                .isEqualTo(loadFixture("java/lone-param-drifted-continuation-aligned"));
    }

    @Test
    void leavesABlockAloneWhenEveryParamUsesOneSpace(@TempDir Path projectDir)
            throws IOException {
        // Nobody aligned this block on purpose, so there is no column to restore.
        writeSource(projectDir, MAIN_SOURCE_PATH, "single-space-params");

        runFormatter(projectDir);

        assertThat(readSource(projectDir, MAIN_SOURCE_PATH))
                .isEqualTo(loadFixture("java/single-space-params"));
    }

    @Test
    void leavesABlockAloneWhenDescriptionsAlreadyAgree(@TempDir Path projectDir)
            throws IOException {
        // Padded on purpose and still in true: the second guard, which is what keeps
        // the pass from re-cutting every aligned block to its own preferred width.
        writeSource(projectDir, MAIN_SOURCE_PATH, "already-aligned-params");

        var result = runFormatter(projectDir);

        assertThat(readSource(projectDir, MAIN_SOURCE_PATH))
                .isEqualTo(loadFixture("java/already-aligned-params"));
        assertThat(result.getOutput()).contains("every @param column already aligned");
    }

    @Test
    void scopesADocBlockThatOpensAndClosesOnOneLine(@TempDir Path projectDir)
            throws IOException {
        // A one-line /** ... */ must close where it opened. Left open, it would run
        // on to the next comment's terminator and swallow the block below it.
        writeSource(projectDir, MAIN_SOURCE_PATH, "one-line-block-before-drifted-block");

        runFormatter(projectDir);

        assertThat(readSource(projectDir, MAIN_SOURCE_PATH))
                .isEqualTo(loadFixture("java/one-line-block-before-drifted-block-aligned"));
    }

    @Test
    void keepsTheLineEndingsTheFileArrivedWith(@TempDir Path projectDir) throws IOException {
        // Consumer mods are checked out CRLF, so a pass that rewrote a block to LF
        // would turn one realigned column into a whole-file diff.
        Files.createDirectories(projectDir.resolve(MAIN_SOURCE_PATH).getParent());
        Files.writeString(projectDir.resolve(MAIN_SOURCE_PATH),
                asCrlf(loadFixture("java/drifted-continuation")));

        runFormatter(projectDir);

        assertThat(readSource(projectDir, MAIN_SOURCE_PATH))
                .isEqualTo(asCrlf(loadFixture("java/drifted-continuation-aligned")));
    }

    @Test
    void skipsASourceSetHeldOutOfTheFormatter(@TempDir Path projectDir) throws IOException {
        // The exemption list is shared with Spotless, so this also covers the pass
        // reading it at all.
        writeSource(projectDir, TEST_SOURCE_PATH, "drifted-columns");

        var result = runFormatter(projectDir, "ext.spotlessExcludedSourceSets = ['test']\n");

        assertThat(readSource(projectDir, TEST_SOURCE_PATH))
                .isEqualTo(loadFixture("java/drifted-columns"));
        assertThat(result.getOutput()).contains("every @param column already aligned");
    }

    @Test
    void namesTheFilesItRewrote(@TempDir Path projectDir) throws IOException {
        // The pass is read as a diff, so its report has to say which files to look at.
        writeSource(projectDir, MAIN_SOURCE_PATH, "drifted-columns");

        var result = runFormatter(projectDir);

        assertThat(result.getOutput()).contains("re-aligned 1 file(s)");
        assertThat(result.getOutput()).contains("Sample.java (1)");
    }

    @Test
    void passesWhenThereIsNoSourceAtAll(@TempDir Path projectDir) throws IOException {
        var result = runFormatter(projectDir);

        assertThat(result.getOutput()).contains("every @param column already aligned");
    }

    private BuildResult runFormatter(Path projectDir) throws IOException {
        return runFormatter(projectDir, "");
    }

    private BuildResult runFormatter(Path projectDir, String buildScriptPrelude)
            throws IOException {
        // An explicit settings file stops Gradle walking up into a real build.
        Files.writeString(projectDir.resolve("settings.gradle"),
                "rootProject.name = 'formatter-fixture'\n");
        // The java plugin is the one piece of the real chain the pass needs: it reads
        // source sets rather than guessing at directory names, so there have to be
        // some. The prelude lands before the script is applied because the pass
        // resolves its source sets as it is applied.
        Files.writeString(projectDir.resolve("build.gradle"),
                "apply plugin: 'java'\n"
                + buildScriptPrelude
                + "apply from: '" + scriptPath() + "'\n");

        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withArguments("alignJavadocParams")
                .build();
    }

    private void writeSource(Path projectDir, String sourcePath, String fixture)
            throws IOException {
        var target = projectDir.resolve(sourcePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, loadFixture("java/" + fixture));
    }

    private String readSource(Path projectDir, String sourcePath) throws IOException {
        return Files.readString(projectDir.resolve(sourcePath));
    }

    // Fixtures are stored LF (.gitattributes pins this repo's text files), so a CRLF
    // case is made from one rather than checked in as a second copy that git would
    // normalise back anyway.
    private String asCrlf(String text) {
        return text.replace("\n", "\r\n");
    }

    // The script path is handed in by the test task so the test does not assume a
    // working directory; forward slashes keep it valid inside the generated build
    // script on Windows.
    private String scriptPath() {
        return System.getProperty("align.javadoc.params.script.path").replace('\\', '/');
    }

    // The fixture name carries its java/ subfolder, which resolves under the
    // classpath root the source set exposes for resources.
    private String loadFixture(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/" + name + ".txt")) {
            if (in == null) {
                throw new IllegalStateException("Missing fixture: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
