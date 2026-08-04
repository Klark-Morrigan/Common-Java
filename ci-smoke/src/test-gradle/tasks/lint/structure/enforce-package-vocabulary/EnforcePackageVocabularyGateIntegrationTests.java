import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Integration test for the shared enforce-package-vocabulary gate: it applies
// the real gate script into a throwaway project and runs the task, asserting
// the build outcome and the message a developer sees. Lives in the Common-Java
// ci-smoke project - the gate script is shared by every consumer, so the tests
// live here beside it. Fixtures load from .txt files under java/, kotlin/ and
// markdown/ subfolders so the gate, which scans every source set, never sees a
// fixture as real source here; this tree is src/test-gradle, deliberately out
// of its reach.
//
// The fixture packages are 'example.framework' and 'example.feature' rather
// than any real consumer's, because the gate owns only the mechanism and this
// repo knows no consumer's vocabulary. Default package: the grouping folder is
// the source root, and its kebab name cannot be a Java package.
class EnforcePackageVocabularyGateIntegrationTests {

    private static final String TASK_PATH = ":enforcePackageVocabulary";

    // What the consuming build declares, and what the fixtures are written
    // against: the framework root may not speak the feature's words.
    private static final String CLOSED_PACKAGE_ROOT = "example.framework";
    private static final String FRAMEWORK_PACKAGE_PATH = "example/framework";

    // One two-word entry among the three, since a phrase and a single word are
    // found by the same run-of-words match and only the phrase can wrap.
    private static final String FORBIDDEN_WORDS =
        "'territory', 'national border', 'bloc'";

    private static final String ALLOWED_README_PATH =
        "src/main/java/" + FRAMEWORK_PACKAGE_PATH + "/README.md";

    private static final String NO_WORDS_DECLARED = "";
    private static final String CLOSED_TO_THE_FEATURES_WORDS = buildDeclaration(
        "forbidWords under: '" + CLOSED_PACKAGE_ROOT + "', words: [" + FORBIDDEN_WORDS + "]");

    private static final String CLOSED_BUT_ALLOWING_THE_README = buildDeclaration(
        "forbidWords under: '" + CLOSED_PACKAGE_ROOT + "', words: [" + FORBIDDEN_WORDS + "]",
        "allowWord word: 'bloc', inFile: '" + ALLOWED_README_PATH + "'");

    // An allowance for a file no source tree holds - the shape a path left
    // behind by a rename or a move takes.
    private static final String CLOSED_BUT_ALLOWING_A_MISSING_FILE = buildDeclaration(
        "forbidWords under: '" + CLOSED_PACKAGE_ROOT + "', words: [" + FORBIDDEN_WORDS + "]",
        "allowWord word: 'bloc', inFile: 'src/main/java/"
            + FRAMEWORK_PACKAGE_PATH + "/GONE.md'");

    // A declaration naming only the closed root, which says nothing about what
    // it may not say - the shape a misspelt or forgotten 'words:' takes.
    private static final String HALF_DECLARED_RULE = buildDeclaration(
        "forbidWords under: '" + CLOSED_PACKAGE_ROOT + "'");

    @Test
    void failsWhenACommentSaysAForbiddenWord(@TempDir Path projectDir)
            throws IOException {

        writeJavaSource(projectDir, "src/main/java", FRAMEWORK_PACKAGE_PATH,
            "framework-saying-a-forbidden-word");

        var result = runGateExpectingFailure(projectDir, CLOSED_TO_THE_FEATURES_WORDS);

        assertThat(result.getOutput())
            .contains("example.framework may not say \"territory\"");
    }

    @Test
    void failsWhenAMethodNameSaysAForbiddenPhrase(@TempDir Path projectDir)
            throws IOException {

        // The case a hand sweep misses, and the reason this gate exists rather
        // than a checklist: the words are spelt in camel case, so searching for
        // the phrase as prose finds nothing and the name reads as one token.
        writeJavaSource(projectDir, "src/main/java", FRAMEWORK_PACKAGE_PATH,
            "framework-saying-a-forbidden-phrase-in-a-name");

        var result = runGateExpectingFailure(projectDir, CLOSED_TO_THE_FEATURES_WORDS);

        assertThat(result.getOutput())
            .contains("may not say \"national border\"");
    }

    @Test
    void failsWhenAMarkdownFileBesideTheCodeSaysAForbiddenWord(@TempDir Path projectDir)
            throws IOException {

        // The README filed in the package is the surface a reader of a
        // framework reads first, and it carries no imports for any other gate
        // to judge it by.
        writeMarkdown(projectDir, "src/main/java", FRAMEWORK_PACKAGE_PATH,
            "framework-readme-saying-a-forbidden-word");

        var result = runGateExpectingFailure(projectDir, CLOSED_TO_THE_FEATURES_WORDS);

        assertThat(result.getOutput())
            .contains("may not say \"territory\"");
    }

    @Test
    void failsWhenProseWrapsAForbiddenPhraseAcrossTwoLines(@TempDir Path projectDir)
            throws IOException {

        // Prose wraps where the column runs out rather than where the phrase
        // ends, so a per-line scan would miss half the prose uses of any
        // multi-word entry.
        writeMarkdown(projectDir, "src/main/java", FRAMEWORK_PACKAGE_PATH,
            "framework-readme-wrapping-a-forbidden-phrase");

        var result = runGateExpectingFailure(projectDir, CLOSED_TO_THE_FEATURES_WORDS);

        assertThat(result.getOutput())
            .contains("may not say \"national border\"");
    }

    @Test
    void failsWhenASourceSetBeyondMainAndTestSaysAForbiddenWord(@TempDir Path projectDir)
            throws IOException {

        // A dev-tooling or fixture source set is written in prose like any
        // other - it is simply prose nobody sweeps, which is exactly where the
        // words a hand sweep missed collect.
        writeJavaSource(projectDir, "src/utils/java", FRAMEWORK_PACKAGE_PATH,
            "framework-saying-a-forbidden-word");

        var result = runGateExpectingFailure(projectDir, CLOSED_TO_THE_FEATURES_WORDS);

        assertThat(result.getOutput())
            .contains("may not say \"territory\"");
    }

    @Test
    void failsWhenKotlinCodeSaysAForbiddenWord(@TempDir Path projectDir)
            throws IOException {

        var dir = projectDir.resolve("src/main/kotlin/" + FRAMEWORK_PACKAGE_PATH);

        Files.createDirectories(dir);
        Files.writeString(
            dir.resolve("Sample.kt"),
            loadFixture("kotlin/framework-saying-a-forbidden-word"));

        var result = runGateExpectingFailure(projectDir, CLOSED_TO_THE_FEATURES_WORDS);

        assertThat(result.getOutput())
            .contains("may not say \"territory\"");
    }

    @Test
    void failsWhenAnAllowedWordIsSaidInAnotherFile(@TempDir Path projectDir)
            throws IOException {

        // An allowance is per file: letting one README make its argument must
        // not unlock the word for the code sitting next to it.
        writeMarkdown(projectDir, "src/main/java", FRAMEWORK_PACKAGE_PATH,
            "framework-readme-saying-an-allowed-word");
        writeJavaSource(projectDir, "src/main/java", FRAMEWORK_PACKAGE_PATH,
            "framework-saying-nothing-forbidden",
            "Neighbour.java",
            "// The palettes here resolve a bloc's recede.\n");

        var result = runGateExpectingFailure(projectDir, CLOSED_BUT_ALLOWING_THE_README);

        assertThat(result.getOutput())
            .contains("Neighbour.java")
            .contains("may not say \"bloc\"")
            .doesNotContain("README.md:");
    }

    @Test
    void failsWhenAnAllowanceNamesAFileTheGateDoesNotScan(@TempDir Path projectDir)
            throws IOException {

        // A stale path exempts nothing while reading in the build as though it
        // does, which is the silent no-op this whole family exists to remove.
        writeJavaSource(projectDir, "src/main/java", FRAMEWORK_PACKAGE_PATH,
            "framework-saying-nothing-forbidden");

        var result = runGateExpectingFailure(projectDir, CLOSED_BUT_ALLOWING_A_MISSING_FILE);

        assertThat(result.getOutput())
            .contains("allowWord names a file this gate does not scan");
    }

    @Test
    void failsWhenWordsAreDeclaredWithoutTheRootTheyAreClosedTo(@TempDir Path projectDir)
            throws IOException {

        // A half-declared rule asserts nothing, so it must be rejected where it
        // is written rather than accepted as a rule that can never fire.
        writeJavaSource(projectDir, "src/main/java", FRAMEWORK_PACKAGE_PATH,
            "framework-saying-a-forbidden-word");

        var result = runGateExpectingFailure(projectDir, HALF_DECLARED_RULE);

        assertThat(result.getOutput())
            .contains("forbidWords needs both 'under'");
    }

    @Test
    void failsAgainAfterAPassingRunOnceAWordIsDeclared(@TempDir Path projectDir)
            throws IOException {

        // The declarations are a task input, not just a closure the action
        // reads. If they were not, this second run would be UP-TO-DATE against
        // the first and a newly forbidden word would silently never be looked
        // for.
        writeJavaSource(projectDir, "src/main/java", FRAMEWORK_PACKAGE_PATH,
            "framework-saying-a-forbidden-word");
        runGateExpectingSuccess(projectDir, NO_WORDS_DECLARED);

        var result = runGateExpectingFailure(projectDir, CLOSED_TO_THE_FEATURES_WORDS);

        assertThat(result.getOutput())
            .contains("may not say \"territory\"");
    }

    @Test
    void passesWhenAWordMerelyCarriesAForbiddenOneAsAPrefix(@TempDir Path projectDir)
            throws IOException {

        // The rule that keeps the list usable: words are matched whole, parted
        // at camel humps, so 'blocks' and 'blockade' are their own words. A
        // gate firing on these would train its next reader to allowlist rather
        // than to read.
        writeJavaSource(projectDir, "src/main/java", FRAMEWORK_PACKAGE_PATH,
            "framework-saying-a-word-containing-a-forbidden-one");

        var result = runGateExpectingSuccess(projectDir, CLOSED_TO_THE_FEATURES_WORDS);

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenTheWordIsAllowedInTheFileThatSaysIt(@TempDir Path projectDir)
            throws IOException {

        writeMarkdown(projectDir, "src/main/java", FRAMEWORK_PACKAGE_PATH,
            "framework-readme-saying-an-allowed-word");

        var result = runGateExpectingSuccess(projectDir, CLOSED_BUT_ALLOWING_THE_README);

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenTheFileSitsOutsideTheClosedRoot(@TempDir Path projectDir)
            throws IOException {

        // The feature's own tree is where these words are the subject. A rule
        // that reached it would forbid a package from saying what it is about.
        writeJavaSource(projectDir, "src/main/java", "example/feature",
            "feature-saying-its-own-word");

        var result = runGateExpectingSuccess(projectDir, CLOSED_TO_THE_FEATURES_WORDS);

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenThePackageOnlySharesAPrefixWithTheClosedRoot(@TempDir Path projectDir)
            throws IOException {

        // 'example.frameworkish' is not under 'example.framework': only a
        // whole-segment match may close a package.
        writeJavaSource(projectDir, "src/main/java", "example/frameworkish",
            "prefixed-package-saying-a-forbidden-word");

        var result = runGateExpectingSuccess(projectDir, CLOSED_TO_THE_FEATURES_WORDS);

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenTheBuildDeclaresNoWord(@TempDir Path projectDir)
            throws IOException {

        // Every consumer inherits the gate from the shared conventions, so a
        // build that never says what its vocabulary is must check nothing
        // rather than guess at it.
        writeJavaSource(projectDir, "src/main/java", FRAMEWORK_PACKAGE_PATH,
            "framework-saying-a-forbidden-word");

        var result = runGateExpectingSuccess(projectDir, NO_WORDS_DECLARED);

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenThereIsNoSourceTree(@TempDir Path projectDir)
            throws IOException {

        var result = runGateExpectingSuccess(projectDir, CLOSED_TO_THE_FEATURES_WORDS);

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    // One place owns the block's syntax, so a case that varies the declarations
    // varies only the declarations. Each argument is one whole call.
    private static String buildDeclaration(String... calls) {
        var declaration = new StringBuilder("enforcePackageVocabulary {\n");

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

        return buildRunner(projectDir, declaration).buildAndFail();
    }

    private BuildResult runGateExpectingSuccess(Path projectDir, String declaration)
            throws IOException {

        return buildRunner(projectDir, declaration).build();
    }

    private GradleRunner buildRunner(Path projectDir, String declaration)
            throws IOException {

        // An explicit settings file stops Gradle walking up into a real build.
        Files.writeString(
            projectDir.resolve("settings.gradle"),
            "rootProject.name = 'gate-fixture'\n");

        Files.writeString(
            projectDir.resolve("build.gradle"),
            "apply from: '" + scriptPath() + "'\n" + declaration);

        return GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments("enforcePackageVocabulary");
    }

    // The source root and the package path are both parameters: this gate reads
    // every source set, and which package a file sits in is the whole of what
    // decides whether a rule reaches it.
    private void writeJavaSource(
            Path projectDir, String sourceRoot, String packagePath, String fixture)
            throws IOException {

        writeJavaSource(projectDir, sourceRoot, packagePath, fixture, "Sample.java", "");
    }

    // The appended text is how a case puts one extra line into an otherwise
    // clean fixture, rather than growing a near-duplicate .txt beside it.
    private void writeJavaSource(
            Path projectDir, String sourceRoot, String packagePath, String fixture,
            String fileName, String appendedText)
            throws IOException {

        var dir = projectDir.resolve(sourceRoot + "/" + packagePath);

        Files.createDirectories(dir);
        Files.writeString(dir.resolve(fileName), loadFixture("java/" + fixture) + appendedText);
    }

    private void writeMarkdown(
            Path projectDir, String sourceRoot, String packagePath, String fixture)
            throws IOException {

        var dir = projectDir.resolve(sourceRoot + "/" + packagePath);

        Files.createDirectories(dir);
        Files.writeString(dir.resolve("README.md"), loadFixture("markdown/" + fixture));
    }

    // The gate script path is handed in by the test task so the test does not
    // assume a working directory; forward slashes keep it valid inside the
    // generated build script on Windows.
    private String scriptPath() {
        return System.getProperty("package.vocabulary.gate.script.path").replace('\\', '/');
    }

    // The fixture name carries its language subfolder (java/, kotlin/,
    // markdown/), which resolves under the classpath root the source set
    // exposes for resources.
    private String loadFixture(String name)
            throws IOException {

        try (InputStream in = getClass().getResourceAsStream("/" + name + ".txt")) {
            if (in == null) {
                throw new IllegalStateException("Missing fixture: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
