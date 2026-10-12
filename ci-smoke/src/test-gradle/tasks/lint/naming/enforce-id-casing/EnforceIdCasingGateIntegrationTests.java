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
        new GateUnderTest("enforceIdCasing", "lint/naming/enforce-id-casing");

    private static final String PRODUCTION_JAVA_ROOT = GateProject.MAIN_JAVA_ROOT + "/example/production";
    private static final String PRODUCTION_KOTLIN_ROOT = GateProject.MAIN_KOTLIN_ROOT + "/example/production";

    // Markdown is scanned across the whole project rather than under src/, because a repository's longest
    // prose is its root README.
    private static final String ROOT_README_PATH = "README.md";

    private static final String VENDORED_DOCUMENT_PATH = "docs/vendor/upstream/guide.md";

    private static final String MARKDOWN_SAYING_ID = "markdown/prose-saying-id";

    @Nested
    class Rule {

        @Test
        void failsWhenAJavaCommentSaysIdInProse(@TempDir Path projectDir) {

            // Line 18 is a @param description: only the parameter's name is code, the words after it are prose.
            var result = GateProject.driving(GATE, projectDir)
                .holdingJavaSample(PRODUCTION_JAVA_ROOT, "comment-saying-id-in-prose")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:4: prose says \"id\"; the abbreviation is \"ID\"")
                .contains("Sample.java:5: prose says \"id\"")
                .contains("Sample.java:11: prose says \"id\"")
                .contains("Sample.java:12: prose says \"id\"")
                .contains("Sample.java:18: prose says \"id\"");
        }

        @Test
        void failsWhenAKotlinCommentSaysIdInProse(@TempDir Path projectDir) {

            var result = GateProject.driving(GATE, projectDir)
                .holdingKotlinSample(PRODUCTION_KOTLIN_ROOT, "comment-saying-id-in-prose")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.kt:3: prose says \"id\"")
                .contains("Sample.kt:6: prose says \"id\"");
        }

        @Test
        void failsWhenABlockCommentOpensAfterCode(@TempDir Path projectDir) {

            // The comment opens mid-line and runs on, so both its lines are prose.
            var result = GateProject.driving(GATE, projectDir)
                .holdingJavaSample(PRODUCTION_JAVA_ROOT, "block-comment-opening-after-code")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:5: prose says \"id\"")
                .contains("Sample.java:6: prose says \"id\"");
        }

        @Test
        void failsWhenALineCommentFollowsAStringHoldingSlashes(@TempDir Path projectDir) {

            // The '//' inside the URL opens no comment, and must not hide the one that follows the string.
            var result = GateProject.driving(GATE, projectDir)
                .holdingJavaSample(PRODUCTION_JAVA_ROOT, "comment-following-a-string-holding-slashes")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:5: prose says \"id\"");
        }

        @Test
        void failsWhenMarkdownProseSaysId(@TempDir Path projectDir) {

            var result = GateProject.driving(GATE, projectDir)
                .holdingFixture(ROOT_README_PATH, MARKDOWN_SAYING_ID)
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("README.md:4: prose says \"id\"")
                .contains("README.md:7: prose says \"id\"");
        }

        @Test
        void failsWhenProseSpellsTheAbbreviationWithOneCapital(@TempDir Path projectDir) {

            // `Ids` and `Id` are the same error wearing a capital: the abbreviation is two capitals, or code.
            var result = GateProject.driving(GATE, projectDir)
                .holdingFixture(ROOT_README_PATH, "markdown/prose-saying-ids-with-one-capital")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("README.md:3: prose says \"Ids\"")
                .contains("README.md:5: prose says \"Id\"");
        }

        @Test
        void passesWhenAJavaCommentQuotesIdAsCode(@TempDir Path projectDir) {

            // Every way a comment can name the field rather than speak the word: a <pre> sample, a {@code}
            // span, backticks, quotes, an escaped placeholder, a hyphenated file name, a dotted path, and the
            // token after @param.
            GateProject.driving(GATE, projectDir)
                .holdingJavaSample(PRODUCTION_JAVA_ROOT, "comment-quoting-id-as-code")
                .runExpectingGateToPass();
        }

        @Test
        void passesWhenJavaSpellsIdOutsideAnyComment(@TempDir Path projectDir) {

            // Only comment text is judged, so no identifier, literal or column can ever be reported - including
            // a string that holds '//'.
            GateProject.driving(GATE, projectDir)
                .holdingJavaSample(PRODUCTION_JAVA_ROOT, "code-saying-id-outside-comments")
                .runExpectingGateToPass();
        }

        @Test
        void passesWhenKotlinSpellsIdOutsideAnyComment(@TempDir Path projectDir) {

            // A raw string and a backtick-quoted name hold '//' and the word without either being a comment.
            GateProject.driving(GATE, projectDir)
                .holdingKotlinSample(PRODUCTION_KOTLIN_ROOT, "code-saying-id-outside-comments")
                .runExpectingGateToPass();
        }

        @Test
        void passesWhenMarkdownQuotesIdAsCode(@TempDir Path projectDir) {

            // Backticks, a fenced block, an indented block, a link whose anchor the host lower-cases, and a URL.
            GateProject.driving(GATE, projectDir)
                .holdingFixture(ROOT_README_PATH, "markdown/prose-quoting-id-as-code")
                .runExpectingGateToPass();
        }
    }

    @Nested
    class Scope {

        @Test
        void passesWhenMarkdownSitsUnderADefaultExcludedDirectory(@TempDir Path projectDir) {

            GateProject.driving(GATE, projectDir)
                .holdingFixture("node_modules/package/README.md", MARKDOWN_SAYING_ID)
                .runExpectingGateToPass();
        }

        @Test
        void passesWhenMarkdownSitsUnderANestedExcludedDirectory(@TempDir Path projectDir) {

            GateProject.driving(GATE, projectDir)
                .holdingFixture("docs/build/README.md", MARKDOWN_SAYING_ID)
                .runExpectingGateToPass();
        }

        @Test
        void passesWhenProseSitsUnderADirectoryTheBuildAddsToTheSharedExclusions(@TempDir Path projectDir) {

            // One list serves every Markdown gate, and it reaches the code tree too.
            GateProject.driving(GATE, projectDir)
                .holdingFixture("generated/README.md", MARKDOWN_SAYING_ID)
                .holdingJavaSample(GateProject.MAIN_JAVA_ROOT + "/generated", "comment-saying-id-in-prose")
                .configuringTheGate("authoredScanExcludedDirectoryNames << 'generated'\n")
                .runExpectingGateToPass();
        }

        @Test
        void passesWhenMarkdownIsUnderAnExemptPath(@TempDir Path projectDir) {

            GateProject.driving(GATE, projectDir)
                .holdingFixture(VENDORED_DOCUMENT_PATH, MARKDOWN_SAYING_ID)
                .configuringTheGate(GateProject.buildConfigurationBlock(
                    "enforceIdCasing", "exemptPath glob: 'docs/vendor/**/*.md'"))
                .runExpectingGateToPass();
        }

        @Test
        void passesWhenCodeIsUnderAnExemptPath(@TempDir Path projectDir) {

            // A glob is written project-relative for code too, though code is scanned under src/ only.
            GateProject.driving(GATE, projectDir)
                .holdingJavaSample(GateProject.MAIN_JAVA_ROOT + "/example/vendor", "comment-saying-id-in-prose")
                .configuringTheGate(GateProject.buildConfigurationBlock(
                    "enforceIdCasing", "exemptPath glob: 'src/main/java/example/vendor/**'"))
                .runExpectingGateToPass();
        }

        @Test
        void passesWhenThereIsNoSourceTree(@TempDir Path projectDir) {
            GateProject.driving(GATE, projectDir).runExpectingGateToPass();
        }
    }

    @Nested
    class DeclarationsAndRefusals {

        @Test
        void failsWhenAnExemptPathMatchesNoFile(@TempDir Path projectDir) {

            // A stale path exempts nothing while reading in the build as though it does.
            var result = GateProject.driving(GATE, projectDir)
                .holdingFixture(ROOT_README_PATH, "markdown/prose-quoting-id-as-code")
                .configuringTheGate(GateProject.buildConfigurationBlock(
                    "enforceIdCasing", "exemptPath glob: 'docs/gone/**/*.md'"))
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("docs/gone/**/*.md: exemptPath matches no file, so it exempts nothing");
        }

        @Test
        void failsWhenAnExemptPathMatchesOnlyAnExcludedDirectory(@TempDir Path projectDir) {

            // The gate never reads an excluded directory, so a glob reaching only into one exempts nothing.
            var result = GateProject.driving(GATE, projectDir)
                .holdingFixture("build/docs/README.md", MARKDOWN_SAYING_ID)
                .configuringTheGate(GateProject.buildConfigurationBlock(
                    "enforceIdCasing", "exemptPath glob: 'build/**/*.md'"))
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("build/**/*.md: exemptPath matches no file, so it exempts nothing");
        }

        @Test
        void failsWhenAnExemptPathIsDeclaredWithoutAGlob(@TempDir Path projectDir) {

            // A half-declared exemption is refused where it is written rather than accepted as a rule that
            // never fires.
            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(GateProject.buildConfigurationBlock(
                    "enforceIdCasing", "exemptPath path: 'docs/**/*.md'"))
                .runExpectingFailure();

            assertThat(result.getOutput())
                .contains("exemptPath needs 'glob'");
        }
    }
}
