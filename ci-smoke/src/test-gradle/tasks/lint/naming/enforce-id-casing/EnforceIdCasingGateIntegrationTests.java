import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Drives the shared enforce-id-casing gate.
//
// The cases lean towards passing, because the gate's difficulty is in not firing: a rule about one two-letter
// word is worthless if it also reports every field, column and file name that spells it.
class EnforceIdCasingGateIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest(
            "enforceIdCasing",
            "lint/naming/enforce-id-casing");

    private static final String PRODUCTION_JAVA_ROOT = GateProject.MAIN_JAVA_ROOT + "/example/production";
    private static final String PRODUCTION_KOTLIN_ROOT = GateProject.MAIN_KOTLIN_ROOT + "/example/production";

    // Markdown is scanned across the whole project rather than under src/, because a repository's longest
    // prose is its root README.
    private static final String ROOT_README_PATH = "README.md";

    private static final String VENDORED_DOCUMENT_PATH = "docs/vendor/upstream/guide.md";
    private static final String MARKDOWN_SAYING_ID = "markdown/prose-saying-id";

    @Nested
    class Rule {

        // Line 18 is a @param description: only the parameter's name is code, the words after it are prose.
        @Test
        void failsWhenAJavaCommentSaysIdInProse(@TempDir Path projectDir) {

            // ARRANGE
            var expectedLine4Finding = "Sample.java:4: prose says \"id\"; the abbreviation is \"ID\"";
            var expectedLine5Finding = "Sample.java:5: prose says \"id\"";
            var expectedLine11Finding = "Sample.java:11: prose says \"id\"";
            var expectedLine12Finding = "Sample.java:12: prose says \"id\"";
            var expectedLine18Finding = "Sample.java:18: prose says \"id\"";

            var project = GateProject
                .driving(GATE, projectDir)
                .holdingJavaSample(
                    PRODUCTION_JAVA_ROOT,
                    "comment-saying-id-in-prose");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedLine4Finding)
                .contains(expectedLine5Finding)
                .contains(expectedLine11Finding)
                .contains(expectedLine12Finding)
                .contains(expectedLine18Finding);
        }

        @Test
        void failsWhenAKotlinCommentSaysIdInProse(@TempDir Path projectDir) {

            // ARRANGE
            var expectedLine3Finding = "Sample.kt:3: prose says \"id\"";
            var expectedLine6Finding = "Sample.kt:6: prose says \"id\"";

            var project = GateProject
                .driving(GATE, projectDir)
                .holdingKotlinSample(
                    PRODUCTION_KOTLIN_ROOT,
                    "comment-saying-id-in-prose");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedLine3Finding)
                .contains(expectedLine6Finding);
        }

        // The comment opens mid-line and runs on, so both its lines are prose.
        @Test
        void failsWhenABlockCommentOpensAfterCode(@TempDir Path projectDir) {

            // ARRANGE
            var expectedLine5Finding = "Sample.java:5: prose says \"id\"";
            var expectedLine6Finding = "Sample.java:6: prose says \"id\"";

            var project = GateProject
                .driving(GATE, projectDir)
                .holdingJavaSample(
                    PRODUCTION_JAVA_ROOT,
                    "block-comment-opening-after-code");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedLine5Finding)
                .contains(expectedLine6Finding);
        }

        // The '//' inside the URL opens no comment, and must not hide the one that follows the string.
        @Test
        void failsWhenALineCommentFollowsAStringHoldingSlashes(@TempDir Path projectDir) {

            // ARRANGE
            var expectedFinding = "Sample.java:5: prose says \"id\"";

            var project = GateProject
                .driving(GATE, projectDir)
                .holdingJavaSample(
                    PRODUCTION_JAVA_ROOT,
                    "comment-following-a-string-holding-slashes");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedFinding);
        }

        @Test
        void failsWhenMarkdownProseSaysId(@TempDir Path projectDir) {

            // ARRANGE
            var expectedLine4Finding = "README.md:4: prose says \"id\"";
            var expectedLine7Finding = "README.md:7: prose says \"id\"";

            var project = GateProject
                .driving(GATE, projectDir)
                .holdingFixture(ROOT_README_PATH, MARKDOWN_SAYING_ID);

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedLine4Finding)
                .contains(expectedLine7Finding);
        }

        // `Ids` and `Id` are the same error wearing a capital: the abbreviation is two capitals, or code.
        @Test
        void failsWhenProseSpellsTheAbbreviationWithOneCapital(@TempDir Path projectDir) {

            // ARRANGE
            var expectedPluralFinding = "README.md:3: prose says \"Ids\"";
            var expectedSingularFinding = "README.md:5: prose says \"Id\"";

            var project = GateProject
                .driving(GATE, projectDir)
                .holdingFixture(
                    ROOT_README_PATH,
                    "markdown/prose-saying-ids-with-one-capital");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedPluralFinding)
                .contains(expectedSingularFinding);
        }

        // Every way a comment can name the field rather than speak the word: a <pre> sample, a {@code}
        // span, backticks, quotes, an escaped placeholder, a hyphenated file name, a dotted path, and the
        // token after @param.
        @Test
        void passesWhenAJavaCommentQuotesIdAsCode(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .holdingJavaSample(
                    PRODUCTION_JAVA_ROOT,
                    "comment-quoting-id-as-code");

            // ACT
            project.runExpectingGateToPass();
        }

        // Only comment text is judged, so no identifier, literal or column can ever be reported - including
        // a string that holds '//'.
        @Test
        void passesWhenJavaSpellsIdOutsideAnyComment(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .holdingJavaSample(
                    PRODUCTION_JAVA_ROOT,
                    "code-saying-id-outside-comments");

            // ACT
            project.runExpectingGateToPass();
        }

        // A raw string and a backtick-quoted name hold '//' and the word without either being a comment.
        @Test
        void passesWhenKotlinSpellsIdOutsideAnyComment(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .holdingKotlinSample(
                    PRODUCTION_KOTLIN_ROOT,
                    "code-saying-id-outside-comments");

            // ACT
            project.runExpectingGateToPass();
        }

        // Backticks, a fenced block, an indented block, a link whose anchor the host lower-cases, and a URL.
        @Test
        void passesWhenMarkdownQuotesIdAsCode(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .holdingFixture(
                    ROOT_README_PATH,
                    "markdown/prose-quoting-id-as-code");

            // ACT
            project.runExpectingGateToPass();
        }

        // A fence closes only on a run at least as long as its own, with nothing after it, so a sample
        // showing a shorter fence, info string and all, stays sample text to the end.
        @Test
        void passesWhenALongerFenceQuotesAShorterOne(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .holdingFixture(
                    ROOT_README_PATH,
                    "markdown/fence-quoting-a-shorter-fence");

            // ACT
            project.runExpectingGateToPass();
        }
    }

    @Nested
    class Scope {

        @Test
        void passesWhenMarkdownSitsUnderADefaultExcludedDirectory(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .holdingFixture(
                    "node_modules/package/README.md",
                    MARKDOWN_SAYING_ID);

            // ACT
            project.runExpectingGateToPass();
        }

        @Test
        void passesWhenMarkdownSitsUnderANestedExcludedDirectory(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .holdingFixture(
                    "docs/build/README.md",
                    MARKDOWN_SAYING_ID);

            // ACT
            project.runExpectingGateToPass();
        }

        // One list serves every Markdown gate, and it reaches the code tree too.
        @Test
        void passesWhenProseSitsUnderADirectoryTheBuildAddsToTheSharedExclusions(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .holdingFixture(
                    "generated/README.md",
                    MARKDOWN_SAYING_ID)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT + "/generated",
                    "comment-saying-id-in-prose")
                .configuringTheGate("authoredScanExcludedDirectoryNames << 'generated'\n");

            // ACT
            project.runExpectingGateToPass();
        }

        @Test
        void passesWhenMarkdownIsUnderAnExemptPath(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .holdingFixture(VENDORED_DOCUMENT_PATH, MARKDOWN_SAYING_ID)
                .configuringTheGate(GateProject.buildConfigurationBlock(
                    "enforceIdCasing",
                    "exemptPath glob: 'docs/vendor/**/*.md'"));

            // ACT
            project.runExpectingGateToPass();
        }

        // A glob is written project-relative for code too, though code is scanned under src/ only.
        @Test
        void passesWhenCodeIsUnderAnExemptPath(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT + "/example/vendor",
                    "comment-saying-id-in-prose")
                .configuringTheGate(GateProject.buildConfigurationBlock(
                    "enforceIdCasing",
                    "exemptPath glob: 'src/main/java/example/vendor/**'"));

            // ACT
            project.runExpectingGateToPass();
        }

        @Test
        void passesWhenThereIsNoSourceTree(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject.driving(GATE, projectDir);

            // ACT
            project.runExpectingGateToPass();
        }
    }

    @Nested
    class DeclarationsAndRefusals {

        // A stale path exempts nothing while reading in the build as though it does.
        @Test
        void failsWhenAnExemptPathMatchesNoFile(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "docs/gone/**/*.md: exemptPath matches no file, so it exempts nothing";

            var project = GateProject
                .driving(GATE, projectDir)
                .holdingFixture(
                    ROOT_README_PATH,
                    "markdown/prose-quoting-id-as-code")
                .configuringTheGate(GateProject.buildConfigurationBlock(
                    "enforceIdCasing",
                    "exemptPath glob: 'docs/gone/**/*.md'"));

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // The gate never reads an excluded directory, so a glob reaching only into one exempts nothing.
        @Test
        void failsWhenAnExemptPathMatchesOnlyAnExcludedDirectory(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "build/**/*.md: exemptPath matches no file, so it exempts nothing";

            var project = GateProject
                .driving(GATE, projectDir)
                .holdingFixture(
                    "build/docs/README.md",
                    MARKDOWN_SAYING_ID)
                .configuringTheGate(GateProject.buildConfigurationBlock(
                    "enforceIdCasing",
                    "exemptPath glob: 'build/**/*.md'"));

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // A half-declared exemption is refused where it is written rather than accepted as a rule that
        // never fires.
        @Test
        void failsWhenAnExemptPathIsDeclaredWithoutAGlob(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "exemptPath needs 'glob'";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(GateProject.buildConfigurationBlock(
                    "enforceIdCasing",
                    "exemptPath glob: ''"));

            // ACT
            var actualResult = project.runExpectingFailure();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // The shape a misspelt 'glob:' takes, which would otherwise be dropped in silence.
        @Test
        void failsWhenAnExemptPathNamesAKeyItDoesNotTake(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "exemptPath does not take 'path'; it takes 'glob'";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(GateProject.buildConfigurationBlock(
                    "enforceIdCasing",
                    "exemptPath path: 'docs/**/*.md'"));

            // ACT
            var actualResult = project.runExpectingFailure();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }
    }
}
