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

// Integration test for the shared enforce-no-test-vocabulary gate: it applies
// the real gate script into a throwaway project and runs the task, asserting
// the build outcome and the message a developer sees. Lives in the Common-Java
// ci-smoke project - the gate script is shared by every consumer, so the tests
// live here beside it. Fixtures load from .txt files under java/, kotlin/ and
// markdown/ subfolders so the gate never sees a fixture as real source here.
//
// The fixture package is 'example.production' rather than any real consumer's,
// because the gate owns the word list and this repo knows no consumer's tree.
// Default package: the grouping folder is the source root, and its kebab name
// cannot be a Java package.
class EnforceNoTestVocabularyGateIntegrationTests {

    private static final String TASK_PATH = ":enforceNoTestVocabulary";

    private static final String PRODUCTION_PACKAGE_PATH = "example/production";

    // A fixture tree that ships in the production jar, which is the case
    // exemptPackage exists for: main by source set, fixture by purpose.
    private static final String FIXTURE_PACKAGE_ROOT = "example.production.testfixtures";
    private static final String FIXTURE_PACKAGE_PATH = "example/production/testfixtures";

    // Shares every segment but the last with the exempt root, so only a
    // whole-segment match can tell the two apart.
    private static final String PREFIXED_PACKAGE_PATH = "example/production/testfixturesold";

    private static final String ALLOWED_README_PATH =
        "src/main/java/" + PRODUCTION_PACKAGE_PATH + "/README.md";

    private static final String NOTHING_EXEMPT = "";

    private static final String EXEMPTING_THE_FIXTURE_SOURCE_SET =
        buildDeclaration("exemptSourceSet name: 'fixtures'");

    private static final String EXEMPTING_THE_FIXTURE_PACKAGE =
        buildDeclaration("exemptPackage root: '" + FIXTURE_PACKAGE_ROOT + "'");

    // Both entries the fixture README says, since an allowance is per entry and
    // the README leaks a single word and a phrase.
    private static final String ALLOWING_THE_README = buildDeclaration(
        "allowWord word: 'mocking', inFile: '" + ALLOWED_README_PATH + "'",
        "allowWord word: 'tests can', inFile: '" + ALLOWED_README_PATH + "'");

    // An allowance for a file no source tree holds - the shape a path left
    // behind by a rename or a move takes.
    private static final String ALLOWING_A_MISSING_FILE = buildDeclaration(
        "allowWord word: 'mock', inFile: 'src/main/java/"
            + PRODUCTION_PACKAGE_PATH + "/GONE.md'");

    // An allowance pointing into a tree that is already exempt whole, so it
    // answers no rule.
    private static final String ALLOWING_A_FILE_IN_AN_EXEMPT_TREE = buildDeclaration(
        "allowWord word: 'mock', inFile: 'src/test/java/"
            + PRODUCTION_PACKAGE_PATH + "/Sample.java'");

    // Declarations naming neither the source set nor the package they exempt -
    // the shape a misspelt or forgotten key takes.
    private static final String HALF_DECLARED_SOURCE_SET =
        buildDeclaration("exemptSourceSet directory: 'fixtures'");

    private static final String HALF_DECLARED_PACKAGE =
        buildDeclaration("exemptPackage packageRoot: '" + FIXTURE_PACKAGE_ROOT + "'");

    @Test
    void failsWhenProductionCodeSaysABannedWord(@TempDir Path projectDir)
            throws IOException {

        writeJavaSource(projectDir, "src/main/java", PRODUCTION_PACKAGE_PATH,
            "production-saying-a-mock-word");

        var result = runGateExpectingFailure(projectDir, NOTHING_EXEMPT);

        assertThat(result.getOutput())
            .contains("production sources may not say \"mock\"");
    }

    @Test
    void failsWhenAMethodNameSaysABannedPhrase(@TempDir Path projectDir)
            throws IOException {

        // The case a hand sweep misses, and the reason the phrase tier is
        // matched by words rather than by text: the words are spelt in camel
        // case, so searching for the phrase as prose finds nothing.
        writeJavaSource(projectDir, "src/main/java", PRODUCTION_PACKAGE_PATH,
            "production-saying-a-suite-phrase-in-a-name");

        var result = runGateExpectingFailure(projectDir, NOTHING_EXEMPT);

        assertThat(result.getOutput())
            .contains("may not say \"unit test\"");
    }

    @Test
    void failsWhenMarkdownBesideTheCodeSaysABannedWord(@TempDir Path projectDir)
            throws IOException {

        // The README filed in the package is the surface a reader of a shipped
        // API reads first, and it carries no imports for any other gate to
        // judge it by.
        writeMarkdown(projectDir, "src/main/java", PRODUCTION_PACKAGE_PATH,
            "production-readme-saying-a-mock-word");

        var result = runGateExpectingFailure(projectDir, NOTHING_EXEMPT);

        assertThat(result.getOutput())
            .contains("may not say \"mock\"");
    }

    @Test
    void failsWhenProseWrapsABannedPhraseAcrossTwoLines(@TempDir Path projectDir)
            throws IOException {

        // Prose wraps where the column runs out rather than where the phrase
        // ends, so a per-line scan would miss half the prose uses of any
        // multi-word entry.
        writeMarkdown(projectDir, "src/main/java", PRODUCTION_PACKAGE_PATH,
            "production-readme-wrapping-a-suite-phrase");

        var result = runGateExpectingFailure(projectDir, NOTHING_EXEMPT);

        assertThat(result.getOutput())
            .contains("may not say \"unit test\"");
    }

    @Test
    void failsWhenKotlinCodeSaysABannedWord(@TempDir Path projectDir)
            throws IOException {

        // Kotlin is half the production surface across the consuming mods, and
        // the leak arrives through KDoc exactly as it does through Javadoc.
        var dir = projectDir.resolve("src/main/kotlin/" + PRODUCTION_PACKAGE_PATH);

        Files.createDirectories(dir);
        Files.writeString(
            dir.resolve("Sample.kt"),
            loadFixture("kotlin/production-saying-a-mock-word"));

        var result = runGateExpectingFailure(projectDir, NOTHING_EXEMPT);

        assertThat(result.getOutput())
            .contains("may not say \"mock\"");
    }

    @Test
    void failsWhenASourceSetBeyondMainSaysABannedWord(@TempDir Path projectDir)
            throws IOException {

        // Only src/test and src/testFixtures are exempt without being named. A
        // dev-tooling or utility source set ships like any other, so it answers
        // to the rule until the build says otherwise.
        writeJavaSource(projectDir, "src/utils/java", PRODUCTION_PACKAGE_PATH,
            "production-saying-a-mock-word");

        var result = runGateExpectingFailure(projectDir, NOTHING_EXEMPT);

        assertThat(result.getOutput())
            .contains("may not say \"mock\"");
    }

    @Test
    void failsWhenThePackageOnlySharesAPrefixWithAnExemptRoot(@TempDir Path projectDir)
            throws IOException {

        // 'example.production.testfixturesold' is not under
        // 'example.production.testfixtures': only a whole-segment match may
        // exempt a tree, or a rename would quietly widen the hole.
        writeJavaSource(projectDir, "src/main/java", PREFIXED_PACKAGE_PATH,
            "production-saying-a-mock-word");

        var result = runGateExpectingFailure(projectDir, EXEMPTING_THE_FIXTURE_PACKAGE);

        assertThat(result.getOutput())
            .contains("may not say \"mock\"");
    }

    @Test
    void failsWhenAnAllowanceNamesAFileTheGateDoesNotScan(@TempDir Path projectDir)
            throws IOException {

        // A stale path exempts nothing while reading in the build as though it
        // does, which is the silent no-op this whole family exists to remove.
        writeJavaSource(projectDir, "src/main/java", PRODUCTION_PACKAGE_PATH,
            "production-saying-nothing-of-tests");

        var result = runGateExpectingFailure(projectDir, ALLOWING_A_MISSING_FILE);

        assertThat(result.getOutput())
            .contains("allowWord names a file this gate does not scan");
    }

    @Test
    void failsWhenAnAllowanceNamesAFileInAnAlreadyExemptTree(@TempDir Path projectDir)
            throws IOException {

        // The other half of the dead-config check, and the one a reader is
        // likeliest to write: the file exists, so the path looks live, but the
        // tree around it is exempt whole and the allowance decides nothing.
        writeJavaSource(projectDir, "src/test/java", PRODUCTION_PACKAGE_PATH,
            "production-saying-a-mock-word");

        var result = runGateExpectingFailure(projectDir, ALLOWING_A_FILE_IN_AN_EXEMPT_TREE);

        assertThat(result.getOutput())
            .contains("names a file in an exempt tree, which is already allowed");
    }

    @Test
    void failsWhenAnExemptSourceSetIsDeclaredWithoutAName(@TempDir Path projectDir)
            throws IOException {

        // A half-declared exemption exempts nothing, so it must be rejected
        // where it is written rather than accepted as a rule that never fires.
        writeJavaSource(projectDir, "src/main/java", PRODUCTION_PACKAGE_PATH,
            "production-saying-nothing-of-tests");

        var result = runGateExpectingFailure(projectDir, HALF_DECLARED_SOURCE_SET);

        assertThat(result.getOutput())
            .contains("exemptSourceSet needs 'name'");
    }

    @Test
    void failsWhenAnExemptPackageIsDeclaredWithoutARoot(@TempDir Path projectDir)
            throws IOException {

        writeJavaSource(projectDir, "src/main/java", PRODUCTION_PACKAGE_PATH,
            "production-saying-nothing-of-tests");

        var result = runGateExpectingFailure(projectDir, HALF_DECLARED_PACKAGE);

        assertThat(result.getOutput())
            .contains("exemptPackage needs 'root'");
    }

    @Test
    void passesWhenProductionProseUsesTheWordsInTheirOwnSense(@TempDir Path projectDir)
            throws IOException {

        // The rule that keeps the list usable, and the reason it is tiered
        // rather than flat: 'hit-test', 'Predicate::test', a placeholder stub
        // and 'the name asserts' are all correct production writing. A gate
        // firing on these would train its next reader to allowlist rather than
        // to read.
        writeJavaSource(projectDir, "src/main/java", PRODUCTION_PACKAGE_PATH,
            "production-saying-nothing-of-tests");

        var result = runGateExpectingSuccess(projectDir, NOTHING_EXEMPT);

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenAWordMerelyCarriesABannedOneAsAPrefix(@TempDir Path projectDir)
            throws IOException {

        // Words are matched whole, parted at camel humps, so 'mockup' and
        // 'mockingbird' are their own words.
        writeJavaSource(projectDir, "src/main/java", PRODUCTION_PACKAGE_PATH,
            "production-saying-a-word-containing-a-banned-one");

        var result = runGateExpectingSuccess(projectDir, NOTHING_EXEMPT);

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenTheWordIsSaidInTheTestSourceSet(@TempDir Path projectDir)
            throws IOException {

        // The tree the vocabulary belongs to, exempt without a consumer saying
        // so - otherwise every project would open with the same declaration.
        writeJavaSource(projectDir, "src/test/java", PRODUCTION_PACKAGE_PATH,
            "production-saying-a-mock-word");

        var result = runGateExpectingSuccess(projectDir, NOTHING_EXEMPT);

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenTheWordIsSaidInADeclaredExemptSourceSet(@TempDir Path projectDir)
            throws IOException {

        writeJavaSource(projectDir, "src/fixtures/java", PRODUCTION_PACKAGE_PATH,
            "production-saying-a-mock-word");

        var result = runGateExpectingSuccess(projectDir, EXEMPTING_THE_FIXTURE_SOURCE_SET);

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenTheFileSitsUnderADeclaredExemptPackage(@TempDir Path projectDir)
            throws IOException {

        // The case a source-set rule cannot express: fixtures that ship in the
        // production jar are main by source set and only the package says what
        // they are for.
        writeJavaSource(projectDir, "src/main/java", FIXTURE_PACKAGE_PATH,
            "production-saying-a-mock-word");

        var result = runGateExpectingSuccess(projectDir, EXEMPTING_THE_FIXTURE_PACKAGE);

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenTheEntryIsAllowedInTheFileThatSaysIt(@TempDir Path projectDir)
            throws IOException {

        // A README documenting a shipped fixture has to say what the fixture is
        // for, and what it is for is stated in the words of a suite.
        writeMarkdown(projectDir, "src/main/java", PRODUCTION_PACKAGE_PATH,
            "production-readme-saying-an-allowed-phrase");

        var result = runGateExpectingSuccess(projectDir, ALLOWING_THE_README);

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
        var declaration = new StringBuilder("enforceNoTestVocabulary {\n");

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
            .withArguments("enforceNoTestVocabulary");
    }

    // The source root and the package path are both parameters: which source
    // set a file sits in and which package it declares are the two things that
    // decide whether the rule reaches it.
    private void writeJavaSource(
            Path projectDir, String sourceRoot, String packagePath, String fixture)
            throws IOException {

        var dir = projectDir.resolve(sourceRoot + "/" + packagePath);

        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Sample.java"), loadFixture("java/" + fixture));
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
        return System.getProperty("test.vocabulary.gate.script.path").replace('\\', '/');
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
