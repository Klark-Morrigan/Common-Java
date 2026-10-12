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
        new GateUnderTest("enforcePackageVocabulary", "lint/structure/enforce-package-vocabulary");

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

    private static final String CLOSED_TO_THE_FEATURES_WORDS = GateProject.buildConfigurationBlock(
        EXTENSION,
        "forbidWords under: '" + CLOSED_PACKAGE_ROOT + "', words: [" + FORBIDDEN_WORDS + "]");

    private static final String CLOSED_BUT_ALLOWING_THE_README = GateProject.buildConfigurationBlock(
        EXTENSION,
        "forbidWords under: '" + CLOSED_PACKAGE_ROOT + "', words: [" + FORBIDDEN_WORDS + "]",
        "allowWord word: 'cart', inFile: '" + ALLOWED_README_PATH + "'");

    private static final String CLOSED_BUT_ALLOWING_SEVERAL_WORDS_IN_THE_README =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "forbidWords under: '" + CLOSED_PACKAGE_ROOT + "', words: [" + FORBIDDEN_WORDS + "]",
            "allowWords words: ['ledger', 'cart'], inFile: '" + ALLOWED_README_PATH + "'");

    // The shape a word left behind by a rule that no longer forbids it takes.
    private static final String CLOSED_BUT_ALLOWING_A_WORD_NO_RULE_FORBIDS = GateProject.buildConfigurationBlock(
        EXTENSION,
        "forbidWords under: '" + CLOSED_PACKAGE_ROOT + "', words: [" + FORBIDDEN_WORDS + "]",
        "allowWord word: 'invoice', inFile: '" + ALLOWED_README_PATH + "'");

    // The shape a path left behind by a rename or a move takes.
    private static final String CLOSED_BUT_ALLOWING_A_MISSING_FILE = GateProject.buildConfigurationBlock(
        EXTENSION,
        "forbidWords under: '" + CLOSED_PACKAGE_ROOT + "', words: [" + FORBIDDEN_WORDS + "]",
        "allowWord word: 'cart', inFile: '" + FRAMEWORK_PATH + "/GONE.md'");

    private static final String CLOSED_EXCEPT_THE_KINDS = GateProject.buildConfigurationBlock(
        EXTENSION,
        "forbidWords under: '" + CLOSED_PACKAGE_ROOT + "', except: '" + CLOSED_PACKAGE_ROOT +
            ".kinds', words: [" + FORBIDDEN_WORDS + "]");

    private static final String CLOSED_EXCEPT_A_PACKAGE_OUTSIDE_THE_ROOT = GateProject.buildConfigurationBlock(
        EXTENSION,
        "forbidWords under: '" + CLOSED_PACKAGE_ROOT + "', except: ['example.feature.kinds'], " +
            "words: [" + FORBIDDEN_WORDS + "]");

    // A phrase declared in camel case, and one declared with a hyphen.
    private static final String CLOSED_TO_A_CAMEL_CASE_PHRASE = GateProject.buildConfigurationBlock(
        EXTENSION,
        "forbidWords under: '" + CLOSED_PACKAGE_ROOT + "', words: ['SalesOrder']");

    private static final String CLOSED_TO_A_HYPHENATED_PHRASE = GateProject.buildConfigurationBlock(
        EXTENSION,
        "forbidWords under: '" + CLOSED_PACKAGE_ROOT + "', words: ['sales-order']");

    private static final String CLOSED_TO_A_WORDLESS_ENTRY = GateProject.buildConfigurationBlock(
        EXTENSION,
        "forbidWords under: '" + CLOSED_PACKAGE_ROOT + "', words: ['42']");

    // The shapes a misspelt or forgotten key takes.
    private static final String HALF_DECLARED_RULE = GateProject.buildConfigurationBlock(
        EXTENSION,
        "forbidWords under: '" + CLOSED_PACKAGE_ROOT + "'");

    private static final String HALF_DECLARED_ALLOWANCE = GateProject.buildConfigurationBlock(
        EXTENSION,
        "forbidWords under: '" + CLOSED_PACKAGE_ROOT + "', words: [" + FORBIDDEN_WORDS + "]",
        "allowWord word: 'cart'");

    private static final String HALF_DECLARED_ALLOWANCE_OF_SEVERAL_WORDS = GateProject.buildConfigurationBlock(
        EXTENSION,
        "forbidWords under: '" + CLOSED_PACKAGE_ROOT + "', words: [" + FORBIDDEN_WORDS + "]",
        "allowWords words: ['ledger', 'cart']");

    private static final String NO_WORDS_DECLARED = "";

    // A file beside the allowed README saying the word the README is allowed.
    private static final String NEIGHBOUR_SAYING_THE_ALLOWED_WORD =
        "package example.framework;\n\n// The totals here are read off the cart.\n";

    @Nested
    class Declarations {

        @Test
        void failsWhenWordsAreDeclaredWithoutTheRootTheyAreClosedTo(@TempDir Path projectDir) {

            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(HALF_DECLARED_RULE)
                .runExpectingFailure();

            assertThat(result.getOutput())
                .contains("forbidWords needs both 'under'");
        }

        @Test
        void failsWhenADeclaredWordHasNoLetters(@TempDir Path projectDir) {

            // An entry with no words could never match, so it would read as a rule and check nothing.
            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_A_WORDLESS_ENTRY)
                .runExpectingFailure();

            assertThat(result.getOutput())
                .contains("forbidWords names a word with no letters in it, which can never match; got: '42'");
        }

        @Test
        void failsWhenTheLeftOutSubtreeSitsOutsideTheClosedRoot(@TempDir Path projectDir) {

            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_EXCEPT_A_PACKAGE_OUTSIDE_THE_ROOT)
                .runExpectingFailure();

            assertThat(result.getOutput())
                .contains("forbidWords 'except' names a package strictly beneath its root " +
                    "example.framework; got: example.feature.kinds");
        }

        @Test
        void failsWhenAnAllowanceNamesNoFile(@TempDir Path projectDir) {

            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(HALF_DECLARED_ALLOWANCE)
                .runExpectingFailure();

            assertThat(result.getOutput())
                .contains("allowWord needs both 'word'");
        }

        @Test
        void failsWhenAnAllowanceOfSeveralWordsNamesNoFile(@TempDir Path projectDir) {

            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(HALF_DECLARED_ALLOWANCE_OF_SEVERAL_WORDS)
                .runExpectingFailure();

            assertThat(result.getOutput())
                .contains("allowWords needs both 'words'");
        }

        @Test
        void passesWhenTheBuildDeclaresNoWord(@TempDir Path projectDir) {

            GateProject.driving(GATE, projectDir)
                .configuringTheGate(NO_WORDS_DECLARED)
                .holdingFixture(FRAMEWORK_PATH + JAVA_SAMPLE, "java/framework-saying-a-forbidden-word")
                .runExpectingGateToPass();
        }

        @Test
        void failsAgainAfterAPassingRunOnceAWordIsDeclared(@TempDir Path projectDir) {

            // Were the declarations not a task input, the second run would be UP-TO-DATE.
            GateProject.driving(GATE, projectDir)
                .configuringTheGate(NO_WORDS_DECLARED)
                .holdingFixture(FRAMEWORK_PATH + JAVA_SAMPLE, "java/framework-saying-a-forbidden-word")
                .runExpectingGateToPass();

            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURES_WORDS)
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:8: example.framework may not say \"ledger\"");
        }
    }

    @Nested
    class Phrases {

        @Test
        void failsWhenACommentSaysAForbiddenWord(@TempDir Path projectDir) {

            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURES_WORDS)
                .holdingFixture(FRAMEWORK_PATH + JAVA_SAMPLE, "java/framework-saying-a-forbidden-word")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:8: example.framework may not say \"ledger\"");
        }

        @Test
        void failsWhenAMethodNameSaysAForbiddenPhrase(@TempDir Path projectDir) {

            // The case a hand sweep misses: the phrase spelt in camel case reads as one token.
            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURES_WORDS)
                .holdingFixture(FRAMEWORK_PATH + JAVA_SAMPLE, "java/framework-saying-a-forbidden-phrase-in-a-name")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:8: example.framework may not say \"sales order\"");
        }

        @Test
        void failsWhenProseWrapsAForbiddenPhraseAcrossTwoLines(@TempDir Path projectDir) {

            // Prose wraps where the column runs out, not where the phrase ends.
            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURES_WORDS)
                .holdingFixture(FRAMEWORK_PATH + README, "markdown/framework-readme-wrapping-a-forbidden-phrase")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("README.md:5: example.framework may not say \"sales order\"");
        }

        @Test
        void failsWhenProseSaysAPhraseDeclaredInCamelCase(@TempDir Path projectDir) {

            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_A_CAMEL_CASE_PHRASE)
                .holdingFixture(FRAMEWORK_PATH + README, "markdown/framework-readme-wrapping-a-forbidden-phrase")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("README.md:5: example.framework may not say \"SalesOrder\"");
        }

        @Test
        void failsWhenAnIdentifierSaysAPhraseDeclaredWithAHyphen(@TempDir Path projectDir) {

            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_A_HYPHENATED_PHRASE)
                .holdingFixture(FRAMEWORK_PATH + JAVA_SAMPLE, "java/framework-saying-a-forbidden-phrase-in-a-name")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:8: example.framework may not say \"sales-order\"");
        }

        @Test
        void passesWhenAWordMerelyCarriesAForbiddenOneAsAPrefix(@TempDir Path projectDir) {

            // Words are matched whole, so 'carton' is its own word. A gate firing on it would train
            // its reader to allow rather than to read.
            GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURES_WORDS)
                .holdingFixture(
                    FRAMEWORK_PATH + JAVA_SAMPLE,
                    "java/framework-saying-a-word-containing-a-forbidden-one")
                .runExpectingGateToPass();
        }
    }

    @Nested
    class Allowances {

        @Test
        void passesWhenTheWordIsAllowedInTheFileThatSaysIt(@TempDir Path projectDir) {

            GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_BUT_ALLOWING_THE_README)
                .holdingFixture(ALLOWED_README_PATH, "markdown/framework-readme-saying-an-allowed-word")
                .runExpectingGateToPass();
        }

        @Test
        void passesWhenSeveralWordsAreAllowedInTheFileThatSaysThem(@TempDir Path projectDir) {

            GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_BUT_ALLOWING_SEVERAL_WORDS_IN_THE_README)
                .holdingFixture(ALLOWED_README_PATH, "markdown/framework-readme-saying-an-allowed-word")
                .runExpectingGateToPass();
        }

        @Test
        void failsWhenAnAllowedWordIsSaidInAnotherFile(@TempDir Path projectDir) {

            // An allowance is per file: one README making its argument unlocks nothing beside it.
            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_BUT_ALLOWING_THE_README)
                .holdingFixture(ALLOWED_README_PATH, "markdown/framework-readme-saying-an-allowed-word")
                .holdingText(FRAMEWORK_PATH + "/Neighbour.java", NEIGHBOUR_SAYING_THE_ALLOWED_WORD)
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Neighbour.java:3: example.framework may not say \"cart\"")
                .doesNotContain("README.md:");
        }

        @Test
        void failsWhenAnAllowanceNamesAFileTheGateDoesNotScan(@TempDir Path projectDir) {

            // A stale path exempts nothing while reading in the build as though it does.
            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_BUT_ALLOWING_A_MISSING_FILE)
                .holdingFixture(FRAMEWORK_PATH + JAVA_SAMPLE, "java/framework-saying-nothing-forbidden")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("src/main/java/example/framework/GONE.md: allowWord names a file this gate does not scan");
        }

        @Test
        void failsWhenAnAllowanceNamesAWordNoRuleForbidsInThatFile(@TempDir Path projectDir) {

            // The file is scanned, so the path looks live, but no rule over it bans the word.
            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_BUT_ALLOWING_A_WORD_NO_RULE_FORBIDS)
                .holdingFixture(ALLOWED_README_PATH, "markdown/framework-readme-saying-an-allowed-word")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("README.md: allowWord names a word no rule forbids in that file, so it allows nothing: " +
                    "\"invoice\"");
        }
    }

    @Nested
    class Scope {

        @Test
        void failsWhenAMarkdownFileBesideTheCodeSaysAForbiddenWord(@TempDir Path projectDir) {

            // The README is what a reader of a framework reads first, and it carries no imports.
            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURES_WORDS)
                .holdingFixture(FRAMEWORK_PATH + README, "markdown/framework-readme-saying-a-forbidden-word")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("README.md:6: example.framework may not say \"ledger\"");
        }

        @Test
        void failsWhenASourceSetBeyondMainAndTestSaysAForbiddenWord(@TempDir Path projectDir) {

            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURES_WORDS)
                .holdingFixture(
                    "src/utils/java/example/framework" + JAVA_SAMPLE,
                    "java/framework-saying-a-forbidden-word")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("src/utils/java/example/framework/Sample.java:8: example.framework may not say \"ledger\"");
        }

        @Test
        void failsWhenKotlinCodeSaysAForbiddenWord(@TempDir Path projectDir) {

            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURES_WORDS)
                .holdingFixture(
                    GateProject.MAIN_KOTLIN_ROOT + "/example/framework" + KOTLIN_SAMPLE,
                    "kotlin/framework-saying-a-forbidden-word")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.kt:7: example.framework may not say \"ledger\"");
        }

        @Test
        void failsWhenASiblingOfTheLeftOutSubtreeSaysAForbiddenWord(@TempDir Path projectDir) {

            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_EXCEPT_THE_KINDS)
                .holdingFixture(CORE_PATH + JAVA_SAMPLE, "java/framework-saying-a-forbidden-word")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:8: example.framework except example.framework.kinds may not say \"ledger\"");
        }

        @Test
        void passesWhenTheLeftOutSubtreeSaysAForbiddenWord(@TempDir Path projectDir) {

            // A subtree whose subject the words are, markdown included.
            GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_EXCEPT_THE_KINDS)
                .holdingFixture(KINDS_PATH + JAVA_SAMPLE, "java/framework-saying-a-forbidden-word")
                .holdingFixture(KINDS_PATH + README, "markdown/framework-readme-saying-a-forbidden-word")
                .runExpectingGateToPass();
        }

        @Test
        void passesWhenTheFileSitsOutsideTheClosedRoot(@TempDir Path projectDir) {

            GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURES_WORDS)
                .holdingFixture(
                    GateProject.MAIN_JAVA_ROOT + "/example/feature" + JAVA_SAMPLE,
                    "java/feature-saying-its-own-word")
                .runExpectingGateToPass();
        }

        @Test
        void passesWhenThePackageOnlySharesAPrefixWithTheClosedRoot(@TempDir Path projectDir) {

            // 'example.frameworkish' is not under 'example.framework'.
            GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURES_WORDS)
                .holdingFixture(
                    GateProject.MAIN_JAVA_ROOT + "/example/frameworkish" + JAVA_SAMPLE,
                    "java/prefixed-package-saying-a-forbidden-word")
                .runExpectingGateToPass();
        }

        @Test
        void passesWhenThePathHoldsTheRootBelowAnotherPackage(@TempDir Path projectDir) {

            // 'com.acme.example.framework' only ends in the root's segments; it is not under it.
            GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURES_WORDS)
                .holdingFixture(
                    GateProject.MAIN_JAVA_ROOT + "/com/acme/example/framework" + JAVA_SAMPLE,
                    "java/framework-saying-a-forbidden-word")
                .runExpectingGateToPass();
        }

        @Test
        void passesWhenThereIsNoSourceTree(@TempDir Path projectDir) {

            GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURES_WORDS)
                .runExpectingGateToPass();
        }
    }
}
