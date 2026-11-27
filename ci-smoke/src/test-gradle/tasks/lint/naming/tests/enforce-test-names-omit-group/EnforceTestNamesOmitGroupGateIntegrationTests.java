import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Drives the shared enforce-test-names-omit-group gate.
//
// The case that matters most is the one the gate must NOT flag: a name opening with the group's own
// word and going on in lower case is a different word, so a prefix match alone would be wrong.
class EnforceTestNamesOmitGroupGateIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest(
            "enforceTestNamesOmitGroup",
            "lint/naming/tests/enforce-test-names-omit-group");

    // The second root the gate reads: test code shared across suites.

    @Nested
    class EnforceTestNamesOmitGroup {

        // The second repeat sits below a parameterised test's source annotation, whose argument list
        // reads like a call; the name judged is still the method's.
        @Test
        void failsWhenATestNameRepeatsItsGroupInJava(@TempDir Path projectDir) {

            // ARRANGE
            var expectedTotalFinding =
                "Sample.java:12: test method 'readTotalIsZeroBeforeAnyEntryIsAdded' " +
                    "repeats its @Nested group 'ReadTotal'";

            var expectedLevelFinding =
                "Sample.java:23: test method 'readLevelClampsAnOutOfRangeValue' repeats its @Nested group 'ReadLevel'";

            var project = GateProject
                .driving(GATE, projectDir)
                .holdingJavaTestSample("group-prefixed-name-fails");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedTotalFinding)
                .contains(expectedLevelFinding);
        }

        @Test
        void passesWhenTestNamesStateTheOutcomeAloneInJava(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .holdingJavaTestSample("outcome-and-condition-names-passes");

            // ACT
            project.runExpectingGateToPass();
        }

        @Test
        void passesWhenANameMerelySharesTheGroupsLeadingWordInJava(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .holdingJavaTestSample("shared-leading-word-passes");

            // ACT
            project.runExpectingGateToPass();
        }

        // A test named for its top-level class is not held to a group it does not sit in: that it sits
        // in none is another gate's finding, and reporting it twice would name one fault two ways.
        @Test
        void passesWhenATestSitsOutsideAnyGroupInJava(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .holdingJavaTestSample("test-outside-a-group-passes");

            // ACT
            project.runExpectingGateToPass();
        }

        @Test
        void failsWhenATestNameRepeatsItsGroupInTestFixtures(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "Sample.java:12: test method 'readTotalIsZeroBeforeAnyEntryIsAdded'";

            var project = GateProject
                .driving(GATE, projectDir)
                .holdingJavaSample(
                    GateProject.TEST_FIXTURES_JAVA_ROOT,
                    "group-prefixed-name-fails");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // A Kotlin group is an 'inner class', and its tests are declared with 'fun'; the rule reads
        // both the same as their Java forms.
        @Test
        void failsWhenATestNameRepeatsItsGroupInKotlin(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage =
                "Sample.kt:10: test method 'readTotalIsZeroBeforeAnyEntryIsAdded' " +
                    "repeats its @Nested group 'ReadTotal'";

            var project = GateProject
                .driving(GATE, projectDir)
                .holdingKotlinTestSample("group-prefixed-name-fails");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        @Test
        void passesWhenATestNameIsBacktickQuotedInKotlin(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .holdingKotlinTestSample("backtick-name-under-a-group-passes");

            // ACT
            project.runExpectingGateToPass();
        }

        @Test
        void passesWhenThereIsNoTestTree(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir);

            // ACT
            project.runExpectingGateToPass();
        }
    }
}
