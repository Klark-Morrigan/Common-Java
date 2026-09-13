import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Integration test for the shared enforce-id-casing gate: it applies the real
// gate script into a throwaway project and runs the task, asserting the build
// outcome and the message a developer sees. Lives in the Common-Java ci-smoke
// project - the gate script is shared by every consumer, so the tests live here
// beside it. Fixtures load from .txt files under java/ and markdown/ subfolders
// so the gate never sees a fixture as real source here.
//
// The cases are weighted towards passing rather than failing, because the gate's
// difficulty is entirely in not firing: a rule about one two-letter word is
// worthless if it also reports every field, column and file name that spells it.
//
// The fixture package is 'example.production' rather than any real consumer's,
// because the gate owns the rule and this repo knows no consumer's tree.
// Default package: the grouping folder is the source root, and its kebab name
// cannot be a Java package.
class EnforceIdCasingGateIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest("enforceIdCasing", "id.casing.gate.script.path");

    private static final String TASK_PATH = ":enforceIdCasing";

    private static final String PRODUCTION_PACKAGE_PATH = "example/production";

    private static final String SOURCE_ROOT = "src/main/java";

    // Markdown is scanned across the whole project rather than under src/,
    // because a repository's longest prose is its root README.
    private static final String ROOT_README_PATH = "README.md";

    private static final String FROZEN_DOCUMENT_PATH =
        "docs/dev/implementation/001-sample/plan.md";

    private static final String NOTHING_EXEMPT = "";

    // The case the exemption exists for: a landed plan is a frozen record of a
    // decision rather than live prose to restyle.
    private static final String EXEMPTING_THE_FROZEN_DOCUMENTS =
        buildDeclaration("exemptPath glob: 'docs/dev/implementation/**/plan.md'");

    // An exemption for a path no tree holds - the shape one left behind by a
    // rename or a move takes.
    private static final String EXEMPTING_A_MISSING_PATH =
        buildDeclaration("exemptPath glob: 'docs/gone/**/*.md'");

    // A declaration naming nothing it exempts - the shape a misspelt or
    // forgotten key takes.
    private static final String HALF_DECLARED_EXEMPTION =
        buildDeclaration("exemptPath path: 'docs/**/*.md'");

    @Test
    void failsWhenAJavaCommentSaysIdInProse(@TempDir Path projectDir)
            throws IOException {

        writeJavaSource(projectDir, "comment-saying-id-in-prose");

        var result = runGateExpectingFailure(projectDir, NOTHING_EXEMPT);

        assertThat(result.getOutput())
            .contains("prose says \"id\"; the abbreviation is \"ID\"");
    }

    @Test
    void failsWhenMarkdownProseSaysId(@TempDir Path projectDir)
            throws IOException {

        writeMarkdown(projectDir, ROOT_README_PATH, "prose-saying-id");

        var result = runGateExpectingFailure(projectDir, NOTHING_EXEMPT);

        assertThat(result.getOutput())
            .contains("README.md:4: prose says \"id\"");
    }

    @Test
    void failsWhenProseSpellsTheAbbreviationWithOneCapital(@TempDir Path projectDir)
            throws IOException {

        // `Ids` and `Id` are the same error wearing a capital: the abbreviation
        // is two capitals, or it is code.
        writeMarkdown(projectDir, ROOT_README_PATH, "prose-saying-ids-with-one-capital");

        var result = runGateExpectingFailure(projectDir, NOTHING_EXEMPT);

        assertThat(result.getOutput())
            .contains("prose says \"Ids\"");
    }

    @Test
    void failsWhenAnExemptPathMatchesNoFile(@TempDir Path projectDir)
            throws IOException {

        // A stale path exempts nothing while reading in the build as though it
        // does, which is the silent no-op this whole family exists to remove.
        writeMarkdown(projectDir, ROOT_README_PATH, "prose-quoting-id-as-code");

        var result = runGateExpectingFailure(projectDir, EXEMPTING_A_MISSING_PATH);

        assertThat(result.getOutput())
            .contains("exemptPath matches no file, so it exempts nothing");
    }

    @Test
    void failsWhenAnExemptPathIsDeclaredWithoutAGlob(@TempDir Path projectDir)
            throws IOException {

        // A half-declared exemption exempts nothing, so it must be rejected
        // where it is written rather than accepted as a rule that never fires.
        writeMarkdown(projectDir, ROOT_README_PATH, "prose-quoting-id-as-code");

        var result = runGateExpectingFailure(projectDir, HALF_DECLARED_EXEMPTION);

        assertThat(result.getOutput())
            .contains("exemptPath needs 'glob'");
    }

    @Test
    void passesWhenAJavaCommentQuotesIdAsCode(@TempDir Path projectDir)
            throws IOException {

        // Every way a comment can name the field rather than speak the word: a
        // <pre> sample, a {@code} span, backticks, quotes, an escaped
        // placeholder, a hyphenated file name, a dotted path, and the token
        // after @param.
        writeJavaSource(projectDir, "comment-quoting-id-as-code");

        var result = runGateExpectingSuccess(projectDir, NOTHING_EXEMPT);

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenIdIsSpeltOutsideAnyComment(@TempDir Path projectDir)
            throws IOException {

        // Only comment text is judged at all, which is what makes the rule safe
        // to enforce: no identifier, literal or column can ever be reported, so
        // the gate can never ask for a rename.
        writeJavaSource(projectDir, "code-saying-id-outside-comments");

        var result = runGateExpectingSuccess(projectDir, NOTHING_EXEMPT);

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenMarkdownQuotesIdAsCode(@TempDir Path projectDir)
            throws IOException {

        // The markdown half of the same rule: backticks, a fenced block, an
        // indented block, a link target whose anchor is lower case because the
        // host lower-cases slugs, and a URL.
        writeMarkdown(projectDir, ROOT_README_PATH, "prose-quoting-id-as-code");

        var result = runGateExpectingSuccess(projectDir, NOTHING_EXEMPT);

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenTheFileIsUnderAnExemptPath(@TempDir Path projectDir)
            throws IOException {

        writeMarkdown(projectDir, FROZEN_DOCUMENT_PATH, "prose-saying-id");

        var result = runGateExpectingSuccess(projectDir, EXEMPTING_THE_FROZEN_DOCUMENTS);

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenThereIsNoSourceTree(@TempDir Path projectDir)
            throws IOException {

        var result = runGateExpectingSuccess(projectDir, NOTHING_EXEMPT);

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    // One place owns the block's syntax, so a case that varies the declarations
    // varies only the declarations. Each argument is one whole call.
    private static String buildDeclaration(String... calls) {
        var declaration = new StringBuilder("enforceIdCasing {\n");

        for (var call : calls) {
            declaration.append("    ").append(call).append('\n');
        }
        return declaration.append("}\n").toString();
    }

    // The expected outcome is in the method name rather than a flag, so a call
    // site reads as what it asserts. Both forward to one runner: the pair
    // differs only in which TestKit terminal it drives.
    private BuildResult runGateExpectingFailure(Path projectDir, String declaration)
            throws IOException {

        return buildProject(projectDir, declaration).runExpectingFailure();
    }

    private BuildResult runGateExpectingSuccess(Path projectDir, String declaration)
            throws IOException {

        return buildProject(projectDir, declaration).runExpectingSuccess();
    }

    private GateProject buildProject(Path projectDir, String declaration) {

        return GateProject
            .driving(GATE, projectDir)
            .configuringTheGate(declaration);
    }

    private void writeJavaSource(Path projectDir, String fixture)
            throws IOException {

        GateProject
            .driving(GATE, projectDir)
            .holdingText(
                SOURCE_ROOT + "/" + PRODUCTION_PACKAGE_PATH + "/" + "Sample.java",
                GateProject.loadFixture("java/" + fixture));
    }

    // The path is a parameter rather than fixed, because where a markdown file
    // sits is what decides whether an exemption reaches it - and the root README
    // is the case that proves the scan is not limited to src/.
    private void writeMarkdown(Path projectDir, String filePath, String fixture)
            throws IOException {

        GateProject
            .driving(GATE, projectDir)
            .holdingText(filePath, GateProject.loadFixture("markdown/" + fixture));
    }
}
