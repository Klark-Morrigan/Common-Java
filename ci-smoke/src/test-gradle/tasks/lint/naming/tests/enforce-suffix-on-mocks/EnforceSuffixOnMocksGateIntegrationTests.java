import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Drives the shared enforce-suffix-on-mocks gate. The variable-suffix rule applies to both
// languages; lateinit, named arguments, apply blocks and MockK are Kotlin-only.
class EnforceSuffixOnMocksGateIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest("enforceSuffixOnMocks", "lint/naming/tests/enforce-suffix-on-mocks");

    // The second root the gate reads: test code shared across suites.

    @Nested
    class Factories {

        @Test
        void passesWhenEveryKindOfDoubleIsSuffixedInJava(@TempDir Path projectDir) {

            GateProject
                .driving(GATE, projectDir)
                .holdingJavaTestSample("mock-variables-are-suffixed-passes")
                .runExpectingGateToPass();
        }

        @Test
        void passesWhenEveryKindOfDoubleIsSuffixedInKotlin(@TempDir Path projectDir) {

            GateProject
                .driving(GATE, projectDir)
                .holdingKotlinTestSample("mock-variables-are-suffixed-passes")
                .runExpectingGateToPass();
        }

        @Test
        void failsWhenVariableIsBareNamedInJava(@TempDir Path projectDir) {

            var result = GateProject
                .driving(GATE, projectDir)
                .holdingJavaTestSample("mock-variable-is-bare-named-fails")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:3: variable 'clock' holds a mock and must be suffixed 'Mock'");
        }

        @Test
        void failsWhenVariableIsBareNamedInKotlin(@TempDir Path projectDir) {

            var result = GateProject
                .driving(GATE, projectDir)
                .holdingKotlinTestSample("mock-variable-is-bare-named-fails")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.kt:3: variable 'clock' holds a mock and must be suffixed 'Mock'");
        }

        @Test
        void failsWhenStaticMockIsBareNamedInJava(@TempDir Path projectDir) {

            var result = GateProject
                .driving(GATE, projectDir)
                .holdingJavaTestSample("mock-static-variable-is-bare-named-fails")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:3: variable 'system' holds a mock");
        }

        @Test
        void failsWhenStaticMockIsBareNamedInKotlin(@TempDir Path projectDir) {

            var result = GateProject
                .driving(GATE, projectDir)
                .holdingKotlinTestSample("mock-static-variable-is-bare-named-fails")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.kt:3: variable 'system' holds a mock");
        }

        @Test
        void failsWhenConstructionMockIsBareNamedInJava(@TempDir Path projectDir) {

            var result = GateProject
                .driving(GATE, projectDir)
                .holdingJavaTestSample("mock-construction-variable-is-bare-named-fails")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:3: variable 'builders' holds a mock");
        }

        @Test
        void failsWhenFullyQualifiedMockIsBareNamedInJava(@TempDir Path projectDir) {

            var result = GateProject
                .driving(GATE, projectDir)
                .holdingJavaTestSample("mock-qualified-factory-variable-is-bare-named-fails")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:3: variable 'clock' holds a mock");
        }

        @Test
        void failsWhenSpyIsBareNamedInJava(@TempDir Path projectDir) {

            var result = GateProject
                .driving(GATE, projectDir)
                .holdingJavaTestSample("mock-spy-variable-is-bare-named-fails")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:3: variable 'registry' holds a mock");
        }

        // The reified 'mock<T>()' form ends the call with '<', not '('.
        @Test
        void failsWhenReifiedMockIsBareNamedInKotlin(@TempDir Path projectDir) {

            var result = GateProject
                .driving(GATE, projectDir)
                .holdingKotlinTestSample("mock-reified-variable-is-bare-named-fails")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.kt:3: variable 'clock' holds a mock");
        }

        @Test
        void failsWhenMockkDoublesAreBareNamedInKotlin(@TempDir Path projectDir) {

            var result = GateProject
                .driving(GATE, projectDir)
                .holdingKotlinTestSample("mock-mockk-variables-are-bare-named-fails")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.kt:3: variable 'clock' holds a mock")
                .contains("Sample.kt:4: variable 'registry' holds a mock");
        }

        // The bracket anchors on the factory itself, so a helper sharing its prefix is no double.
        @Test
        void passesWhenAMethodMerelySharesAFactoryPrefixInJava(@TempDir Path projectDir) {

            GateProject
                .driving(GATE, projectDir)
                .holdingJavaTestSample("mock-same-prefixed-method-passes")
                .runExpectingGateToPass();
        }
    }

    // A double the extension fills is born from no call at all, only from its field's annotation.
    @Nested
    class Annotations {

        @Test
        void failsWhenAnnotatedFieldsAreBareNamedInJava(@TempDir Path projectDir) {

            var result = GateProject
                .driving(GATE, projectDir)
                .holdingJavaTestSample("mock-annotated-fields-are-bare-named-fails")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:4: variable 'clock' holds a mock")
                .contains("Sample.java:6: variable 'registry' holds a mock")
                .contains("Sample.java:10: variable 'lookup' holds a mock");
        }

        @Test
        void failsWhenAnnotatedPropertyIsBareNamedInKotlin(@TempDir Path projectDir) {

            var result = GateProject
                .driving(GATE, projectDir)
                .holdingKotlinTestSample("mock-annotated-property-is-bare-named-fails")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.kt:4: variable 'clock' holds a mock");
        }
    }

    // Which bindings count as a holder the test can rename.
    @Nested
    class Holders {

        @Test
        void failsWhenConstantIsBareNamedInJava(@TempDir Path projectDir) {

            var result = GateProject
                .driving(GATE, projectDir)
                .holdingJavaTestSample("mock-constant-is-bare-named-fails")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:3: variable 'EMPTY' holds a mock");
        }

        // A Java type's generic arguments can nest, which ends the type at a later '>'.
        @Test
        void failsWhenVariableOfANestedGenericTypeIsBareNamedInJava(@TempDir Path projectDir) {

            var result = GateProject
                .driving(GATE, projectDir)
                .holdingJavaTestSample("mock-nested-generic-variable-is-bare-named-fails")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:4: variable 'lookup' holds a mock");
        }

        @Test
        void failsWhenLateinitVariableIsBareNamedInKotlin(@TempDir Path projectDir) {

            var result = GateProject
                .driving(GATE, projectDir)
                .holdingKotlinTestSample("mock-lateinit-variable-is-bare-named-fails")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.kt:5: variable 'clock' holds a mock");
        }

        // A type annotation puts the type, not the holder, before '='.
        @Test
        void failsWhenTypedVariableIsBareNamedInKotlin(@TempDir Path projectDir) {

            var result = GateProject
                .driving(GATE, projectDir)
                .holdingKotlinTestSample("mock-typed-variable-is-bare-named-fails")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.kt:2: variable 'clock' holds a mock");
        }

        @Test
        void passesWhenBareHoldersSitInCommentsInJava(@TempDir Path projectDir) {

            GateProject
                .driving(GATE, projectDir)
                .holdingJavaTestSample("mock-commented-out-double-passes")
                .runExpectingGateToPass();
        }

        // A named argument across lines whose parameter collides with a binding declared in the
        // file: only its trailing ',' tells it from a holder.
        @Test
        void passesWhenMockIsANamedArgumentAcrossLinesInKotlin(@TempDir Path projectDir) {

            GateProject
                .driving(GATE, projectDir)
                .holdingKotlinTestSample("mock-named-argument-across-lines-passes")
                .runExpectingGateToPass();
        }

        @Test
        void passesWhenMockIsANamedArgumentOnOneLineInKotlin(@TempDir Path projectDir) {

            GateProject
                .driving(GATE, projectDir)
                .holdingKotlinTestSample("mock-named-argument-on-one-line-passes")
                .runExpectingGateToPass();
        }

        // Inside an apply block the name is a production property, not the test's to rename.
        @Test
        void passesWhenMockIsSetOnAnAppliedPropertyInKotlin(@TempDir Path projectDir) {

            GateProject
                .driving(GATE, projectDir)
                .holdingKotlinTestSample("mock-applied-property-passes")
                .runExpectingGateToPass();
        }
    }

    @Nested
    class ScannedTrees {

        @Test
        void failsWhenVariableIsBareNamedInTestFixtures(@TempDir Path projectDir) {

            var result = GateProject
                .driving(GATE, projectDir)
                .holdingJavaSample(GateProject.TEST_FIXTURES_JAVA_ROOT, "mock-variable-is-bare-named-fails")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:3: variable 'clock' holds a mock");
        }

        @Test
        void passesWhenThereIsNoTestTree(@TempDir Path projectDir) {

            GateProject
                .driving(GATE, projectDir)
                .runExpectingGateToPass();
        }
    }
}
