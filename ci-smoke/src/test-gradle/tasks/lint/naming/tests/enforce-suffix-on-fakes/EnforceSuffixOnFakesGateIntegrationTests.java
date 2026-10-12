import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Drives the shared enforce-suffix-on-fakes gate. The gate is one source-text rule, so every
// behaviour is exercised in both languages; named arguments and apply blocks are Kotlin-only.
class EnforceSuffixOnFakesGateIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest("enforceSuffixOnFakes", "lint/naming/tests/enforce-suffix-on-fakes");

    // The second root the gate reads: test code shared across suites, where most fakes live.

    @Nested
    class Types {

        @Test
        void failsWhenTypeIsPrefixedRatherThanSuffixedInJava(@TempDir Path projectDir) {

            var result = GateProject
                .driving(GATE, projectDir)
                .holdingJavaTestSample("fake-type-is-prefixed-fails")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:1: test-double type 'FakeRepository' must be suffixed 'Fake'");
        }

        @Test
        void failsWhenTypeIsPrefixedRatherThanSuffixedInKotlin(@TempDir Path projectDir) {

            var result = GateProject
                .driving(GATE, projectDir)
                .holdingKotlinTestSample("fake-type-is-prefixed-fails")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.kt:1: test-double type 'FakeRepository' must be suffixed 'Fake'");
        }

        // A suite over a fake is named for what it covers, so it holds the fake's whole name with
        // 'Tests' after it. It is the one type carrying 'Fake' that is not a double.
        @Test
        void passesWhenTypeIsASuiteOverAFakeInJava(@TempDir Path projectDir) {

            GateProject
                .driving(GATE, projectDir)
                .holdingJavaTestSample("fake-suite-over-a-double-passes")
                .runExpectingGateToPass();
        }

        @Test
        void passesWhenTypeIsASuiteOverAFakeInKotlin(@TempDir Path projectDir) {

            GateProject
                .driving(GATE, projectDir)
                .holdingKotlinTestSample("fake-suite-over-a-double-passes")
                .runExpectingGateToPass();
        }

        // A suite over a fake spelled in the singular is misnamed, but as a suite: that finding is
        // enforceSuffixOnSuites', and one here would tell its author to end the name in 'Fake'.
        @Test
        void passesWhenASuiteOverAFakeEndsInTheSingularInJava(@TempDir Path projectDir) {

            GateProject
                .driving(GATE, projectDir)
                .holdingJavaTestSample("fake-singular-suite-over-a-double-passes")
                .runExpectingGateToPass();
        }
    }

    @Nested
    class Variables {

        @Test
        void passesWhenTypesAndVariablesAreSuffixedInJava(@TempDir Path projectDir) {

            GateProject
                .driving(GATE, projectDir)
                .holdingJavaTestSample("fake-variables-are-suffixed-passes")
                .runExpectingGateToPass();
        }

        @Test
        void passesWhenTypesAndVariablesAreSuffixedInKotlin(@TempDir Path projectDir) {

            GateProject
                .driving(GATE, projectDir)
                .holdingKotlinTestSample("fake-variables-are-suffixed-passes")
                .runExpectingGateToPass();
        }

        @Test
        void failsWhenVariableIsBareNamedInJava(@TempDir Path projectDir) {

            var result = GateProject
                .driving(GATE, projectDir)
                .holdingJavaTestSample("fake-variable-is-bare-named-fails")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:3: variable 'clock' holds a fake and must be suffixed 'Fake'");
        }

        @Test
        void failsWhenVariableIsBareNamedInKotlin(@TempDir Path projectDir) {

            var result = GateProject
                .driving(GATE, projectDir)
                .holdingKotlinTestSample("fake-variable-is-bare-named-fails")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.kt:3: variable 'clock' holds a fake and must be suffixed 'Fake'");
        }

        // A type annotation puts the type, not the holder, before '='.
        @Test
        void failsWhenTypedVariableIsBareNamedInKotlin(@TempDir Path projectDir) {

            var result = GateProject
                .driving(GATE, projectDir)
                .holdingKotlinTestSample("fake-typed-variable-is-bare-named-fails")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.kt:2: variable 'clock' holds a fake");
        }

        // The constant spelling belongs to a constant's name; a camel-case holder tacking it on is
        // not a constant spelled its own way.
        @Test
        void failsWhenCamelCaseVariableCarriesTheConstantSuffixInJava(@TempDir Path projectDir) {

            var result = GateProject
                .driving(GATE, projectDir)
                .holdingJavaTestSample("fake-camel-variable-carries-the-constant-suffix-fails")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:3: variable 'clock_FAKE' holds a fake");
        }

        @Test
        void failsWhenCamelCaseVariableCarriesTheConstantSuffixInKotlin(@TempDir Path projectDir) {

            var result = GateProject
                .driving(GATE, projectDir)
                .holdingKotlinTestSample("fake-camel-variable-carries-the-constant-suffix-fails")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.kt:3: variable 'clock_FAKE' holds a fake");
        }
    }

    // A constant is named in SCREAMING_SNAKE, which no camel-case suffix can end in, so the suffix
    // is spelled '_FAKE' there - and a bare constant is still caught.
    @Nested
    class Constants {

        @Test
        void passesWhenConstantCarriesTheConstantSuffixInJava(@TempDir Path projectDir) {

            GateProject
                .driving(GATE, projectDir)
                .holdingJavaTestSample("fake-constant-carries-the-constant-suffix-passes")
                .runExpectingGateToPass();
        }

        @Test
        void passesWhenConstantCarriesTheConstantSuffixInKotlin(@TempDir Path projectDir) {

            GateProject
                .driving(GATE, projectDir)
                .holdingKotlinTestSample("fake-constant-carries-the-constant-suffix-passes")
                .runExpectingGateToPass();
        }

        @Test
        void failsWhenConstantIsBareNamedInJava(@TempDir Path projectDir) {

            var result = GateProject
                .driving(GATE, projectDir)
                .holdingJavaTestSample("fake-constant-is-bare-named-fails")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:3: variable 'EMPTY' holds a fake");
        }

        @Test
        void failsWhenConstantIsBareNamedInKotlin(@TempDir Path projectDir) {

            var result = GateProject
                .driving(GATE, projectDir)
                .holdingKotlinTestSample("fake-constant-is-bare-named-fails")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.kt:3: variable 'EMPTY' holds a fake");
        }
    }

    // Shapes that look like a holder but name nothing the test can rename, or nothing at all.
    @Nested
    class NonHolders {

        // A named argument across lines whose parameter collides with a binding declared in the
        // file: only its trailing ',' tells it from a holder.
        @Test
        void passesWhenFakeIsANamedArgumentAcrossLinesInKotlin(@TempDir Path projectDir) {

            GateProject
                .driving(GATE, projectDir)
                .holdingKotlinTestSample("fake-named-argument-across-lines-passes")
                .runExpectingGateToPass();
        }

        @Test
        void passesWhenFakeIsANamedArgumentOnOneLineInKotlin(@TempDir Path projectDir) {

            GateProject
                .driving(GATE, projectDir)
                .holdingKotlinTestSample("fake-named-argument-on-one-line-passes")
                .runExpectingGateToPass();
        }

        // Inside an apply block the name is a production property, not the test's to rename.
        @Test
        void passesWhenFakeIsSetOnAnAppliedPropertyInKotlin(@TempDir Path projectDir) {

            GateProject
                .driving(GATE, projectDir)
                .holdingKotlinTestSample("fake-applied-property-passes")
                .runExpectingGateToPass();
        }

        @Test
        void passesWhenBareHoldersAndPrefixedTypesSitInCommentsInJava(@TempDir Path projectDir) {

            GateProject
                .driving(GATE, projectDir)
                .holdingJavaTestSample("fake-commented-out-double-passes")
                .runExpectingGateToPass();
        }
    }

    @Nested
    class ScannedTrees {

        @Test
        void failsWhenTypeIsPrefixedInTestFixtures(@TempDir Path projectDir) {

            var result = GateProject
                .driving(GATE, projectDir)
                .holdingJavaSample(GateProject.TEST_FIXTURES_JAVA_ROOT, "fake-type-is-prefixed-fails")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:1: test-double type 'FakeRepository'");
        }

        @Test
        void passesWhenThereIsNoTestTree(@TempDir Path projectDir) {

            GateProject
                .driving(GATE, projectDir)
                .runExpectingGateToPass();
        }
    }
}
