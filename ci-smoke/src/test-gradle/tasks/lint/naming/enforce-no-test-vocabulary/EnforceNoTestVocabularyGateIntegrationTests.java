import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Drives the shared enforce-no-test-vocabulary gate.
class EnforceNoTestVocabularyGateIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest("enforceNoTestVocabulary", "lint/naming/enforce-no-test-vocabulary");

    private static final String EXTENSION = "enforceNoTestVocabulary";

    private static final String PRODUCTION_PACKAGE_PATH = "/example/production";
    private static final String PRODUCTION_JAVA_ROOT = GateProject.MAIN_JAVA_ROOT + PRODUCTION_PACKAGE_PATH;
    private static final String PRODUCTION_README_PATH = PRODUCTION_JAVA_ROOT + "/README.md";

    // A fixture tree that ships in the production jar: main by source set, a fixture by purpose.
    private static final String FIXTURE_PACKAGE_ROOT = "example.production.testfixtures";

    private static final String MOCK_WORD_FIXTURE = "production-saying-a-mock-word";
    private static final String CLEAN_FIXTURE = "production-saying-nothing-of-tests";
    private static final String ALLOWED_PHRASE_FIXTURE = "markdown/production-readme-saying-an-allowed-phrase";

    private static final String EXEMPTING_THE_FIXTURE_PACKAGE = GateProject.buildConfigurationBlock(
        EXTENSION, "exemptPackage root: '" + FIXTURE_PACKAGE_ROOT + "'");

    @Nested
    class Rule {

        @Test
        void failsWhenProductionCodeSaysABannedWord(@TempDir Path projectDir) {

            var result = GateProject.driving(GATE, projectDir)
                .holdingJavaSample(PRODUCTION_JAVA_ROOT, MOCK_WORD_FIXTURE)
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:8: production sources may not say \"mock\"");
        }

        @Test
        void failsWhenAMethodNameSaysABannedPhrase(@TempDir Path projectDir) {

            // The case a hand sweep misses: the phrase is spelt in camel case, so a text search finds nothing.
            var result = GateProject.driving(GATE, projectDir)
                .holdingJavaSample(PRODUCTION_JAVA_ROOT, "production-saying-a-suite-phrase-in-a-name")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:8: production sources may not say \"unit test\"")
                .contains("Sample.java:8: production sources may not say \"test harness\"");
        }

        @Test
        void failsWhenKotlinCodeSaysABannedWord(@TempDir Path projectDir) {

            var result = GateProject.driving(GATE, projectDir)
                .holdingKotlinSample(GateProject.MAIN_KOTLIN_ROOT + PRODUCTION_PACKAGE_PATH, MOCK_WORD_FIXTURE)
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.kt:7: production sources may not say \"mock\"");
        }

        @Test
        void failsWhenMarkdownBesideTheCodeSaysABannedWord(@TempDir Path projectDir) {

            // The README filed in a package carries no imports for any other gate to judge it by.
            var result = GateProject.driving(GATE, projectDir)
                .holdingFixture(PRODUCTION_README_PATH, "markdown/production-readme-saying-a-mock-word")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("README.md:6: production sources may not say \"mock\"");
        }

        @Test
        void failsWhenProseWrapsABannedPhraseAcrossTwoLines(@TempDir Path projectDir) {

            // Prose wraps where the column runs out, so a per-line scan would miss half of every phrase.
            var result = GateProject.driving(GATE, projectDir)
                .holdingFixture(PRODUCTION_README_PATH, "markdown/production-readme-wrapping-a-suite-phrase")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("README.md:5: production sources may not say \"unit test\"");
        }

        @Test
        void failsWhenProseNamesTheTestFrameworkInItsOwnSpelling(@TempDir Path projectDir) {

            // `JUnit` parts at its camel hump, so only the declared two-word phrase can find it.
            var result = GateProject.driving(GATE, projectDir)
                .holdingText(PRODUCTION_README_PATH, "# Sample\n\nThe seam runs on JUnit.\n")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("README.md:3: production sources may not say \"j unit\"");
        }

        @Test
        void passesWhenProductionProseUsesTheWordsInTheirOwnSense(@TempDir Path projectDir) {

            // 'hit-test', 'Predicate::test', a placeholder stub and 'the name asserts' are correct production
            // writing, which is why the list is tiered rather than flat.
            GateProject.driving(GATE, projectDir)
                .holdingJavaSample(PRODUCTION_JAVA_ROOT, CLEAN_FIXTURE)
                .runExpectingGateToPass();
        }

        @Test
        void passesWhenAWordMerelyCarriesABannedOneAsAPrefix(@TempDir Path projectDir) {

            // Words are matched whole, parted at camel humps, so 'mockup' and 'mockingbird' are their own words.
            GateProject.driving(GATE, projectDir)
                .holdingJavaSample(PRODUCTION_JAVA_ROOT, "production-saying-a-word-containing-a-banned-one")
                .runExpectingGateToPass();
        }
    }

    @Nested
    class Scope {

        @Test
        void failsWhenASourceSetBeyondMainSaysABannedWord(@TempDir Path projectDir) {

            // Only the test trees are exempt unnamed: any other source set ships like main does.
            var result = GateProject.driving(GATE, projectDir)
                .holdingJavaSample("src/utils/java" + PRODUCTION_PACKAGE_PATH, MOCK_WORD_FIXTURE)
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("src/utils/java/example/production/Sample.java:8: production sources may not say");
        }

        @Test
        void failsWhenThePackageOnlySharesAPrefixWithAnExemptRoot(@TempDir Path projectDir) {

            // Only a whole-segment match may exempt a tree, or a rename would quietly widen the hole.
            var result = GateProject.driving(GATE, projectDir)
                .holdingJavaSample(PRODUCTION_JAVA_ROOT + "/testfixturesold", MOCK_WORD_FIXTURE)
                .configuringTheGate(EXEMPTING_THE_FIXTURE_PACKAGE)
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("testfixturesold/Sample.java:8: production sources may not say \"mock\"");
        }

        @Test
        void failsWhenAnExemptRootMatchesOnlyADirectoryInsideThePackage(@TempDir Path projectDir) {

            // A root is read from the package's start, so 'production' is not 'example.production'.
            var result = GateProject.driving(GATE, projectDir)
                .holdingJavaSample(PRODUCTION_JAVA_ROOT, MOCK_WORD_FIXTURE)
                .configuringTheGate(GateProject.buildConfigurationBlock(
                    EXTENSION, "exemptPackage root: 'production'"))
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:8: production sources may not say \"mock\"");
        }

        @Test
        void passesWhenTheWordIsSaidInTheTestSourceSet(@TempDir Path projectDir) {

            GateProject.driving(GATE, projectDir)
                .holdingJavaSample(GateProject.TEST_JAVA_ROOT + PRODUCTION_PACKAGE_PATH, MOCK_WORD_FIXTURE)
                .runExpectingGateToPass();
        }

        @Test
        void passesWhenTheWordIsSaidInTheTestFixturesSourceSet(@TempDir Path projectDir) {

            GateProject.driving(GATE, projectDir)
                .holdingJavaSample(GateProject.TEST_FIXTURES_JAVA_ROOT + PRODUCTION_PACKAGE_PATH, MOCK_WORD_FIXTURE)
                .runExpectingGateToPass();
        }

        @Test
        void passesWhenTheWordIsSaidInADeclaredExemptSourceSet(@TempDir Path projectDir) {

            GateProject.driving(GATE, projectDir)
                .holdingJavaSample("src/fixtures/java" + PRODUCTION_PACKAGE_PATH, MOCK_WORD_FIXTURE)
                .configuringTheGate(GateProject.buildConfigurationBlock(EXTENSION, "exemptSourceSet name: 'fixtures'"))
                .runExpectingGateToPass();
        }

        @Test
        void passesWhenTheFileSitsUnderADeclaredExemptPackage(@TempDir Path projectDir) {

            GateProject.driving(GATE, projectDir)
                .holdingJavaSample(PRODUCTION_JAVA_ROOT + "/testfixtures", MOCK_WORD_FIXTURE)
                .configuringTheGate(EXEMPTING_THE_FIXTURE_PACKAGE)
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
        void passesWhenAllowWordLetsEachEntryThroughTheFileThatSaysIt(@TempDir Path projectDir) {

            // A README documenting a shipped fixture has to say what the fixture is for.
            GateProject.driving(GATE, projectDir)
                .holdingFixture(PRODUCTION_README_PATH, ALLOWED_PHRASE_FIXTURE)
                .configuringTheGate(GateProject.buildConfigurationBlock(EXTENSION,
                    "allowWord word: 'mocking', inFile: '" + PRODUCTION_README_PATH + "'",
                    "allowWord word: 'tests can', inFile: '" + PRODUCTION_README_PATH + "'"))
                .runExpectingGateToPass();
        }

        @Test
        void passesWhenAllowWordsLetsSeveralEntriesThroughOneFile(@TempDir Path projectDir) {

            GateProject.driving(GATE, projectDir)
                .holdingFixture(PRODUCTION_README_PATH, ALLOWED_PHRASE_FIXTURE)
                .configuringTheGate(GateProject.buildConfigurationBlock(EXTENSION,
                    "allowWords words: ['mocking', 'tests can'], inFile: '" + PRODUCTION_README_PATH + "'"))
                .runExpectingGateToPass();
        }

        @Test
        void failsWhenAnAllowanceCoversOnlyOneOfTheEntriesAFileSays(@TempDir Path projectDir) {

            // An allowance is per entry, so the phrase it does not name is still banned in the same file.
            var result = GateProject.driving(GATE, projectDir)
                .holdingFixture(PRODUCTION_README_PATH, ALLOWED_PHRASE_FIXTURE)
                .configuringTheGate(GateProject.buildConfigurationBlock(EXTENSION,
                    "allowWord word: 'mocking', inFile: '" + PRODUCTION_README_PATH + "'"))
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("README.md:7: production sources may not say \"tests can\"")
                .doesNotContain("may not say \"mocking\"");
        }

        @Test
        void failsWhenAnAllowanceNamesAFileTheGateDoesNotScan(@TempDir Path projectDir) {

            // A stale path exempts nothing while reading in the build as though it does.
            var result = GateProject.driving(GATE, projectDir)
                .holdingJavaSample(PRODUCTION_JAVA_ROOT, CLEAN_FIXTURE)
                .configuringTheGate(GateProject.buildConfigurationBlock(EXTENSION,
                    "allowWord word: 'mock', inFile: '" + PRODUCTION_JAVA_ROOT + "/GONE.md'"))
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("GONE.md: allowWord names a file this gate does not scan, so it allows nothing: \"mock\"");
        }

        @Test
        void failsWhenAnAllowanceNamesAFileInAnAlreadyExemptTree(@TempDir Path projectDir) {

            // The file exists, so the path looks live, but the tree around it is exempt whole.
            var testSamplePath = GateProject.TEST_JAVA_ROOT + PRODUCTION_PACKAGE_PATH + "/Sample.java";

            var result = GateProject.driving(GATE, projectDir)
                .holdingJavaSample(GateProject.TEST_JAVA_ROOT + PRODUCTION_PACKAGE_PATH, MOCK_WORD_FIXTURE)
                .configuringTheGate(GateProject.buildConfigurationBlock(EXTENSION,
                    "allowWord word: 'mock', inFile: '" + testSamplePath + "'"))
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java: allowWord names a file in an exempt tree, which is already allowed");
        }

        @Test
        void failsWhenAnAllowanceNamesNoBannedEntry(@TempDir Path projectDir) {

            // A misspelt entry allows nothing, while the real entry it meant stays banned.
            var result = GateProject.driving(GATE, projectDir)
                .holdingFixture(PRODUCTION_README_PATH, ALLOWED_PHRASE_FIXTURE)
                .configuringTheGate(GateProject.buildConfigurationBlock(EXTENSION,
                    "allowWords words: ['moking', 'tests can'], inFile: '" + PRODUCTION_README_PATH + "'"))
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("README.md: allowWord names no banned entry, so it allows nothing: \"moking\"");
        }

        @Test
        void passesWhenAllowWordsNamesASingleEntryWithoutAList(@TempDir Path projectDir) {

            // One entry is the common case, so it is not spelt out letter by letter as a list of one.
            GateProject.driving(GATE, projectDir)
                .holdingFixture(PRODUCTION_README_PATH, ALLOWED_PHRASE_FIXTURE)
                .configuringTheGate(GateProject.buildConfigurationBlock(EXTENSION,
                    "allowWords words: 'mocking', inFile: '" + PRODUCTION_README_PATH + "'",
                    "allowWord word: 'tests can', inFile: '" + PRODUCTION_README_PATH + "'"))
                .runExpectingGateToPass();
        }

        @Test
        void failsWhenAnExemptSourceSetNamesNoDirectory(@TempDir Path projectDir) {

            // A renamed tree leaves its exemption behind, exempting nothing while reading as though it does.
            var result = GateProject.driving(GATE, projectDir)
                .holdingJavaSample(PRODUCTION_JAVA_ROOT, CLEAN_FIXTURE)
                .configuringTheGate(GateProject.buildConfigurationBlock(EXTENSION, "exemptSourceSet name: 'gone'"))
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("src/gone: exemptSourceSet names no source set directory, so it exempts nothing");
        }

        @Test
        void failsWhenAnExemptPackageHoldsNoScannedFile(@TempDir Path projectDir) {

            var result = GateProject.driving(GATE, projectDir)
                .holdingJavaSample(PRODUCTION_JAVA_ROOT, CLEAN_FIXTURE)
                .configuringTheGate(EXEMPTING_THE_FIXTURE_PACKAGE)
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains(FIXTURE_PACKAGE_ROOT + ": exemptPackage names no package holding a scanned file");
        }

        @Test
        void failsWhenAnExemptSourceSetIsDeclaredWithoutAName(@TempDir Path projectDir) {

            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(GateProject.buildConfigurationBlock(
                    EXTENSION, "exemptSourceSet directory: 'fixtures'"))
                .runExpectingFailure();

            assertThat(result.getOutput())
                .contains("exemptSourceSet needs 'name'");
        }

        @Test
        void failsWhenAnExemptPackageIsDeclaredWithoutARoot(@TempDir Path projectDir) {

            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(GateProject.buildConfigurationBlock(
                    EXTENSION, "exemptPackage packageRoot: '" + FIXTURE_PACKAGE_ROOT + "'"))
                .runExpectingFailure();

            assertThat(result.getOutput())
                .contains("exemptPackage needs 'root'");
        }

        @Test
        void failsWhenAnAllowanceIsDeclaredWithoutAFile(@TempDir Path projectDir) {

            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(GateProject.buildConfigurationBlock(EXTENSION, "allowWord word: 'mock'"))
                .runExpectingFailure();

            assertThat(result.getOutput())
                .contains("allowWord needs both 'word'");
        }

        @Test
        void failsWhenAllowWordsIsDeclaredWithoutAFile(@TempDir Path projectDir) {

            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(GateProject.buildConfigurationBlock(EXTENSION, "allowWords words: ['mock']"))
                .runExpectingFailure();

            assertThat(result.getOutput())
                .contains("allowWords needs both 'words'");
        }
    }

    @Nested
    class UpToDateBehaviour {

        @Test
        void passesUpToDateWhenOnlyAnExemptTreeChanged(@TempDir Path projectDir) {

            // An exempt tree is not read, so editing a test must not re-run the gate.
            var project = GateProject.driving(GATE, projectDir)
                .holdingJavaSample(PRODUCTION_JAVA_ROOT, CLEAN_FIXTURE)
                .holdingJavaSample(GateProject.TEST_JAVA_ROOT + PRODUCTION_PACKAGE_PATH, MOCK_WORD_FIXTURE);

            project.runExpectingGateToPass();
            project.holdingJavaSample(GateProject.TEST_JAVA_ROOT + PRODUCTION_PACKAGE_PATH, CLEAN_FIXTURE);

            assertThat(project.runExpectingSuccess().task(GATE.taskPath()).getOutcome())
                .isEqualTo(TaskOutcome.UP_TO_DATE);
        }
    }
}
