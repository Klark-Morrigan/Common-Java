import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Drives the shared report-awt-environment probe, asserting what a developer is told and when the
// build stops.
//
// Every case is machine-independent, which for a probe about the machine takes saying. The report
// case asserts that each fact is NAMED, never what it says - a runner with no fonts must still pass
// this suite. The gate cases ask for a number of font families no machine has, so "cannot meet the
// requirement" is reachable on a workstation with every font installed and on a bare container
// alike.
class ReportAwtEnvironmentIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest(
            "reportAwtEnvironment",
            "diagnostics/report-awt-environment");

    // More families than any machine offers, so the requirement cannot be met wherever this runs.
    private static final String REQUIRES_MORE_FONTS_THAN_EXIST =
        "awtFontFamiliesRequired = 999999\n";

    @Nested
    class ReportAwtEnvironment {

        // Nothing required is the default: every consumer inherits the task, and a repo with no UI code is
        // never failed by it.
        @Test
        void reportsEveryFactAndPassesWhenNothingIsRequired(@TempDir Path projectDir) {

            // ARRANGE
            var expectedHeading = "AWT environment:";
            var expectedJvmLabel = "jvm: ";
            var expectedHeadlessLabel = "java.awt.headless: ";
            var expectedFontDirectory = "lib" + java.io.File.separator + "fonts";
            var expectedFamiliesLabel = "font families: ";
            var expectedMeasurementLabel = "text measurement: ";

            // No java plugin: the probe reads the machine rather than the project, so it has to work in
            // a build that declares no sources at all.
            var project = GateProject
                .driving(GATE, projectDir);

            // ACT
            var actualResult = project.runExpectingGateToPass();

            // ASSERT
            // The facts, not their values: what makes this worth running on a red runner is that the
            // log names the JVM, the headless setting, the JDK's own font directory, how many families
            // were offered, and whether text could actually be measured.
            assertThat(actualResult.getOutput())
                .contains(expectedHeading)
                .contains(expectedJvmLabel)
                .contains(expectedHeadlessLabel)
                .contains(expectedFontDirectory)
                .contains(expectedFamiliesLabel)
                .contains(expectedMeasurementLabel);
        }

        // The verdict names the number asked for, the environment behind it, and the package to install -
        // the three things the InternalError this replaces says nothing about.
        @Test
        void failsWhenTheMachineCannotOfferTheFontsAConsumerDeclared(@TempDir Path projectDir) {

            // ARRANGE
            var expectedRequirement = "awtFontFamiliesRequired = 999999";
            var expectedHeading = "AWT environment:";
            var expectedPackage = "fontconfig";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(REQUIRES_MORE_FONTS_THAN_EXIST);

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedRequirement)
                .contains(expectedHeading)
                .contains(expectedPackage);
        }

        // The whole point of the gate: one failure naming the machine, ahead of the suites, rather than
        // every suite that builds a text component failing with a JDK internal.
        @Test
        void stopsTheBuildBeforeTheTestsRunWhenFontsAreRequired(@TempDir Path projectDir) {

            // ARRANGE
            var project = projectWithATestTask(projectDir)
                .configuringTheGate(REQUIRES_MORE_FONTS_THAN_EXIST);

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            // Never reached, which is what "before the tests" means.
            assertThat(actualResult.task(":test"))
                .isNull();
        }

        @Test
        void leavesTheTestTaskAloneWhenNoFontsAreRequired(@TempDir Path projectDir) {

            // ARRANGE
            var unexpectedHeading = "AWT environment:";
            var project = projectWithATestTask(projectDir);

            // ACT
            var actualResult = project.runExpectingSuccess();

            // ASSERT
            assertThat(actualResult.getOutput())
                .doesNotContain(unexpectedHeading);

            assertThat(actualResult.task(GATE.taskPath()))
                .isNull();
        }
    }

    // The java plugin, for the cases about what the probe is wired into: a 'test' task has to exist
    // before anything can run ahead of it. It has no tests to run, which is all these cases need -
    // whether the probe ran, and whether 'test' was reached.
    private static GateProject projectWithATestTask(Path projectDir) {

        return GateProject
            .driving(GATE, projectDir)
            .applyingTheJavaPlugin()
            .runningTask("test");
    }
}
