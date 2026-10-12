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
        new GateUnderTest("enforceRestrictedCalls", "lint/structure/enforce-restricted-calls");

    private static final String EXTENSION = "enforceRestrictedCalls";

    // One call contained beneath one root, to one type: what the fixtures are written against.
    private static final String CLOSED_PACKAGE_ROOT = "example.framework";
    private static final String RESTRICTED_CALL = "Example.getGlobalHandle()";
    private static final String SECOND_RESTRICTED_CALL = "Example.getGlobalClock()";
    private static final String ALLOWED_TYPE = "Adapter";
    private static final String INSTEAD_REPLACEMENT = "Seams.openGlobalHandle()";

    private static final String ALLOWED_TYPE_FILE = ALLOWED_TYPE + ".java";

    private static final String CONTAINED_TO_THE_ADAPTER = GateProject.buildConfigurationBlock(
        EXTENSION,
        "restrictCall call: '" + RESTRICTED_CALL + "', under: '" + CLOSED_PACKAGE_ROOT +
            "', toTypes: ['" + ALLOWED_TYPE + "']");

    // The allowed type as one name rather than a list, the shape a single seam is written in.
    private static final String CONTAINED_TO_THE_ADAPTER_WITHOUT_A_LIST = GateProject.buildConfigurationBlock(
        EXTENSION,
        "restrictCall call: '" + RESTRICTED_CALL + "', under: '" + CLOSED_PACKAGE_ROOT +
            "', toTypes: '" + ALLOWED_TYPE + "'");

    private static final String CONTAINED_TO_NOTHING = GateProject.buildConfigurationBlock(
        EXTENSION,
        "restrictCall call: '" + RESTRICTED_CALL + "', under: '" + CLOSED_PACKAGE_ROOT + "'");

    private static final String BOTH_CALLS_CONTAINED = GateProject.buildConfigurationBlock(
        EXTENSION,
        "restrictCall call: '" + RESTRICTED_CALL + "', under: '" + CLOSED_PACKAGE_ROOT +
            "', toTypes: ['" + ALLOWED_TYPE + "']",
        "restrictCall call: '" + SECOND_RESTRICTED_CALL + "', under: '" + CLOSED_PACKAGE_ROOT +
            "', toTypes: ['" + ALLOWED_TYPE + "']");

    // Closed outright with the way round it named: the shape a rule takes when the one allowed
    // seam lives in another build.
    private static final String CONTAINED_TO_NOTHING_NAMING_THE_REPLACEMENT =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "restrictCall call: '" + RESTRICTED_CALL + "', under: '" + CLOSED_PACKAGE_ROOT +
                "', instead: '" + INSTEAD_REPLACEMENT + "'");

    // The shape a misspelt or forgotten 'under:' takes.
    private static final String HALF_DECLARED_RESTRICTION = GateProject.buildConfigurationBlock(
        EXTENSION,
        "restrictCall call: '" + RESTRICTED_CALL + "'");

    private static final String NO_RESTRICTION_DECLARED = "";

    @Nested
    class Declarations {

        @Test
        void failsWhenARestrictionIsDeclaredWithoutTheRootItAppliesUnder(@TempDir Path projectDir) {

            // A half-declared restriction would be a rule that never fires.
            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(HALF_DECLARED_RESTRICTION)
                .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "caller-in-the-closed-root")
                .runExpectingFailure();

            assertThat(result.getOutput())
                .contains("restrictCall needs both 'call'");
        }

        @Test
        void passesWhenTheAllowedTypeIsNamedWithoutAList(@TempDir Path projectDir) {

            GateProject.driving(GATE, projectDir)
                .configuringTheGate(CONTAINED_TO_THE_ADAPTER_WITHOUT_A_LIST)
                .holdingFixture(
                    GateProject.MAIN_JAVA_ROOT + "/" + ALLOWED_TYPE_FILE,
                    "java/caller-in-the-closed-root")
                .runExpectingGateToPass();
        }

        @Test
        void failsWhenAnotherTypeMakesTheCallAllowedWithoutAList(@TempDir Path projectDir) {

            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(CONTAINED_TO_THE_ADAPTER_WITHOUT_A_LIST)
                .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "caller-in-the-closed-root")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:4: example.framework may call Example.getGlobalHandle() only in " +
                    "[Adapter]: Sample");
        }

        @Test
        void passesWhenTheBuildDeclaresNoRestriction(@TempDir Path projectDir) {

            GateProject.driving(GATE, projectDir)
                .configuringTheGate(NO_RESTRICTION_DECLARED)
                .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "caller-in-the-closed-root")
                .runExpectingGateToPass();
        }

        @Test
        void failsAgainAfterAPassingRunOnceARestrictionIsDeclared(@TempDir Path projectDir) {

            // Were the restrictions not a task input, the second run would be UP-TO-DATE.
            GateProject.driving(GATE, projectDir)
                .configuringTheGate(NO_RESTRICTION_DECLARED)
                .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "caller-in-the-closed-root")
                .runExpectingGateToPass();

            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(CONTAINED_TO_THE_ADAPTER)
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:4: example.framework may call Example.getGlobalHandle() only in");
        }
    }

    @Nested
    class Containment {

        @Test
        void failsWhenProductionCodeOutsideTheAllowedTypesMakesTheCall(@TempDir Path projectDir) {

            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(CONTAINED_TO_THE_ADAPTER)
                .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "caller-in-the-closed-root")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:4: example.framework may call Example.getGlobalHandle() only in " +
                    "[Adapter]: Sample");
        }

        @Test
        void failsOnARestrictionBeyondTheFirstDeclared(@TempDir Path projectDir) {

            // A gate that honoured only the first declaration would pass every other case.
            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(BOTH_CALLS_CONTAINED)
                .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "caller-of-the-second-restricted-call")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:4: example.framework may call Example.getGlobalClock() only in");
        }

        @Test
        void failsWhenTheAllowedTypeMakesACallContainedToNothing(@TempDir Path projectDir) {

            // No allowed types closes the root outright, which is what a mistyped 'toTypes:' becomes.
            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(CONTAINED_TO_NOTHING)
                .holdingFixture(
                    GateProject.MAIN_JAVA_ROOT + "/" + ALLOWED_TYPE_FILE,
                    "java/caller-in-the-closed-root")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Adapter.java:4: example.framework may call Example.getGlobalHandle() only in " +
                    "[]: Adapter");
        }

        @Test
        void failsNamingTheReplacementWhereTheRestrictionDeclaresOne(@TempDir Path projectDir) {

            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(CONTAINED_TO_NOTHING_NAMING_THE_REPLACEMENT)
                .holdingJavaSample(GateProject.TEST_JAVA_ROOT, "caller-in-the-closed-root")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:4: example.framework may call Example.getGlobalHandle() only in " +
                    "[]: Sample - instead: Seams.openGlobalHandle()");
        }

        @Test
        void passesWhenTheAllowedTypeMakesTheCall(@TempDir Path projectDir) {

            GateProject.driving(GATE, projectDir)
                .configuringTheGate(CONTAINED_TO_THE_ADAPTER)
                .holdingFixture(
                    GateProject.MAIN_JAVA_ROOT + "/" + ALLOWED_TYPE_FILE,
                    "java/caller-in-the-closed-root")
                .runExpectingGateToPass();
        }

        @Test
        void passesWhenNothingMakesTheRestrictedCall(@TempDir Path projectDir) {

            GateProject.driving(GATE, projectDir)
                .configuringTheGate(CONTAINED_TO_THE_ADAPTER)
                .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "calling-nothing-restricted")
                .runExpectingGateToPass();
        }
    }

    @Nested
    class Scope {

        @Test
        void failsWhenTestCodeMakesTheCall(@TempDir Path projectDir) {

            // A suite reaching for the ambient handle is the shortest way to make a case compile.
            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(CONTAINED_TO_THE_ADAPTER)
                .holdingJavaSample(GateProject.TEST_JAVA_ROOT, "caller-in-the-closed-root")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:4: example.framework may call Example.getGlobalHandle() only in");
        }

        @Test
        void failsWhenASourceSetBeyondMainAndTestMakesTheCall(@TempDir Path projectDir) {

            // An extra source set compiles against the same API, so exempting it leaves a hole.
            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(CONTAINED_TO_THE_ADAPTER)
                .holdingJavaSample("src/utils/java", "caller-in-the-closed-root")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:4: example.framework may call Example.getGlobalHandle() only in");
        }

        @Test
        void failsWhenKotlinCodeMakesTheCall(@TempDir Path projectDir) {

            // Kotlin leaves the semicolon off its package declaration.
            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(CONTAINED_TO_THE_ADAPTER)
                .holdingKotlinSample(GateProject.MAIN_KOTLIN_ROOT, "caller-in-the-closed-root")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.kt:4: example.framework may call Example.getGlobalHandle() only in");
        }

        @Test
        void passesWhenTheCallIsMadeOutsideTheClosedRoot(@TempDir Path projectDir) {

            GateProject.driving(GATE, projectDir)
                .configuringTheGate(CONTAINED_TO_THE_ADAPTER)
                .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "caller-outside-the-closed-root")
                .runExpectingGateToPass();
        }

        @Test
        void passesWhenTheCallingPackageOnlySharesAPrefix(@TempDir Path projectDir) {

            // 'example.frameworkish' is not under 'example.framework'.
            GateProject.driving(GATE, projectDir)
                .configuringTheGate(CONTAINED_TO_THE_ADAPTER)
                .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "caller-in-a-package-sharing-a-prefix")
                .runExpectingGateToPass();
        }

        @Test
        void passesWhenTheCallingFileIsInTheDefaultPackage(@TempDir Path projectDir) {

            // The default package sits under no root, so no restriction names it.
            GateProject.driving(GATE, projectDir)
                .configuringTheGate(CONTAINED_TO_THE_ADAPTER)
                .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "caller-in-the-default-package")
                .runExpectingGateToPass();
        }

        @Test
        void passesWhenThereIsNoSourceTree(@TempDir Path projectDir) {

            GateProject.driving(GATE, projectDir)
                .configuringTheGate(CONTAINED_TO_THE_ADAPTER)
                .runExpectingGateToPass();
        }
    }

    @Nested
    class CommentsAndLiterals {

        @Test
        void failsWhenTheCallIsAssembledAsAStringLiteral(@TempDir Path projectDir) {

            // Spelling the call inside a string must not be cheaper than declaring the seam.
            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(CONTAINED_TO_THE_ADAPTER)
                .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "calling-through-a-string-literal")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:4: example.framework may call Example.getGlobalHandle() only in");
        }

        @Test
        void failsWhenTheCallFollowsAClosedBlockCommentOnOneLine(@TempDir Path projectDir) {

            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(CONTAINED_TO_THE_ADAPTER)
                .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "calling-after-a-block-comment-closes")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:4: example.framework may call Example.getGlobalHandle() only in");
        }

        @Test
        void failsWhenTheCallSitsBelowEachCommentFormNestedInTheOther(@TempDir Path projectDir) {

            // A '/*' inside a line comment opens nothing; a '//' inside a block comment ends nothing.
            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(CONTAINED_TO_THE_ADAPTER)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT,
                    "calling-below-each-comment-form-nested-in-the-other")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:6: example.framework may call Example.getGlobalHandle() only in");
        }

        @Test
        void failsWhenTheCallFollowsAStringHoldingABlockCommentOpener(@TempDir Path projectDir) {

            // A '/*' inside a string opens no comment, so the code below it is still read.
            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(CONTAINED_TO_THE_ADAPTER)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT,
                    "calling-below-a-string-holding-a-block-comment-opener")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:5: example.framework may call Example.getGlobalHandle() only in");
        }

        @Test
        void failsWhenTheCallFollowsAStringHoldingALineCommentOpener(@TempDir Path projectDir) {

            // A '//' inside a string ends nothing, so the rest of its line is still read.
            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(CONTAINED_TO_THE_ADAPTER)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT,
                    "calling-beside-a-string-holding-a-line-comment-opener")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:4: example.framework may call Example.getGlobalHandle() only in");
        }

        @Test
        void passesWhenTheCallIsOnlyNamedInProse(@TempDir Path projectDir) {

            // Prose explaining why a call is contained is not a breach of the rule.
            GateProject.driving(GATE, projectDir)
                .configuringTheGate(CONTAINED_TO_THE_ADAPTER)
                .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "naming-the-call-in-prose-only")
                .runExpectingGateToPass();
        }
    }
}
