import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Integration test for the shared report-awt-environment probe: it applies the real script into a
// throwaway project and runs it, asserting what a developer is told and when the build stops.
//
// Every case is machine-independent, which for a probe about the machine takes saying. The report
// case asserts that each fact is NAMED, never what it says - a runner with no fonts must still pass
// this suite. The gate cases ask for a number of font families no machine has, so "cannot meet the
// requirement" is reachable on a workstation with every font installed and on a bare container
// alike. Default package: the grouping folder is the source root, and its kebab name cannot be a
// Java package.
class ReportAwtEnvironmentIntegrationTests {

    private static final GateUnderTest PROBE =
        new GateUnderTest("reportAwtEnvironment", "awt.environment.script.path");

    private static final String TASK_PATH = ":reportAwtEnvironment";

    // More families than any machine offers, so the requirement cannot be met wherever this runs.
    private static final String REQUIRES_MORE_FONTS_THAN_EXIST =
        "awtFontFamiliesRequired = 999999\n";

    @Test
    void reportsEveryFactItCanGather(@TempDir Path projectDir) {

        var result = runProbe(projectDir, false, "");

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);

        // The facts, not their values: what makes this worth running on a red runner is that the
        // log names the JVM, the headless setting, the JDK's own font directory, how many families
        // were offered, and whether text could actually be measured.
        assertThat(result.getOutput())
            .contains("AWT environment:")
            .contains("jvm: ")
            .contains("java.awt.headless: ")
            .contains("lib" + java.io.File.separator + "fonts")
            .contains("font families: ")
            .contains("Dialog 12");
    }

    @Test
    void passesWhenNothingIsRequiredOfTheMachine(@TempDir Path projectDir) {

        // The default: every consumer inherits the task, and a repo with no UI code is never
        // failed by it, however little its runner can draw.
        var result = runProbe(projectDir, false, "");

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void failsWhenTheMachineCannotOfferTheFontsAConsumerDeclared(@TempDir Path projectDir) {

        var result = runProbe(projectDir, true, REQUIRES_MORE_FONTS_THAN_EXIST);

        // The verdict names the number asked for, the environment behind it, and the package to
        // install - the three things the InternalError this replaces says nothing about.
        assertThat(result.getOutput())
            .contains("awtFontFamiliesRequired = 999999")
            .contains("AWT environment:")
            .contains("fontconfig");
    }

    @Test
    void stopsTheBuildBeforeTheTestsRunWhenFontsAreRequired(@TempDir Path projectDir) {

        // The whole point of the gate: one failure naming the machine, ahead of the suites, rather
        // than every suite that builds a text component failing with a JDK internal.
        var result = runTaskOnProbingProject(
            projectDir, "test", true, REQUIRES_MORE_FONTS_THAN_EXIST);

        assertThat(result.getOutput())
            .contains("AWT environment:");

        // Never reached, which is what "before the tests" means.
        assertThat(result.task(":test"))
            .isNull();
    }

    @Test
    void leavesTheTestTaskAloneWhenNoFontsAreRequired(@TempDir Path projectDir) {

        var result = runTaskOnProbingProject(projectDir, "test", false, "");

        assertThat(result.getOutput())
            .doesNotContain("AWT environment:");
        assertThat(result.task(TASK_PATH))
            .isNull();
    }

    private BuildResult runProbe(
            Path projectDir,
            boolean expectFailure,
            String probeConfiguration) {

        // No java plugin: the probe reads the machine rather than the project, so it has to work in
        // a build that declares no sources at all.
        var project = GateProject
            .driving(PROBE, projectDir)
            .configuringTheGate(probeConfiguration);

        return expectFailure
            ? project.runExpectingFailure()
            : project.runExpectingSuccess();
    }

    // The java plugin, for the cases about what the probe is wired into: a 'test' task has to exist
    // before anything can run ahead of it. It has no tests to run, which is all these cases need -
    // whether the probe ran, and whether 'test' was reached.
    private BuildResult runTaskOnProbingProject(
            Path projectDir,
            String taskName,
            boolean expectFailure,
            String probeConfiguration) {

        var project = GateProject
            .driving(PROBE, projectDir)
            .applyingTheJavaPlugin()
            .configuringTheGate(probeConfiguration)
            .runningTask(taskName);

        return expectFailure
            ? project.runExpectingFailure()
            : project.runExpectingSuccess();
    }
}
