import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Integration test for the shared enforce-suffix-on-suites gate: it applies the real gate script
// into a throwaway project and runs the task, asserting the build outcome and the message a
// developer sees. Lives in the Common-Java ci-smoke project - the gate script is shared by every
// consumer; the tests live here beside it. Fixtures load from .txt files under java/ and kotlin/
// subfolders so the gate, which scans src/test, never sees a violating class here; this tree is
// src/test-gradle, deliberately out of its reach.
//
// The case that matters most is the one the gate must NOT flag: a helper filed under src/test
// holds no test and keeps its own name, so a suffix check over every class there would be wrong.
// Default package: the grouping folder is the source root, and its kebab name cannot be a Java
// package.
class EnforceSuffixOnSuitesGateIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest("enforceSuffixOnSuites", "suffix.on.suites.gate.script.path");

    private static final String TASK_PATH = ":enforceSuffixOnSuites";

    @Test
    void failsWhenASuiteEndsInTest(@TempDir Path projectDir) {

        var result = javaProject(projectDir, "suite-ending-in-test-is-flagged")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("'SampleTest'")
            .contains("does not end in 'Tests'");
    }

    // A suite is reported once however many tests it holds, so a developer reads one line per
    // class to rename rather than one per test.
    @Test
    void reportsASuiteOnce(@TempDir Path projectDir) {

        var result = javaProject(projectDir, "suite-ending-in-test-is-flagged")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .containsOnlyOnce("'SampleTest'");
    }

    // A group is what a suite is built of, so a class declaring one is a suite before its first
    // test lands.
    @Test
    void failsWhenAClassHoldsAGroupButNoTestYet(@TempDir Path projectDir) {

        var result = javaProject(projectDir, "group-without-tests-is-flagged")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("'SampleTest'")
            .contains("does not end in 'Tests'");
    }

    @Test
    void passesWhenASuiteEndsInTests(@TempDir Path projectDir) {

        var result = javaProject(projectDir, "suite-ending-in-tests-passes")
            .runExpectingSuccess();

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenAnIntegrationSuiteEndsInIntegrationTests(@TempDir Path projectDir) {

        var result = javaProject(projectDir, "integration-suite-passes")
            .runExpectingSuccess();

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    // A fixture or helper under src/test holds no test, so its name is its own; a nested class it
    // declares without @Nested is no group either.
    @Test
    void passesWhenAHelperHoldsNoTest(@TempDir Path projectDir) {

        var result = javaProject(projectDir, "helper-without-tests-passes")
            .runExpectingSuccess();

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    // A Kotlin group is an 'inner class' and its tests are declared with 'fun'; the outermost
    // class is read the same as its Java form.
    @Test
    void failsWhenAKotlinSuiteEndsInTest(@TempDir Path projectDir) {

        var result = kotlinProject(projectDir, "suite-ending-in-test-is-flagged")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("'SampleTest'")
            .contains("does not end in 'Tests'");
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
