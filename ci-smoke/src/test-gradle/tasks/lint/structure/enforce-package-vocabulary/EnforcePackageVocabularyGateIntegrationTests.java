import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Drives the shared enforce-package-vocabulary gate.
//
// The gate judges a file by the directory it sits in, so a case files its sample under a package
// path rather than at a source root.
class EnforcePackageVocabularyGateIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest(
            "enforcePackageVocabulary",
            "lint/structure/enforce-package-vocabulary");

    private static final String EXTENSION = "enforcePackageVocabulary";

    // The framework root may not speak the feature's words: what the fixtures are written against.
    private static final String CLOSED_PACKAGE_ROOT = "example.framework";
    private static final String FRAMEWORK_PATH = GateProject.MAIN_JAVA_ROOT + "/example/framework";
    private static final String KINDS_PATH = FRAMEWORK_PATH + "/kinds";
    private static final String CORE_PATH = FRAMEWORK_PATH + "/core";

    private static final String JAVA_SAMPLE = "/Sample.java";
    private static final String KOTLIN_SAMPLE = "/Sample.kt";
    private static final String README = "/README.md";

    // One two-word entry among the three, since only a phrase can wrap.
    private static final String FORBIDDEN_WORDS = "'ledger', 'sales order', 'cart'";

    private static final String ALLOWED_README_PATH = FRAMEWORK_PATH + README;

    private static final String CLOSED_TO_THE_FEATURES_WORDS =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "forbidWords under: '" +
                CLOSED_PACKAGE_ROOT +
                "', words: [" +
                FORBIDDEN_WORDS +
                "]");

    private static final String CLOSED_BUT_ALLOWING_THE_README =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "forbidWords under: '" +
                CLOSED_PACKAGE_ROOT +
                "', words: [" +
                FORBIDDEN_WORDS +
                "]",
            "allowWord word: 'cart', inFile: '" +
                ALLOWED_README_PATH +
                "'");

    private static final String CLOSED_BUT_ALLOWING_SEVERAL_WORDS_IN_THE_README =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "forbidWords under: '" +
                CLOSED_PACKAGE_ROOT +
                "', words: [" +
                FORBIDDEN_WORDS +
                "]",
            "allowWords words: ['ledger', 'cart'], inFile: '" +
                ALLOWED_README_PATH +
                "'");

    // The shape a word left behind by a rule that no longer forbids it takes.
    private static final String CLOSED_BUT_ALLOWING_A_WORD_NO_RULE_FORBIDS =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "forbidWords under: '" +
                CLOSED_PACKAGE_ROOT +
                "', words: [" +
                FORBIDDEN_WORDS +
                "]",
            "allowWord word: 'invoice', inFile: '" +
                ALLOWED_README_PATH +
                "'");

    // The shape a path left behind by a rename or a move takes.
    private static final String CLOSED_BUT_ALLOWING_A_MISSING_FILE =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "forbidWords under: '" +
                CLOSED_PACKAGE_ROOT +
                "', words: [" +
                FORBIDDEN_WORDS +
                "]",
            "allowWord word: 'cart', inFile: '" +
                FRAMEWORK_PATH +
                "/GONE.md'");

    private static final String CLOSED_EXCEPT_THE_KINDS =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "forbidWords under: '" +
                CLOSED_PACKAGE_ROOT +
                "', except: '" +
                CLOSED_PACKAGE_ROOT +
                ".kinds', words: [" +
                FORBIDDEN_WORDS +
                "]");

    private static final String CLOSED_EXCEPT_A_PACKAGE_OUTSIDE_THE_ROOT =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "forbidWords under: '" +
                CLOSED_PACKAGE_ROOT +
                "', except: ['example.feature.kinds'], words: [" +
                FORBIDDEN_WORDS +
                "]");

    // A phrase declared in camel case, and one declared with a hyphen.
    private static final String CLOSED_TO_A_CAMEL_CASE_PHRASE =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "forbidWords under: '" +
                CLOSED_PACKAGE_ROOT +
                "', words: ['SalesOrder']");

    private static final String CLOSED_TO_A_HYPHENATED_PHRASE =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "forbidWords under: '" +
                CLOSED_PACKAGE_ROOT +
                "', words: ['sales-order']");

    private static final String CLOSED_TO_A_WORDLESS_ENTRY =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "forbidWords under: '" +
                CLOSED_PACKAGE_ROOT +
                "', words: ['42']");

    // The shapes a misspelt or forgotten key takes.
    private static final String HALF_DECLARED_RULE =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "forbidWords under: '" +
                CLOSED_PACKAGE_ROOT +
                "'");

    private static final String HALF_DECLARED_ALLOWANCE =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "forbidWords under: '" +
                CLOSED_PACKAGE_ROOT +
                "', words: [" +
                FORBIDDEN_WORDS +
                "]",
            "allowWord word: 'cart'");

    private static final String HALF_DECLARED_ALLOWANCE_OF_SEVERAL_WORDS =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "forbidWords under: '" +
                CLOSED_PACKAGE_ROOT +
                "', words: [" +
                FORBIDDEN_WORDS +
                "]",
            "allowWords words: ['ledger', 'cart']");

    private static final String NO_WORDS_DECLARED = "";

    // A word the framework sample never says, for a passing run that still reads every file.
    private static final String CLOSED_TO_AN_UNSAID_WORD =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "forbidWords under: '" +
                CLOSED_PACKAGE_ROOT +
                "', words: ['zeppelin']");

    private static final String A_RULE_WITH_A_MISSPELT_KEY =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "forbidWords under: '" +
                CLOSED_PACKAGE_ROOT +
                "', expect: 'example.framework.kinds', words: ['ledger']");

    private static final String AN_ALLOWANCE_WITH_A_MISSPELT_KEY =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "forbidWords under: '" +
                CLOSED_PACKAGE_ROOT +
                "', words: ['ledger']",
            "allowWord word: 'ledger', inFiles: 'src/main/java/example/framework/README.md'");

    // A file beside the allowed README saying the word the README is allowed.
    private static final String NEIGHBOUR_SAYING_THE_ALLOWED_WORD =
        "package example.framework;\n\n// The totals here are read off the cart.\n";

    @Nested
    class Declarations {

        @Test
        void failsWhenWordsAreDeclaredWithoutTheRootTheyAreClosedTo(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "forbidWords needs both 'under'";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(HALF_DECLARED_RULE);

            // ACT
            var actualResult = project.runExpectingFailure();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // An entry with no words could never match, so it would read as a rule and check nothing.
        @Test
        void failsWhenADeclaredWordHasNoLetters(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage =
                "forbidWords names a word with no letters in it, which can never match; got: '42'";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_A_WORDLESS_ENTRY);

            // ACT
            var actualResult = project.runExpectingFailure();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        @Test
        void failsWhenTheLeftOutSubtreeSitsOutsideTheClosedRoot(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage =
                "forbidWords 'except' names a package strictly beneath its " +
                    "root example.framework; got: example.feature.kinds";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_EXCEPT_A_PACKAGE_OUTSIDE_THE_ROOT);

            // ACT
            var actualResult = project.runExpectingFailure();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        @Test
        void failsWhenAnAllowanceNamesNoFile(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "allowWord needs both 'word'";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(HALF_DECLARED_ALLOWANCE);

            // ACT
            var actualResult = project.runExpectingFailure();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        @Test
        void failsWhenAnAllowanceOfSeveralWordsNamesNoFile(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "allowWords needs both 'words'";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(HALF_DECLARED_ALLOWANCE_OF_SEVERAL_WORDS);

            // ACT
            var actualResult = project.runExpectingFailure();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // A misspelt key would otherwise be dropped in silence: 'expect' would close the subtree it meant to
        // leave out.
        @Test
        void failsWhenARuleNamesAKeyItDoesNotTake(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "forbidWords does not take 'expect'; it takes 'under', 'except', 'words'";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(A_RULE_WITH_A_MISSPELT_KEY);

            // ACT
            var actualResult = project.runExpectingFailure();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        @Test
        void failsWhenAnAllowanceNamesAKeyItDoesNotTake(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "allowWord does not take 'inFiles'; it takes 'word', 'inFile'";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(AN_ALLOWANCE_WITH_A_MISSPELT_KEY);

            // ACT
            var actualResult = project.runExpectingFailure();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // Every consumer inherits the gate, so one declaring nothing has it sit out.
        @Test
        void sitsOutWhenTheBuildDeclaresNoWord(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(NO_WORDS_DECLARED)
                .holdingFixture(
                    FRAMEWORK_PATH + JAVA_SAMPLE,
                    "java/framework-saying-a-forbidden-word");

            // ACT
            project.runExpectingGateToBeSkipped();
        }

        // Were the declarations not a task input, the second run would be UP-TO-DATE.
        @Test
        void failsAgainAfterAPassingRunOnceARuleTightens(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "Sample.java:8: example.framework may not say \"ledger\"";

            GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_AN_UNSAID_WORD)
                .holdingFixture(
                    FRAMEWORK_PATH + JAVA_SAMPLE,
                    "java/framework-saying-a-forbidden-word")
                .runExpectingGateToPass();

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURES_WORDS);

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }
    }

    @Nested
    class Phrases {

        @Test
        void failsWhenACommentSaysAForbiddenWord(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "Sample.java:8: example.framework may not say \"ledger\"";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURES_WORDS)
                .holdingFixture(
                    FRAMEWORK_PATH + JAVA_SAMPLE,
                    "java/framework-saying-a-forbidden-word");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // The case a hand sweep misses: the phrase spelt in camel case reads as one token.
        @Test
        void failsWhenAMethodNameSaysAForbiddenPhrase(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "Sample.java:8: example.framework may not say \"sales order\"";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURES_WORDS)
                .holdingFixture(
                    FRAMEWORK_PATH + JAVA_SAMPLE,
                    "java/framework-saying-a-forbidden-phrase-in-a-name");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // Prose wraps where the column runs out, not where the phrase ends.
        @Test
        void failsWhenProseWrapsAForbiddenPhraseAcrossTwoLines(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "README.md:5: example.framework may not say \"sales order\"";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURES_WORDS)
                .holdingFixture(
                    FRAMEWORK_PATH + README,
                    "markdown/framework-readme-wrapping-a-forbidden-phrase");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        @Test
        void failsWhenProseSaysAPhraseDeclaredInCamelCase(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "README.md:5: example.framework may not say \"SalesOrder\"";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_A_CAMEL_CASE_PHRASE)
                .holdingFixture(
                    FRAMEWORK_PATH + README,
                    "markdown/framework-readme-wrapping-a-forbidden-phrase");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        @Test
        void failsWhenAnIdentifierSaysAPhraseDeclaredWithAHyphen(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "Sample.java:8: example.framework may not say \"sales-order\"";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_A_HYPHENATED_PHRASE)
                .holdingFixture(
                    FRAMEWORK_PATH + JAVA_SAMPLE,
                    "java/framework-saying-a-forbidden-phrase-in-a-name");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // Words are matched whole, so 'carton' is its own word. A gate firing on it would train
        // its reader to allow rather than to read.
        @Test
        void passesWhenAWordMerelyCarriesAForbiddenOneAsAPrefix(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURES_WORDS)
                .holdingFixture(
                    FRAMEWORK_PATH + JAVA_SAMPLE,
                    "java/framework-saying-a-word-containing-a-forbidden-one");

            // ACT
            project.runExpectingGateToPass();
        }
    }

    @Nested
    class Allowances {

        @Test
        void passesWhenTheWordIsAllowedInTheFileThatSaysIt(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_BUT_ALLOWING_THE_README)
                .holdingFixture(
                    ALLOWED_README_PATH,
                    "markdown/framework-readme-saying-an-allowed-word");

            // ACT
            project.runExpectingGateToPass();
        }

        @Test
        void passesWhenSeveralWordsAreAllowedInTheFileThatSaysThem(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_BUT_ALLOWING_SEVERAL_WORDS_IN_THE_README)
                .holdingFixture(
                    ALLOWED_README_PATH,
                    "markdown/framework-readme-saying-an-allowed-word");

            // ACT
            project.runExpectingGateToPass();
        }

        // An allowance is per file: one README making its argument unlocks nothing beside it.
        @Test
        void failsWhenAnAllowedWordIsSaidInAnotherFile(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "Neighbour.java:3: example.framework may not say \"cart\"";
            var unexpectedFile = "README.md:";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_BUT_ALLOWING_THE_README)
                .holdingFixture(
                    ALLOWED_README_PATH,
                    "markdown/framework-readme-saying-an-allowed-word")
                .holdingText(
                    FRAMEWORK_PATH + "/Neighbour.java",
                    NEIGHBOUR_SAYING_THE_ALLOWED_WORD);

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage)
                .doesNotContain(unexpectedFile);
        }

        // A stale path exempts nothing while reading in the build as though it does.
        @Test
        void failsWhenAnAllowanceNamesAFileTheGateDoesNotScan(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage =
                "src/main/java/example/framework/GONE.md: allowWord names a file this gate does not scan";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_BUT_ALLOWING_A_MISSING_FILE)
                .holdingFixture(
                    FRAMEWORK_PATH + JAVA_SAMPLE,
                    "java/framework-saying-nothing-forbidden");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // The file is scanned, so the path looks live, but no rule over it bans the word.
        @Test
        void failsWhenAnAllowanceNamesAWordNoRuleForbidsInThatFile(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage =
                "README.md: allowWord names a word no rule forbids in that file, " +
                "so it allows nothing: \"invoice\"";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_BUT_ALLOWING_A_WORD_NO_RULE_FORBIDS)
                .holdingFixture(
                    ALLOWED_README_PATH,
                    "markdown/framework-readme-saying-an-allowed-word");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }
    }

    @Nested
    class Scope {

        // The README is what a reader of a framework reads first, and it carries no imports.
        @Test
        void failsWhenAMarkdownFileBesideTheCodeSaysAForbiddenWord(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "README.md:6: example.framework may not say \"ledger\"";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURES_WORDS)
                .holdingFixture(
                    FRAMEWORK_PATH + README,
                    "markdown/framework-readme-saying-a-forbidden-word");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        @Test
        void failsWhenASourceSetBeyondMainAndTestSaysAForbiddenWord(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage =
                "src/utils/java/example/framework/Sample.java:8: example.framework may not say \"ledger\"";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURES_WORDS)
                .holdingFixture(
                    "src/utils/java/example/framework" + JAVA_SAMPLE,
                    "java/framework-saying-a-forbidden-word");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        @Test
        void failsWhenKotlinCodeSaysAForbiddenWord(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "Sample.kt:7: example.framework may not say \"ledger\"";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURES_WORDS)
                .holdingFixture(
                    GateProject.MAIN_KOTLIN_ROOT + "/example/framework" + KOTLIN_SAMPLE,
                    "kotlin/framework-saying-a-forbidden-word");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        @Test
        void failsWhenASiblingOfTheLeftOutSubtreeSaysAForbiddenWord(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage =
                "Sample.java:8: example.framework except example.framework.kinds may not say \"ledger\"";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_EXCEPT_THE_KINDS)
                .holdingFixture(
                    CORE_PATH + JAVA_SAMPLE,
                    "java/framework-saying-a-forbidden-word");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // A subtree whose subject the words are, markdown included.
        @Test
        void passesWhenTheLeftOutSubtreeSaysAForbiddenWord(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_EXCEPT_THE_KINDS)
                .holdingFixture(
                    KINDS_PATH + JAVA_SAMPLE,
                    "java/framework-saying-a-forbidden-word")
                .holdingFixture(
                    KINDS_PATH + README,
                    "markdown/framework-readme-saying-a-forbidden-word");

            // ACT
            project.runExpectingGateToPass();
        }

        @Test
        void passesWhenTheFileSitsOutsideTheClosedRoot(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURES_WORDS)
                .holdingFixture(
                    GateProject.MAIN_JAVA_ROOT + "/example/feature" + JAVA_SAMPLE,
                    "java/feature-saying-its-own-word");

            // ACT
            project.runExpectingGateToPass();
        }

        // 'example.frameworkish' is not under 'example.framework'.
        @Test
        void passesWhenThePackageOnlySharesAPrefixWithTheClosedRoot(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURES_WORDS)
                .holdingFixture(
                    GateProject.MAIN_JAVA_ROOT + "/example/frameworkish" + JAVA_SAMPLE,
                    "java/prefixed-package-saying-a-forbidden-word");

            // ACT
            project.runExpectingGateToPass();
        }

        // 'com.acme.example.framework' only ends in the root's segments; it is not under it.
        @Test
        void passesWhenThePathHoldsTheRootBelowAnotherPackage(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURES_WORDS)
                .holdingFixture(
                    GateProject.MAIN_JAVA_ROOT + "/com/acme/example/framework" + JAVA_SAMPLE,
                    "java/framework-saying-a-forbidden-word");

            // ACT
            project.runExpectingGateToPass();
        }

        @Test
        void passesWhenThereIsNoSourceTree(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURES_WORDS);

            // ACT
            project.runExpectingGateToPass();
        }
    }
}
