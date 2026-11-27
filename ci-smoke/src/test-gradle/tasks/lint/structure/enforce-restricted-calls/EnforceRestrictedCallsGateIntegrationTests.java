import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Drives the shared enforce-restricted-calls gate.
//
// Which type a sample counts as is its file name, so a case writes its sample under the name the
// declaration does or does not allow - that pairing is the whole of what the gate decides.
class EnforceRestrictedCallsGateIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest(
            "enforceRestrictedCalls",
            "lint/structure/enforce-restricted-calls");

    private static final String EXTENSION = "enforceRestrictedCalls";

    // One call contained beneath one root, to one type: what the fixtures are written against.
    private static final String CLOSED_PACKAGE_ROOT = "example.framework";
    private static final String RESTRICTED_CALL = "Example.getGlobalHandle()";
    private static final String SECOND_RESTRICTED_CALL = "Example.getGlobalClock()";
    private static final String ALLOWED_TYPE = "Adapter";
    private static final String INSTEAD_REPLACEMENT = "Seams.openGlobalHandle()";

    private static final String ALLOWED_TYPE_FILE = ALLOWED_TYPE + ".java";

    private static final String CONTAINED_TO_THE_ADAPTER =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "restrictCall call: '" +
                RESTRICTED_CALL +
                "', under: '" +
                CLOSED_PACKAGE_ROOT +
                "', toTypes: ['" +
                ALLOWED_TYPE +
                "']");

    // The allowed type as one name rather than a list, the shape a single seam is written in.
    private static final String CONTAINED_TO_THE_ADAPTER_WITHOUT_A_LIST =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "restrictCall call: '" +
                RESTRICTED_CALL +
                "', under: '" +
                CLOSED_PACKAGE_ROOT +
                "', toTypes: '" +
                ALLOWED_TYPE +
                "'");

    private static final String CONTAINED_TO_NOTHING =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "restrictCall call: '" +
                RESTRICTED_CALL +
                "', under: '" +
                CLOSED_PACKAGE_ROOT +
                "'");

    private static final String BOTH_CALLS_CONTAINED =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "restrictCall call: '" +
                RESTRICTED_CALL +
                "', under: '" +
                CLOSED_PACKAGE_ROOT +
                "', toTypes: ['" +
                ALLOWED_TYPE +
                "']",
            "restrictCall call: '" +
                SECOND_RESTRICTED_CALL +
                "', under: '" +
                CLOSED_PACKAGE_ROOT +
                "', toTypes: ['" +
                ALLOWED_TYPE +
                "']");

    // Closed outright with the way round it named: the shape a rule takes when the one allowed
    // seam lives in another build.
    private static final String CONTAINED_TO_NOTHING_NAMING_THE_REPLACEMENT =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "restrictCall call: '" +
                RESTRICTED_CALL +
                "', under: '" +
                CLOSED_PACKAGE_ROOT +
                "', instead: '" +
                INSTEAD_REPLACEMENT +
                "'");

    // The shape a misspelt or forgotten 'under:' takes.
    private static final String HALF_DECLARED_RESTRICTION =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "restrictCall call: '" +
                RESTRICTED_CALL +
                "'");

    private static final String NO_RESTRICTION_DECLARED = "";

    // A call the sample never makes, for a passing run that still reads every file.
    private static final String THE_CLOCK_CONTAINED_TO_THE_ADAPTER =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "restrictCall call: '" +
                SECOND_RESTRICTED_CALL +
                "', under: '" +
                CLOSED_PACKAGE_ROOT +
                "', toTypes: ['" +
                ALLOWED_TYPE +
                "']");

    private static final String A_RESTRICTION_WITH_A_MISSPELT_KEY =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "restrictCall call: '" +
                RESTRICTED_CALL +
                "', under: '" +
                CLOSED_PACKAGE_ROOT +
                "', toType: '" +
                ALLOWED_TYPE +
                "'");

    @Nested
    class Declarations {

        // A half-declared restriction would be a rule that never fires.
        @Test
        void failsWhenARestrictionIsDeclaredWithoutTheRootItAppliesUnder(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "restrictCall needs both 'call'";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(HALF_DECLARED_RESTRICTION)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT,
                    "caller-in-the-closed-root");

            // ACT
            var actualResult = project.runExpectingFailure();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        @Test
        void passesWhenTheAllowedTypeIsNamedWithoutAList(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CONTAINED_TO_THE_ADAPTER_WITHOUT_A_LIST)
                .holdingFixture(
                    GateProject.MAIN_JAVA_ROOT + "/" + ALLOWED_TYPE_FILE,
                    "java/caller-in-the-closed-root");

            // ACT
            project.runExpectingGateToPass();
        }

        @Test
        void failsWhenAnotherTypeMakesTheCallAllowedWithoutAList(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage =
                "Sample.java:4: example.framework may call Example.getGlobalHandle() only in [Adapter]: Sample";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CONTAINED_TO_THE_ADAPTER_WITHOUT_A_LIST)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT,
                    "caller-in-the-closed-root");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // A misspelt key would otherwise be dropped in silence: 'toType' would close the root to the call
        // outright.
        @Test
        void failsWhenARestrictionNamesAKeyItDoesNotTake(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage =
                "restrictCall does not take 'toType'; it takes 'call', 'under', 'toTypes', 'instead'";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(A_RESTRICTION_WITH_A_MISSPELT_KEY)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT,
                    "caller-in-the-closed-root");

            // ACT
            var actualResult = project.runExpectingFailure();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // Every consumer inherits the gate, so one declaring nothing has it sit out.
        @Test
        void sitsOutWhenTheBuildDeclaresNoRestriction(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(NO_RESTRICTION_DECLARED)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT,
                    "caller-in-the-closed-root");

            // ACT
            project.runExpectingGateToBeSkipped();
        }

        // Were the restrictions not a task input, the second run would be UP-TO-DATE.
        @Test
        void failsAgainAfterAPassingRunOnceARestrictionTightens(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "Sample.java:4: example.framework may call Example.getGlobalHandle() only in";

            GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(THE_CLOCK_CONTAINED_TO_THE_ADAPTER)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT,
                    "caller-in-the-closed-root")
                .runExpectingGateToPass();

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CONTAINED_TO_THE_ADAPTER);

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }
    }

    @Nested
    class Containment {

        @Test
        void failsWhenProductionCodeOutsideTheAllowedTypesMakesTheCall(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage =
                "Sample.java:4: example.framework may call Example.getGlobalHandle() only in [Adapter]: Sample";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CONTAINED_TO_THE_ADAPTER)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT,
                    "caller-in-the-closed-root");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // A gate that honoured only the first declaration would pass every other case.
        @Test
        void failsOnARestrictionBeyondTheFirstDeclared(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "Sample.java:4: example.framework may call Example.getGlobalClock() only in";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(BOTH_CALLS_CONTAINED)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT,
                    "caller-of-the-second-restricted-call");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // No allowed types closes the root outright, which is what a mistyped 'toTypes:' becomes.
        @Test
        void failsWhenTheAllowedTypeMakesACallContainedToNothing(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage =
                "Adapter.java:4: example.framework may call Example.getGlobalHandle() only in []: Adapter";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CONTAINED_TO_NOTHING)
                .holdingFixture(
                    GateProject.MAIN_JAVA_ROOT + "/" + ALLOWED_TYPE_FILE,
                    "java/caller-in-the-closed-root");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        @Test
        void failsNamingTheReplacementWhereTheRestrictionDeclaresOne(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage =
                "Sample.java:4: example.framework may call Example.getGlobalHandle() " +
                    "only in []: Sample - instead: Seams.openGlobalHandle()";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CONTAINED_TO_NOTHING_NAMING_THE_REPLACEMENT)
                .holdingJavaSample(
                    GateProject.TEST_JAVA_ROOT,
                    "caller-in-the-closed-root");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        @Test
        void passesWhenTheAllowedTypeMakesTheCall(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CONTAINED_TO_THE_ADAPTER)
                .holdingFixture(
                    GateProject.MAIN_JAVA_ROOT + "/" + ALLOWED_TYPE_FILE,
                    "java/caller-in-the-closed-root");

            // ACT
            project.runExpectingGateToPass();
        }

        @Test
        void passesWhenNothingMakesTheRestrictedCall(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CONTAINED_TO_THE_ADAPTER)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT,
                    "calling-nothing-restricted");

            // ACT
            project.runExpectingGateToPass();
        }
    }

    @Nested
    class Scope {

        // A suite reaching for the ambient handle is the shortest way to make a case compile.
        @Test
        void failsWhenTestCodeMakesTheCall(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "Sample.java:4: example.framework may call Example.getGlobalHandle() only in";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CONTAINED_TO_THE_ADAPTER)
                .holdingJavaSample(
                    GateProject.TEST_JAVA_ROOT,
                    "caller-in-the-closed-root");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // An extra source set compiles against the same API, so exempting it leaves a hole.
        @Test
        void failsWhenASourceSetBeyondMainAndTestMakesTheCall(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "Sample.java:4: example.framework may call Example.getGlobalHandle() only in";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CONTAINED_TO_THE_ADAPTER)
                .holdingJavaSample(
                    "src/utils/java",
                    "caller-in-the-closed-root");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // Kotlin leaves the semicolon off its package declaration.
        @Test
        void failsWhenKotlinCodeMakesTheCall(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "Sample.kt:4: example.framework may call Example.getGlobalHandle() only in";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CONTAINED_TO_THE_ADAPTER)
                .holdingKotlinSample(
                    GateProject.MAIN_KOTLIN_ROOT,
                    "caller-in-the-closed-root");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        @Test
        void passesWhenTheCallIsMadeOutsideTheClosedRoot(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CONTAINED_TO_THE_ADAPTER)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT,
                    "caller-outside-the-closed-root");

            // ACT
            project.runExpectingGateToPass();
        }

        // 'example.frameworkish' is not under 'example.framework'.
        @Test
        void passesWhenTheCallingPackageOnlySharesAPrefix(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CONTAINED_TO_THE_ADAPTER)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT,
                    "caller-in-a-package-sharing-a-prefix");

            // ACT
            project.runExpectingGateToPass();
        }

        // The default package sits under no root, so no restriction names it.
        @Test
        void passesWhenTheCallingFileIsInTheDefaultPackage(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CONTAINED_TO_THE_ADAPTER)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT,
                    "caller-in-the-default-package");

            // ACT
            project.runExpectingGateToPass();
        }

        @Test
        void passesWhenThereIsNoSourceTree(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CONTAINED_TO_THE_ADAPTER);

            // ACT
            project.runExpectingGateToPass();
        }
    }

    @Nested
    class CommentsAndLiterals {

        // Spelling the call inside a string must not be cheaper than declaring the seam.
        @Test
        void failsWhenTheCallIsAssembledAsAStringLiteral(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "Sample.java:4: example.framework may call Example.getGlobalHandle() only in";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CONTAINED_TO_THE_ADAPTER)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT,
                    "calling-through-a-string-literal");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        @Test
        void failsWhenTheCallFollowsAClosedBlockCommentOnOneLine(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "Sample.java:4: example.framework may call Example.getGlobalHandle() only in";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CONTAINED_TO_THE_ADAPTER)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT,
                    "calling-after-a-block-comment-closes");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // A '/*' inside a line comment opens nothing; a '//' inside a block comment ends nothing.
        @Test
        void failsWhenTheCallSitsBelowEachCommentFormNestedInTheOther(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "Sample.java:6: example.framework may call Example.getGlobalHandle() only in";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CONTAINED_TO_THE_ADAPTER)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT,
                    "calling-below-each-comment-form-nested-in-the-other");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // A '/*' inside a string opens no comment, so the code below it is still read.
        @Test
        void failsWhenTheCallFollowsAStringHoldingABlockCommentOpener(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "Sample.java:5: example.framework may call Example.getGlobalHandle() only in";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CONTAINED_TO_THE_ADAPTER)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT,
                    "calling-below-a-string-holding-a-block-comment-opener");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // A '//' inside a string ends nothing, so the rest of its line is still read.
        @Test
        void failsWhenTheCallFollowsAStringHoldingALineCommentOpener(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "Sample.java:4: example.framework may call Example.getGlobalHandle() only in";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CONTAINED_TO_THE_ADAPTER)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT,
                    "calling-beside-a-string-holding-a-line-comment-opener");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // Prose explaining why a call is contained is not a breach of the rule.
        @Test
        void passesWhenTheCallIsOnlyNamedInProse(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CONTAINED_TO_THE_ADAPTER)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT,
                    "naming-the-call-in-prose-only");

            // ACT
            project.runExpectingGateToPass();
        }
    }
}
