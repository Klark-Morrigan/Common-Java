import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Drives the shared enforce-camel-case-test-names gate.
//
// The cases that matter most are the ones about what the gate must NOT read: snake_case is a common
// spelling for data, so a key in a string and a method named in a comment both have to pass, and
// Kotlin's backtick-quoted test names are the language's own idiom rather than an identifier a
// camelCase rule can be stated over.
class EnforceCamelCaseTestNamesGateIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest(
            "enforceCamelCaseTestNames",
            "lint/naming/tests/enforce-camel-case-test-names");

    // The second root the gate reads: test code shared across suites.

    @Nested
    class EnforceCamelCaseTestNames {

        @Test
        void passesWhenTestNamesAreCamelCaseInJava(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .holdingJavaTestSample("camel-case-names-passes");

            // ACT
            project.runExpectingGateToPass();
        }

        @Test
        void failsWhenATestNameIsSnakeCaseInJava(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage =
                "Sample.java:3: test method 'reads_the_owner_off_the_record' must be named in camelCase";

            var project = GateProject
                .driving(GATE, projectDir)
                .holdingJavaTestSample("snake-case-test-name-fails");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // A helper is read in a report exactly as a case is, so the rule reaches it too - and catching
        // it at the call as well as the declaration is what makes the gate's list the whole worklist.
        @Test
        void failsWhenAHelperNameIsSnakeCaseInJava(@TempDir Path projectDir) {

            // ARRANGE
            var expectedDeclarationFinding = "Sample.java:4: test method 'build_record'";
            var expectedCallFinding = "Sample.java:7: test method 'build_record'";

            var project = GateProject
                .driving(GATE, projectDir)
                .holdingJavaTestSample("snake-case-helper-name-fails");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedDeclarationFinding)
                .contains(expectedCallFinding);
        }

        // Data is not a name the gate has any business renaming, so a literal and a comment both pass -
        // even when the literal is spelt to look like a call.
        @Test
        void passesWhenSnakeCaseIsInALiteralOrACommentInJava(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .holdingJavaTestSample("snake-case-in-a-literal-or-comment-passes");

            // ACT
            project.runExpectingGateToPass();
        }

        // An enum constant with an argument list opens a call shape, but a constant is not a method.
        @Test
        void passesWhenAConstantNameOpensACallInJava(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .holdingJavaTestSample("snake-case-constant-call-passes");

            // ACT
            project.runExpectingGateToPass();
        }

        // Fixtures are held to the same rule as the cases that drive them.
        @Test
        void failsWhenATestNameIsSnakeCaseInTestFixtures(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "Sample.java:3: test method 'reads_the_owner_off_the_record'";

            var project = GateProject
                .driving(GATE, projectDir)
                .holdingJavaSample(
                    GateProject.TEST_FIXTURES_JAVA_ROOT,
                    "snake-case-test-name-fails");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // A Kotlin raw string runs across lines, and a line inside it carries no quote of its own for a
        // line-at-a-time strip to see.
        @Test
        void passesWhenSnakeCaseIsInARawStringInKotlin(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .holdingKotlinTestSample("snake-case-in-a-raw-string-passes");

            // ACT
            project.runExpectingGateToPass();
        }

        @Test
        void passesWhenATestNameIsBacktickQuotedInKotlin(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .holdingKotlinTestSample("backtick-test-name-passes");

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
