import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Drives the shared end-of-run test report through a real 'test' task: a project with one suite, the
// report attached to its test task, and the log the run prints. What is asserted is what a reader of
// a long CI log is handed at its bottom.
class ReportTestOutcomesIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest(
            "test",
            "diagnostics/report-test-outcomes");

    private static final String SUITE_PATH = "src/test/java/SampleTests.java";

    // The JUnit stack the conventions pin, declared before the script is applied as a consumer's own
    // conventions would.
    private static final String JUNIT_PROJECT = "apply plugin: 'java'\n"
        + "repositories { mavenCentral() }\n"
        + "dependencies {\n"
        + "    testImplementation platform('org.junit:junit-bom:5.13.1')\n"
        + "    testImplementation 'org.junit.jupiter:junit-jupiter'\n"
        + "    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'\n"
        + "}\n"
        + "test { useJUnitPlatform() }\n";

    private static final String REPORTED_TEST_TASK = "reportTestOutcomesOf(test)\n";

    private static GateProject projectHolding(Path projectDir, String suiteFixture) {

        return GateProject
            .driving(GATE, projectDir)
            .withBuildPreamble(JUNIT_PROJECT)
            .configuringTheGate(REPORTED_TEST_TASK)
            .holdingFixture(
                SUITE_PATH,
                "java/" + suiteFixture);
    }

    @Nested
    class ReportTestOutcomesOf {

        @Test
        void listsTheLongestTestsOfAPassingRun(@TempDir Path projectDir) {

            // ARRANGE
            var expectedHeading = "Top 10 longest tests:";
            var expectedEntry = "SampleTests.passes()";
            var unexpectedHeading = "Failed tests";
            var project = projectHolding(projectDir, "all-passing");

            // ACT
            var actualResult = project.runExpectingGateToPass();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedHeading)
                .contains(expectedEntry)
                .doesNotContain(unexpectedHeading);
        }

        // The names first, numbered, then each one's trace under the same number, so a name picked
        // out of the list is looked up rather than searched for.
        @Test
        void relistsEachFailureByNameThenByTrace(@TempDir Path projectDir) {

            // ARRANGE
            var expectedList = "Failed tests (1):";
            var expectedEntry = " 1. SampleTests.fails()";
            var expectedDetails = "Failure details:";
            var expectedAssertion = "the sum ==> expected: <2> but was: <1>";
            var project = projectHolding(projectDir, "one-passing-one-failing");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedList)
                .contains(expectedEntry)
                .contains(expectedDetails)
                .contains(expectedAssertion);
        }

        // The throwable a reader sees first often says nothing, so the chain is walked to the cause
        // that does.
        @Test
        void walksAFailureToItsLastCause(@TempDir Path projectDir) {

            // ARRANGE
            var expectedCause = "Caused by: java.io.IOException: the real fault";
            var project = projectHolding(projectDir, "failure-with-a-cause");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedCause);
        }

        // A run dying on its environment fails every test with one fault, so the traces stop at ten
        // and the rest are counted rather than printed.
        @Test
        void capsTheTracesAtTenFailures(@TempDir Path projectDir) {

            // ARRANGE
            var expectedList = "Failed tests (12):";
            var expectedRemainder = "... and 2 more, in the task's HTML test report";
            var project = projectHolding(projectDir, "twelve-failing");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedList)
                .contains(expectedRemainder);
        }
    }
}
