import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Drives the shared enforce-no-test-vocabulary gate.
class EnforceNoTestVocabularyGateIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest(
            "enforceNoTestVocabulary",
            "lint/naming/enforce-no-test-vocabulary");

    private static final String EXTENSION = "enforceNoTestVocabulary";

    private static final String PRODUCTION_PACKAGE_PATH = "/example/production";
    private static final String PRODUCTION_JAVA_ROOT = GateProject.MAIN_JAVA_ROOT + PRODUCTION_PACKAGE_PATH;
    private static final String PRODUCTION_README_PATH = PRODUCTION_JAVA_ROOT + "/README.md";

    // A fixture tree that ships in the production jar: main by source set, a fixture by purpose.
    private static final String FIXTURE_PACKAGE_ROOT = "example.production.testfixtures";

    private static final String MOCK_WORD_FIXTURE = "production-saying-a-mock-word";
    private static final String CLEAN_FIXTURE = "production-saying-nothing-of-tests";
    private static final String ALLOWED_PHRASE_FIXTURE = "markdown/production-readme-saying-an-allowed-phrase";

    private static final String EXEMPTING_THE_FIXTURE_PACKAGE =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "exemptPackage root: '" +
                FIXTURE_PACKAGE_ROOT +
                "'");

    @Nested
    class Rule {

        @Test
        void failsWhenProductionCodeSaysABannedWord(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "Sample.java:8: production sources may not say \"mock\"";

            var project = GateProject
                .driving(GATE, projectDir)
                .holdingJavaSample(
                    PRODUCTION_JAVA_ROOT,
                    MOCK_WORD_FIXTURE);

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // The case a hand sweep misses: the phrase is spelt in camel case, so a text search finds nothing.
        @Test
        void failsWhenAMethodNameSaysABannedPhrase(@TempDir Path projectDir) {

            // ARRANGE
            var expectedUnitTestMessage = "Sample.java:8: production sources may not say \"unit test\"";
            var expectedTestHarnessMessage = "Sample.java:8: production sources may not say \"test harness\"";

            var project = GateProject
                .driving(GATE, projectDir)
                .holdingJavaSample(
                    PRODUCTION_JAVA_ROOT,
                    "production-saying-a-suite-phrase-in-a-name");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedUnitTestMessage)
                .contains(expectedTestHarnessMessage);
        }

        @Test
        void failsWhenKotlinCodeSaysABannedWord(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "Sample.kt:7: production sources may not say \"mock\"";

            var project = GateProject
                .driving(GATE, projectDir)
                .holdingKotlinSample(
                    GateProject.MAIN_KOTLIN_ROOT + PRODUCTION_PACKAGE_PATH,
                    MOCK_WORD_FIXTURE);

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // The README filed in a package carries no imports for any other gate to judge it by.
        @Test
        void failsWhenMarkdownBesideTheCodeSaysABannedWord(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "README.md:6: production sources may not say \"mock\"";

            var project = GateProject
                .driving(GATE, projectDir)
                .holdingFixture(
                    PRODUCTION_README_PATH,
                    "markdown/production-readme-saying-a-mock-word");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // Prose wraps where the column runs out, so a per-line scan would miss half of every phrase.
        @Test
        void failsWhenProseWrapsABannedPhraseAcrossTwoLines(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "README.md:5: production sources may not say \"unit test\"";

            var project = GateProject
                .driving(GATE, projectDir)
                .holdingFixture(
                    PRODUCTION_README_PATH,
                    "markdown/production-readme-wrapping-a-suite-phrase");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // `JUnit` parts at its camel hump, so only the declared two-word phrase can find it.
        @Test
        void failsWhenProseNamesTheTestFrameworkInItsOwnSpelling(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "README.md:3: production sources may not say \"j unit\"";

            var project = GateProject
                .driving(GATE, projectDir)
                .holdingText(
                    PRODUCTION_README_PATH,
                    "# Sample\n\nThe seam runs on JUnit.\n");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // 'hit-test', 'Predicate::test', a placeholder stub and 'the name asserts' are correct production
        // writing, which is why the list is tiered rather than flat.
        @Test
        void passesWhenProductionProseUsesTheWordsInTheirOwnSense(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .holdingJavaSample(
                    PRODUCTION_JAVA_ROOT,
                    CLEAN_FIXTURE);

            // ACT
            project.runExpectingGateToPass();
        }

        // Words are matched whole, parted at camel humps, so 'mockup' and 'mockingbird' are their own words.
        @Test
        void passesWhenAWordMerelyCarriesABannedOneAsAPrefix(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .holdingJavaSample(
                    PRODUCTION_JAVA_ROOT,
                    "production-saying-a-word-containing-a-banned-one");

            // ACT
            project.runExpectingGateToPass();
        }
    }

    @Nested
    class Scope {

        // Only the test trees are exempt unnamed: any other source set ships like main does.
        @Test
        void failsWhenASourceSetBeyondMainSaysABannedWord(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "src/utils/java/example/production/Sample.java:8: production sources may not say";

            var project = GateProject
                .driving(GATE, projectDir)
                .holdingJavaSample(
                    "src/utils/java" + PRODUCTION_PACKAGE_PATH,
                    MOCK_WORD_FIXTURE);

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // Only a whole-segment match may exempt a tree, or a rename would quietly widen the hole.
        @Test
        void failsWhenThePackageOnlySharesAPrefixWithAnExemptRoot(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "testfixturesold/Sample.java:8: production sources may not say \"mock\"";

            var project = GateProject
                .driving(GATE, projectDir)
                .holdingJavaSample(
                    PRODUCTION_JAVA_ROOT + "/testfixturesold",
                    MOCK_WORD_FIXTURE)
                .configuringTheGate(EXEMPTING_THE_FIXTURE_PACKAGE);

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // A root is read from the package's start, so 'production' is not 'example.production'.
        @Test
        void failsWhenAnExemptRootMatchesOnlyADirectoryInsideThePackage(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "Sample.java:8: production sources may not say \"mock\"";

            var project = GateProject
                .driving(GATE, projectDir)
                .holdingJavaSample(
                    PRODUCTION_JAVA_ROOT,
                    MOCK_WORD_FIXTURE)
                .configuringTheGate(GateProject.buildConfigurationBlock(
                    EXTENSION,
                    "exemptPackage root: 'production'"));

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        @Test
        void passesWhenTheWordIsSaidInTheTestSourceSet(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .holdingJavaSample(
                    GateProject.TEST_JAVA_ROOT + PRODUCTION_PACKAGE_PATH,
                    MOCK_WORD_FIXTURE);

            // ACT
            project.runExpectingGateToPass();
        }

        @Test
        void passesWhenTheWordIsSaidInTheTestFixturesSourceSet(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .holdingJavaSample(
                    GateProject.TEST_FIXTURES_JAVA_ROOT + PRODUCTION_PACKAGE_PATH,
                    MOCK_WORD_FIXTURE);

            // ACT
            project.runExpectingGateToPass();
        }

        // A suite tree a project adds is test code by its name, as Gradle's own two are.
        @Test
        void passesWhenTheWordIsSaidInAnAddedTestSourceSet(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .holdingJavaSample(
                    "src/integrationTest/java" + PRODUCTION_PACKAGE_PATH,
                    MOCK_WORD_FIXTURE);

            // ACT
            project.runExpectingGateToPass();
        }

        // A name is read as a test tree only where 'test' opens one of its words, so a tree merely
        // holding the letters ships like main does.
        @Test
        void failsWhenASourceSetOnlyHoldsTheLettersOfTest(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage =
                "src/contest/java/example/production/Sample.java:8: production sources may not say";

            var project = GateProject
                .driving(GATE, projectDir)
                .holdingJavaSample(
                    "src/contest/java" + PRODUCTION_PACKAGE_PATH,
                    MOCK_WORD_FIXTURE);

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        @Test
        void passesWhenTheWordIsSaidInADeclaredExemptSourceSet(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .holdingJavaSample(
                    "src/fixtures/java" + PRODUCTION_PACKAGE_PATH,
                    MOCK_WORD_FIXTURE)
                .configuringTheGate(GateProject.buildConfigurationBlock(
                    EXTENSION,
                    "exemptSourceSet name: 'fixtures'"));

            // ACT
            project.runExpectingGateToPass();
        }

        @Test
        void passesWhenTheFileSitsUnderADeclaredExemptPackage(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .holdingJavaSample(
                    PRODUCTION_JAVA_ROOT + "/testfixtures",
                    MOCK_WORD_FIXTURE)
                .configuringTheGate(EXEMPTING_THE_FIXTURE_PACKAGE);

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

        // A README documenting a shipped fixture has to say what the fixture is for.
        @Test
        void passesWhenAllowWordLetsEachEntryThroughTheFileThatSaysIt(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .holdingFixture(
                    PRODUCTION_README_PATH,
                    ALLOWED_PHRASE_FIXTURE)
                .configuringTheGate(GateProject.buildConfigurationBlock(
                    EXTENSION,
                    "allowWord word: 'mocking', inFile: '" + PRODUCTION_README_PATH + "'",
                    "allowWord word: 'tests can', inFile: '" + PRODUCTION_README_PATH + "'"));

            // ACT
            project.runExpectingGateToPass();
        }

        @Test
        void passesWhenAllowWordsLetsSeveralEntriesThroughOneFile(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .holdingFixture(
                    PRODUCTION_README_PATH,
                    ALLOWED_PHRASE_FIXTURE)
                .configuringTheGate(GateProject.buildConfigurationBlock(
                    EXTENSION,
                    "allowWords words: ['mocking', 'tests can'], inFile: '" + PRODUCTION_README_PATH + "'"));

            // ACT
            project.runExpectingGateToPass();
        }

        // An allowance is per entry, so the phrase it does not name is still banned in the same file.
        @Test
        void failsWhenAnAllowanceCoversOnlyOneOfTheEntriesAFileSays(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "README.md:7: production sources may not say \"tests can\"";
            var unexpectedMessage = "may not say \"mocking\"";

            var project = GateProject
                .driving(GATE, projectDir)
                .holdingFixture(
                    PRODUCTION_README_PATH,
                    ALLOWED_PHRASE_FIXTURE)
                .configuringTheGate(GateProject.buildConfigurationBlock(
                    EXTENSION,
                    "allowWord word: 'mocking', inFile: '" + PRODUCTION_README_PATH + "'"));

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage)
                .doesNotContain(unexpectedMessage);
        }

        // A stale path exempts nothing while reading in the build as though it does.
        @Test
        void failsWhenAnAllowanceNamesAFileTheGateDoesNotScan(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage =
                "GONE.md: allowWord names a file this gate does not scan, so it allows nothing: \"mock\"";

            var project = GateProject
                .driving(GATE, projectDir)
                .holdingJavaSample(
                    PRODUCTION_JAVA_ROOT,
                    CLEAN_FIXTURE)
                .configuringTheGate(GateProject.buildConfigurationBlock(
                    EXTENSION,
                    "allowWord word: 'mock', inFile: '" + PRODUCTION_JAVA_ROOT + "/GONE.md'"));

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // The file exists, so the path looks live, but the tree around it is exempt whole.
        @Test
        void failsWhenAnAllowanceNamesAFileInAnAlreadyExemptTree(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage =
                "Sample.java: allowWord names a file in an exempt tree, which is already allowed";

            var testSamplePath = GateProject.TEST_JAVA_ROOT + PRODUCTION_PACKAGE_PATH + "/Sample.java";

            var project = GateProject
                .driving(GATE, projectDir)
                .holdingJavaSample(
                    GateProject.TEST_JAVA_ROOT + PRODUCTION_PACKAGE_PATH,
                    MOCK_WORD_FIXTURE)
                .configuringTheGate(GateProject.buildConfigurationBlock(
                    EXTENSION,
                    "allowWord word: 'mock', inFile: '" + testSamplePath + "'"));

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // A misspelt entry allows nothing, while the real entry it meant stays banned.
        @Test
        void failsWhenAnAllowanceNamesNoBannedEntry(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "README.md: allowWord names no banned entry, so it allows nothing: \"moking\"";

            var project = GateProject
                .driving(GATE, projectDir)
                .holdingFixture(
                    PRODUCTION_README_PATH,
                    ALLOWED_PHRASE_FIXTURE)
                .configuringTheGate(GateProject.buildConfigurationBlock(
                    EXTENSION,
                    "allowWords words: ['moking', 'tests can'], inFile: '" + PRODUCTION_README_PATH + "'"));

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // One entry is the common case, so it is not spelt out letter by letter as a list of one.
        @Test
        void passesWhenAllowWordsNamesASingleEntryWithoutAList(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .holdingFixture(
                    PRODUCTION_README_PATH,
                    ALLOWED_PHRASE_FIXTURE)
                .configuringTheGate(GateProject.buildConfigurationBlock(
                    EXTENSION,
                    "allowWords words: 'mocking', inFile: '" + PRODUCTION_README_PATH + "'",
                    "allowWord word: 'tests can', inFile: '" + PRODUCTION_README_PATH + "'"));

            // ACT
            project.runExpectingGateToPass();
        }

        // A renamed tree leaves its exemption behind, exempting nothing while reading as though it does.
        @Test
        void failsWhenAnExemptSourceSetNamesNoDirectory(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "src/gone: exemptSourceSet names no source set directory, so it exempts nothing";

            var project = GateProject
                .driving(GATE, projectDir)
                .holdingJavaSample(
                    PRODUCTION_JAVA_ROOT,
                    CLEAN_FIXTURE)
                .configuringTheGate(GateProject.buildConfigurationBlock(
                    EXTENSION,
                    "exemptSourceSet name: 'gone'"));

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        @Test
        void failsWhenAnExemptPackageHoldsNoScannedFile(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = FIXTURE_PACKAGE_ROOT + ": exemptPackage names no package holding a scanned file";

            var project = GateProject
                .driving(GATE, projectDir)
                .holdingJavaSample(
                    PRODUCTION_JAVA_ROOT,
                    CLEAN_FIXTURE)
                .configuringTheGate(EXEMPTING_THE_FIXTURE_PACKAGE);

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        @Test
        void failsWhenAnExemptSourceSetIsDeclaredWithoutAName(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "exemptSourceSet needs 'name'";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(GateProject.buildConfigurationBlock(
                    EXTENSION,
                    "exemptSourceSet name: ''"));

            // ACT
            var actualResult = project.runExpectingFailure();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // The shape a misspelt 'name:' takes, which would otherwise be dropped in silence.
        @Test
        void failsWhenAnExemptSourceSetNamesAKeyItDoesNotTake(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "exemptSourceSet does not take 'directory'; it takes 'name'";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(GateProject.buildConfigurationBlock(
                    EXTENSION,
                    "exemptSourceSet directory: 'fixtures'"));

            // ACT
            var actualResult = project.runExpectingFailure();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        @Test
        void failsWhenAnExemptPackageIsDeclaredWithoutARoot(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "exemptPackage needs 'root'";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(GateProject.buildConfigurationBlock(
                    EXTENSION,
                    "exemptPackage root: ''"));

            // ACT
            var actualResult = project.runExpectingFailure();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        @Test
        void failsWhenAnExemptPackageNamesAKeyItDoesNotTake(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "exemptPackage does not take 'packageRoot'; it takes 'root'";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(GateProject.buildConfigurationBlock(
                    EXTENSION,
                    "exemptPackage packageRoot: '" + FIXTURE_PACKAGE_ROOT + "'"));

            // ACT
            var actualResult = project.runExpectingFailure();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        @Test
        void failsWhenAnAllowanceNamesAKeyItDoesNotTake(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "allowWords does not take 'word'; it takes 'words', 'inFile'";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(GateProject.buildConfigurationBlock(
                    EXTENSION,
                    "allowWords word: 'mock', inFile: 'src/main/java/README.md'"));

            // ACT
            var actualResult = project.runExpectingFailure();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        @Test
        void failsWhenAnAllowanceIsDeclaredWithoutAFile(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "allowWord needs both 'word'";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(GateProject.buildConfigurationBlock(
                    EXTENSION,
                    "allowWord word: 'mock'"));

            // ACT
            var actualResult = project.runExpectingFailure();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        @Test
        void failsWhenAllowWordsIsDeclaredWithoutAFile(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "allowWords needs both 'words'";

            var project = GateProject
            .driving(GATE, projectDir)
                .configuringTheGate(GateProject.buildConfigurationBlock(
                    EXTENSION,
                    "allowWords words: ['mock']"));

            // ACT
            var actualResult = project.runExpectingFailure();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }
    }

    @Nested
    class UpToDateBehaviour {

        // An exempt tree is not read, so editing a test must not re-run the gate.
        @Test
        void passesUpToDateWhenOnlyAnExemptTreeChanged(@TempDir Path projectDir) {

            // ARRANGE
            var expectedOutcome = TaskOutcome.UP_TO_DATE;

            var project = GateProject
                .driving(GATE, projectDir)
                .holdingJavaSample(
                    PRODUCTION_JAVA_ROOT,
                    CLEAN_FIXTURE)
                .holdingJavaSample(
                    GateProject.TEST_JAVA_ROOT + PRODUCTION_PACKAGE_PATH,
                    MOCK_WORD_FIXTURE);

            project.runExpectingGateToPass();
            project.holdingJavaSample(
                GateProject.TEST_JAVA_ROOT + PRODUCTION_PACKAGE_PATH,
                CLEAN_FIXTURE);

            // ACT
            var actualResult = project.runExpectingSuccess();

            // ASSERT
            assertThat(actualResult.task(GATE.taskPath()).getOutcome())
                .isEqualTo(expectedOutcome);
        }
    }
}
