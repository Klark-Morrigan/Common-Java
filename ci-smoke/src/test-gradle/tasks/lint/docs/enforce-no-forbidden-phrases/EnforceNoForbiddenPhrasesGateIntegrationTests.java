import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Drives the shared enforce-no-forbidden-phrases gate.
//
// Each case states one phrase ruled out of one set of documents, so what it shows is which lines the gate
// judges: the files a glob names, and the prose of those files rather than the samples quoted in them.
class EnforceNoForbiddenPhrasesGateIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest(
            "enforceNoForbiddenPhrases",
            "lint/docs/enforce-no-forbidden-phrases");

    private static final String EXTENSION = "enforceNoForbiddenPhrases";

    // The documents the phrase is ruled out of, and one beside them it is not.
    private static final String REFERENCE_PATH = "docs/reference/terms.md";
    private static final String GUIDE_PATH = "docs/guide.md";

    private static final String FORBIDDEN_LINE = "Kept as the manual says.\n";
    private static final String CLEAN_LINE = "Kept for what the word means.\n";

    private static final String MANUAL_RULE =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "forbidPhrase pattern: /(?i)\\bas the manual says\\b/, inFiles: " +
                "'docs/reference/*.md', reason: 'cites the manual as the reason'");

    // The documents named as a list, the shape a rule over two trees takes.
    private static final String MANUAL_RULE_OVER_TWO_GLOBS =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "forbidPhrase pattern: /(?i)\\bas the manual says\\b/, inFiles: " +
                "['docs/reference/*.md', 'docs/guide.md'], reason: 'cites the manual as the reason'");

    private static final String MANUAL_AND_HANDBOOK_RULES =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "forbidPhrase pattern: /(?i)\\bas the manual says\\b/, inFiles: " +
                "'docs/reference/*.md', reason: 'cites the manual as the reason'",
            "forbidPhrase pattern: /(?i)\\bas the handbook says\\b/, inFiles: " +
                "'docs/reference/*.md', reason: 'cites the handbook as the reason'");

    // A rule over the same documents that the forbidden line does not break.
    private static final String HANDBOOK_RULE =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "forbidPhrase pattern: /(?i)\\bas the handbook says\\b/, inFiles: " +
                "'docs/reference/*.md', reason: 'cites the handbook as the reason'");

    private static final String A_RULE_WITH_A_MISSPELT_KEY =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "forbidPhrase pattern: /as the manual says/, inFile: 'docs/reference/*.md', reason: 'cites the manual'");

    // The shape a forgotten 'reason:' takes.
    private static final String HALF_DECLARED_RULE =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "forbidPhrase pattern: /as the manual says/, inFiles: 'docs/reference/*.md'");

    private static final String UNCOMPILABLE_RULE =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "forbidPhrase pattern: '(unclosed', inFiles: 'docs/reference/*.md', reason: 'never compiles'");

    private static final String NO_RULE_DECLARED = "";

    @Nested
    class Declarations {

        // A rule missing a part would be a rule that never fires, or one whose finding says nothing.
        @Test
        void failsWhenARuleIsDeclaredWithoutItsReason(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "forbidPhrase needs 'pattern'";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(HALF_DECLARED_RULE)
                .holdingText(REFERENCE_PATH, FORBIDDEN_LINE);

            // ACT
            var actualResult = project.runExpectingFailure();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        @Test
        void failsWhenThePatternIsNotARegularExpression(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "forbidPhrase pattern is not a regular expression: (unclosed";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(UNCOMPILABLE_RULE)
                .holdingText(REFERENCE_PATH, FORBIDDEN_LINE);

            // ACT
            var actualResult = project.runExpectingFailure();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // A mistyped glob would otherwise read as a rule holding while the documents it meant go unjudged.
        @Test
        void failsWhenARuleMatchesNoFile(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "[docs/reference/*.md]: forbidPhrase matches no file, so it forbids nothing";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(MANUAL_RULE)
                .holdingText(GUIDE_PATH, CLEAN_LINE);

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // A misspelt key would otherwise be dropped in silence, and the rule refused for a part it named.
        @Test
        void failsWhenARuleNamesAKeyItDoesNotTake(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "forbidPhrase does not take 'inFile'; it takes 'pattern', 'inFiles', 'reason'";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(A_RULE_WITH_A_MISSPELT_KEY)
                .holdingText(REFERENCE_PATH, FORBIDDEN_LINE);

            // ACT
            var actualResult = project.runExpectingFailure();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // Every consumer inherits the gate, so one declaring nothing has it sit out.
        @Test
        void sitsOutWhenTheBuildDeclaresNoRule(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(NO_RULE_DECLARED)
                .holdingText(REFERENCE_PATH, FORBIDDEN_LINE);

            // ACT
            project.runExpectingGateToBeSkipped();
        }

        // Were the rules not a task input, the second run would be UP-TO-DATE.
        @Test
        void failsAgainAfterAPassingRunOnceARuleTightens(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "docs/reference/terms.md:1: cites the manual as the reason: 'as the manual says'";

            GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(HANDBOOK_RULE)
                .holdingText(REFERENCE_PATH, FORBIDDEN_LINE)
                .runExpectingGateToPass();

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(MANUAL_RULE);

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }
    }

    @Nested
    class Matching {

        @Test
        void failsNamingTheLineTheReasonAndThePhrase(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "docs/reference/terms.md:3: cites the manual as the reason: 'as the manual says'";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(MANUAL_RULE)
                .holdingText(
                    REFERENCE_PATH,
                    "# Terms\n\n" + FORBIDDEN_LINE);

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // A flag inside the pattern is what lets a rule cover a sentence-initial capital.
        @Test
        void failsAPhraseInAnyCaseWhereThePatternAsksForIt(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "docs/reference/terms.md:1: cites the manual as the reason: 'As The Manual Says'";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(MANUAL_RULE)
                .holdingText(
                    REFERENCE_PATH,
                    "As The Manual Says, it stays.\n");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // A gate that honoured only the first declaration would pass every other case.
        @Test
        void failsOnARuleBeyondTheFirstDeclared(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage =
                "docs/reference/terms.md:1: cites the handbook as the reason: 'as the handbook says'";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(MANUAL_AND_HANDBOOK_RULES)
                .holdingText(
                    REFERENCE_PATH,
                    "Kept as the handbook says.\n");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        @Test
        void failsInEveryDocumentAListOfGlobsNames(@TempDir Path projectDir) {

            // ARRANGE
            var expectedReferenceMessage = "docs/reference/terms.md:1: cites the manual as the reason";
            var expectedGuideMessage = "docs/guide.md:1: cites the manual as the reason";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(MANUAL_RULE_OVER_TWO_GLOBS)
                .holdingText(REFERENCE_PATH, FORBIDDEN_LINE)
                .holdingText(GUIDE_PATH, FORBIDDEN_LINE);

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedReferenceMessage)
                .contains(expectedGuideMessage);
        }

        // The phrase is ruled out of some documents, not all of them.
        @Test
        void passesWhenThePhraseSitsInADocumentTheRuleDoesNotName(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(MANUAL_RULE)
                .holdingText(REFERENCE_PATH, CLEAN_LINE)
                .holdingText(GUIDE_PATH, FORBIDDEN_LINE);

            // ACT
            project.runExpectingGateToPass();
        }

        @Test
        void passesWhenTheDocumentsSayNothingForbidden(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(MANUAL_RULE)
                .holdingText(REFERENCE_PATH, CLEAN_LINE);

            // ACT
            project.runExpectingGateToPass();
        }
    }

    @Nested
    class Scope {

        // A fenced block is sample text, so a phrase quoted in one is shown rather than said. The fence has
        // to close again: one that latched open would swallow the forbidden line below it.
        @Test
        void failsOnlyBelowAFenceQuotingThePhrase(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "docs/reference/terms.md:5: cites the manual as the reason";
            var unexpectedLocation = "terms.md:2:";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(MANUAL_RULE)
                .holdingText(
                    REFERENCE_PATH,
                    "```\n" + FORBIDDEN_LINE + "```\n\n" + FORBIDDEN_LINE);

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage)
                .doesNotContain(unexpectedLocation);
        }

        @Test
        void passesWhenThePhraseIsQuotedInACodeSpan(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(MANUAL_RULE)
                .holdingText(
                    REFERENCE_PATH,
                    "Never write `as the manual says` as a reason.\n");

            // ACT
            project.runExpectingGateToPass();
        }

        @Test
        void passesWhenTheDocumentSitsUnderAnExcludedDirectory(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(GateProject.buildConfigurationBlock(
                    EXTENSION,
                    "forbidPhrase pattern: /as the manual says/, inFiles: '**/*.md', reason: 'cites the manual'"))
                .holdingText(
                    "build/docs/terms.md",
                    FORBIDDEN_LINE)
                .holdingText(GUIDE_PATH, CLEAN_LINE);

            // ACT
            project.runExpectingGateToPass();
        }
    }

    @Nested
    class UpToDateBehaviour {

        @Test
        void passesUpToDateWhenNothingChanged(@TempDir Path projectDir) {

            // ARRANGE
            var expectedOutcome = TaskOutcome.UP_TO_DATE;

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(MANUAL_RULE)
                .holdingText(REFERENCE_PATH, CLEAN_LINE);

            project.runExpectingGateToPass();

            // ACT
            var actualResult = project.runExpectingSuccess();

            // ASSERT
            assertThat(actualResult.task(GATE.taskPath()).getOutcome())
                .isEqualTo(expectedOutcome);
        }
    }
}
