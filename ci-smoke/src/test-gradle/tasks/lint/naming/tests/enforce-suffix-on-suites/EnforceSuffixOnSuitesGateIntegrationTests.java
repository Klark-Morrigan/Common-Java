import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Drives the shared enforce-suffix-on-suites gate.
//
// The case that matters most is the one the gate must NOT flag: a helper filed under src/test
// holds no test and keeps its own name, so a suffix check over every class there would be wrong.
class EnforceSuffixOnSuitesGateIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest("enforceSuffixOnSuites", "suffix.on.suites.gate.script.path");

    @Test
    void failsWhenASuiteEndsInTest(@TempDir Path projectDir) {

        var result = GateProject
            .driving(GATE, projectDir)
            .holdingJavaTestSample("suite-ending-in-test-is-flagged")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("'SampleTest'")
            .contains("does not end in 'Tests'");
    }

    // A suite is reported once however many tests it holds, so a developer reads one line per
    // class to rename rather than one per test.
    @Test
    void reportsASuiteOnce(@TempDir Path projectDir) {

        var result = GateProject
            .driving(GATE, projectDir)
            .holdingJavaTestSample("suite-ending-in-test-is-flagged")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .containsOnlyOnce("'SampleTest'");
    }

    // A group is what a suite is built of, so a class declaring one is a suite before its first
    // test lands.
    @Test
    void failsWhenAClassHoldsAGroupButNoTestYet(@TempDir Path projectDir) {

        var result = GateProject
            .driving(GATE, projectDir)
            .holdingJavaTestSample("group-without-tests-is-flagged")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("'SampleTest'")
            .contains("does not end in 'Tests'");
    }

    @Test
    void passesWhenASuiteEndsInTests(@TempDir Path projectDir) {

        GateProject
            .driving(GATE, projectDir)
            .holdingJavaTestSample("suite-ending-in-tests-passes")
            .runExpectingGateToPass();
    }

    @Test
    void passesWhenAnIntegrationSuiteEndsInIntegrationTests(@TempDir Path projectDir) {

        GateProject
            .driving(GATE, projectDir)
            .holdingJavaTestSample("integration-suite-passes")
            .runExpectingGateToPass();
    }

    // A fixture or helper under src/test holds no test, so its name is its own; a nested class it
    // declares without @Nested is no group either.
    @Test
    void passesWhenAHelperHoldsNoTest(@TempDir Path projectDir) {

        GateProject
            .driving(GATE, projectDir)
            .holdingJavaTestSample("helper-without-tests-passes")
            .runExpectingGateToPass();
    }

    // A Kotlin group is an 'inner class' and its tests are declared with 'fun'; the outermost
    // class is read the same as its Java form.
    @Test
    void failsWhenAKotlinSuiteEndsInTest(@TempDir Path projectDir) {

        var result = GateProject
            .driving(GATE, projectDir)
            .holdingKotlinTestSample("suite-ending-in-test-is-flagged")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("'SampleTest'")
            .contains("does not end in 'Tests'");
    }

    // A Kotlin file may hold several top-level classes; each is judged on its own, so a helper
    // above a suite is not blamed for the suite's name.
    @Test
    void namesTheSuiteRatherThanAHelperAboveIt(@TempDir Path projectDir) {

        var result = GateProject
            .driving(GATE, projectDir)
            .holdingKotlinTestSample("helper-before-a-suite-in-one-file")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("'SampleTest'")
            .doesNotContain("'SampleFixture'");
    }

    // A test outside any class is enforceTestsNested's finding. The class that closed above it
    // is not its suite, and naming it would send its author to rename a helper.
    @Test
    void passesWhenATestSitsOutsideAnyClass(@TempDir Path projectDir) {

        GateProject
            .driving(GATE, projectDir)
            .holdingKotlinTestSample("test-outside-a-class-is-left-alone")
            .runExpectingGateToPass();
    }

    @Test
    void passesWhenThereIsNoTestTree(@TempDir Path projectDir) {

        GateProject
            .driving(GATE, projectDir)
            .runExpectingGateToPass();
    }
}
