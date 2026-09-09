import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Integration test for the shared lint-gate stamp, which is what lets every gate go UP-TO-DATE
// instead of re-scanning the whole source tree on each build. It is driven through a real gate
// because a stamp is only observable through one: the helper declares an output on somebody's task
// and appends an action to it, so there is nothing to call on its own.
//
// The trailing-whitespace gate is the one driven here, on no ground other than that a violation of
// it is a single trailing space - what is being tested is the skipping, not the rule.
//
// This is worth its own suite because the failure is silent in the direction that matters. A gate
// wrongly held UP-TO-DATE reports success over sources nobody scanned, and every consumer inherits
// that: the build stays green while the convention stops being enforced. The gate suites next door
// all drive a first run, so none of them can see it.
class LintGateStampIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest("enforceNoTrailingWhitespace", "trailing.whitespace.gate.script.path");

    private static final String TASK_PATH = ":enforceNoTrailingWhitespace";
    private static final String STAMP_PATH = "build/lint/enforceNoTrailingWhitespace.stamp";

    private static final String CLEAN_SOURCE = "class Sample {\n}\n";
    private static final String CLEAN_SOURCE_EDITED = "class Sample {\n    // edited\n}\n";
    private static final String SOURCE_WITH_TRAILING_SPACE = "class Sample { \n}\n";

    @Test
    void skipsAGateWhoseSourcesHaveNotChanged(@TempDir Path projectDir) {

        stage(projectDir, CLEAN_SOURCE);

        assertThat(runGate(projectDir, false).task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);

        assertThat(runGate(projectDir, false).task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.UP_TO_DATE);
    }

    @Test
    void runsAGateAgainWhenASourceItReadsChanges(@TempDir Path projectDir) {
        // The other half of skipping, and the one that makes it safe: a gate held UP-TO-DATE across
        // an edit would report a pass over text nobody read.
        stage(projectDir, CLEAN_SOURCE);
        runGate(projectDir, false);
        stage(projectDir, CLEAN_SOURCE_EDITED);

        assertThat(runGate(projectDir, false).task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void catchesAViolationIntroducedAfterAPassingRun(@TempDir Path projectDir) {
        // The whole point stated end to end: a gate that passed once must still fail on the edit
        // that breaks it, rather than riding its stamp into a green build.
        stage(projectDir, CLEAN_SOURCE);
        runGate(projectDir, false);
        stage(projectDir, SOURCE_WITH_TRAILING_SPACE);

        assertThat(runGate(projectDir, true).task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.FAILED);
    }

    @Test
    void runsAFailingGateAgainRatherThanSkippingIt(@TempDir Path projectDir) {
        // A failed gate leaves no stamp, so the next build re-runs it and fails again. Were the
        // stamp written regardless, a second build over unchanged bad sources would go UP-TO-DATE
        // and read as green.
        stage(projectDir, SOURCE_WITH_TRAILING_SPACE);

        assertThat(runGate(projectDir, true).task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.FAILED);

        assertThat(runGate(projectDir, true).task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.FAILED);
    }

    @Test
    void writesTheStampOnlyOnceTheGateHasPassed(@TempDir Path projectDir) {

        stage(projectDir, SOURCE_WITH_TRAILING_SPACE);
        runGate(projectDir, true);

        assertThat(projectDir.resolve(STAMP_PATH))
            .doesNotExist();

        stage(projectDir, CLEAN_SOURCE);
        runGate(projectDir, false);

        assertThat(projectDir.resolve(STAMP_PATH))
            .exists();
    }

    // Written and run as two steps, because these cases are about what a second run makes of
    // what a first one left: everything the fixture holds is on disk, so each step states the
    // project afresh rather than carrying one between them.
    //
    // The java plugin, because the gate driven here finds its trees by reading the project's own
    // source sets.
    private static void stage(Path projectDir, String content) {

        project(projectDir)
            .holdingText("src/main/java/Sample.java", content);
    }

    private static BuildResult runGate(Path projectDir, boolean expectFailure) {

        return expectFailure
            ? project(projectDir).runExpectingFailure()
            : project(projectDir).runExpectingSuccess();
    }

    private static GateProject project(Path projectDir) {

        return GateProject
            .driving(GATE, projectDir)
            .applyingTheJavaPlugin();
    }
}
