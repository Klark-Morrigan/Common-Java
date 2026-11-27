import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Drives the shared enforce-tests-nested gate. The nested/top-level rule and the literal-stripping
// that protects it run in both languages; the Kotlin literals fixture uses a """ raw string and the
// backtick fixture an escaped identifier, both Kotlin-only lexical forms with no Java analogue.
class EnforceTestsNestedGateIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest(
            "enforceTestsNested",
            "lint/structure/tests/enforce-tests-nested");

    // The second root the gate reads: test code shared across suites.

    @Nested
    class EnforceTestsNested {

        @Test
        void passesWhenTestIsNestedInJava(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .holdingJavaTestSample("nested-test-passes");

            // ACT
            project.runExpectingGateToPass();
        }

        @Test
        void failsWhenTestIsAtTopLevelInJava(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "Sample.java:4: @Test method must live inside a @Nested class";

            var project = GateProject
                .driving(GATE, projectDir)
                .holdingJavaTestSample("top-level-test-fails");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // Every annotation that makes a JUnit test is held to the rule, the fully qualified spelling
        // included.
        @Test
        void failsWhenAnyTestAnnotationIsAtTopLevelInJava(@TempDir Path projectDir) {

            // ARRANGE
            var expectedFindingOnLine10 = "Sample.java:10: @Test method";
            var expectedFindingOnLine15 = "Sample.java:15: @Test method";
            var expectedFindingOnLine19 = "Sample.java:19: @Test method";
            var expectedFindingOnLine24 = "Sample.java:24: @Test method";

            var project = GateProject
                .driving(GATE, projectDir)
                .holdingJavaTestSample("test-family-at-top-level-fails");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedFindingOnLine10)
                .contains(expectedFindingOnLine15)
                .contains(expectedFindingOnLine19)
                .contains(expectedFindingOnLine24);
        }

        // An inner class without @Nested is a helper JUnit never descends into.
        @Test
        void failsWhenTestSitsInAClassWithoutNestedInJava(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "Sample.java:5: @Test method must live inside a @Nested class";

            var project = GateProject
                .driving(GATE, projectDir)
                .holdingJavaTestSample("test-in-a-class-without-nested-fails");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        @Test
        void failsWhenNestedClassIsStaticInJava(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "Sample.java:6: Java @Nested class must not be static";

            var project = GateProject
                .driving(GATE, projectDir)
                .holdingJavaTestSample("nested-static-class-fails");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        @Test
        void passesWhenAnnotationsAndBracesSitInLiteralsInJava(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .holdingJavaTestSample("literals-hiding-a-test-passes");

            // ACT
            project.runExpectingGateToPass();
        }

        @Test
        void failsWhenTestIsAtTopLevelInTestFixtures(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "Sample.java:4: @Test method must live inside a @Nested class";

            var project = GateProject
                .driving(GATE, projectDir)
                .holdingJavaSample(
                    GateProject.TEST_FIXTURES_JAVA_ROOT,
                    "top-level-test-fails");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        @Test
        void passesWhenTestIsNestedInKotlin(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .holdingKotlinTestSample("nested-test-passes");

            // ACT
            project.runExpectingGateToPass();
        }

        @Test
        void failsWhenTestIsAtTopLevelInKotlin(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "Sample.kt:4: @Test method must live inside a @Nested class";

            var project = GateProject
                .driving(GATE, projectDir)
                .holdingKotlinTestSample("top-level-test-fails");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // The Kotlin fixture hides the stray braces and @Test inside a """ raw string that spans
        // lines, which reaches the cross-line raw-string state the Java fixture cannot.
        @Test
        void passesWhenAnnotationsAndBracesSitInLiteralsInKotlin(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .holdingKotlinTestSample("literals-hiding-a-test-passes");

            // ACT
            project.runExpectingGateToPass();
        }

        // The @Test is inside a @Nested class, but one JUnit skips.
        @Test
        void failsWhenNestedClassIsNotInnerInKotlin(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "Sample.kt:7: Kotlin @Nested class must be declared `inner class`";

            var project = GateProject
                .driving(GATE, projectDir)
                .holdingKotlinTestSample("nested-class-without-inner-fails");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // An apostrophe in a backtick name read as a char-literal opener would swallow the method
        // brace and drift the scan into flagging later nested tests.
        @Test
        void passesWhenBacktickTestNameHoldsAnApostropheInKotlin(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .holdingKotlinTestSample("backtick-name-with-apostrophe-passes");

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
