import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Integration test for the shared enforce-test-names-omit-group gate: it applies the real gate
// script into a throwaway project and runs the task, asserting the build outcome and the message a
// developer sees. Lives in the Common-Java ci-smoke project - the gate script is shared by every
// consumer; the tests live here beside it. Fixtures load from .txt files under java/ and kotlin/
// subfolders so the gate, which scans src/test, never sees a violating line here; this tree is
// src/test-gradle, deliberately out of its reach.
//
// The case that matters most is the one the gate must NOT flag: a name opening with the group's own
// word and going on in lower case is a different word, so a prefix match alone would be wrong.
// Default package: the grouping folder is the source root, and its kebab name cannot be a Java
// package.
class EnforceTestNamesOmitGroupGateIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest("enforceTestNamesOmitGroup", "test.names.omit.group.gate.script.path");

    private static final String TASK_PATH = ":enforceTestNamesOmitGroup";

    @Test
    void failsWhenATestNameRepeatsItsGroup(@TempDir Path projectDir) {

        var result = javaProject(projectDir, "group-prefixed-name-is-flagged")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("'getDrawnLayerIsNullBeforeAnyLayerIsRegistered'")
            .contains("repeats its @Nested group 'GetDrawnLayer'");
    }

    // A parameterised test carries a source annotation between its own and the method, and that
    // annotation's argument list reads like a call; the name judged is still the method's.
    @Test
    void failsWhenARepeatSitsBelowAnotherAnnotation(@TempDir Path projectDir) {

        var result = javaProject(projectDir, "group-prefixed-name-is-flagged")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("'readLevelClampsAnOutOfRangeValue'")
            .contains("repeats its @Nested group 'ReadLevel'");
    }

    @Test
    void passesWhenTestNamesStateTheOutcomeAlone(@TempDir Path projectDir) {

        var result = javaProject(projectDir, "outcome-and-condition-names-pass")
            .runExpectingSuccess();

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenANameMerelySharesTheGroupsLeadingWord(@TempDir Path projectDir) {

        var result = javaProject(projectDir, "shared-leading-word-passes")
            .runExpectingSuccess();

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    // A Kotlin group is an 'inner class', and its tests are declared with 'fun'; the rule reads
    // both the same as their Java forms.
    @Test
    void failsWhenAKotlinTestNameRepeatsItsInnerClassGroup(@TempDir Path projectDir) {

        var result = kotlinProject(projectDir, "group-prefixed-name-is-flagged")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("'getDrawnLayerIsNullBeforeAnyLayerIsRegistered'")
            .contains("repeats its @Nested group 'GetDrawnLayer'");
    }

    @Test
    void passesWhenAKotlinTestNameIsBacktickQuoted(@TempDir Path projectDir) {

        var result = kotlinProject(projectDir, "backtick-name-under-a-group-passes")
            .runExpectingSuccess();

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenThereIsNoTestTree(@TempDir Path projectDir) {

        var result = GateProject
            .driving(GATE, projectDir)
            .runExpectingSuccess();

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    private static GateProject javaProject(Path projectDir, String fixture) {

        return GateProject
            .driving(GATE, projectDir)
            .holdingFixture("src/test/java/Sample.java", "java/" + fixture);
    }

    private static GateProject kotlinProject(Path projectDir, String fixture) {

        return GateProject
            .driving(GATE, projectDir)
            .holdingFixture("src/test/kotlin/Sample.kt", "kotlin/" + fixture);
    }
}
