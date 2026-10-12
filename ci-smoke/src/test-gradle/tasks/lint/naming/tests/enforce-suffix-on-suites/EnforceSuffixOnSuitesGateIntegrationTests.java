import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Drives the shared enforce-suffix-on-suites gate.
//
// The case that matters most is the one the gate must NOT flag: a helper filed in a test tree holds
// no test and keeps its own name, so a suffix check over every class there would be wrong.
class EnforceSuffixOnSuitesGateIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest("enforceSuffixOnSuites", "lint/naming/tests/enforce-suffix-on-suites");

    // The second root the gate reads: test code shared across suites.

    // A suite is reported once however many tests it holds, so a developer reads one line per
    // class to rename rather than one per test.
    @Test
    void failsOnceWhenASuiteEndsInTestInJava(@TempDir Path projectDir) {

        var result = GateProject
            .driving(GATE, projectDir)
            .holdingJavaTestSample("suite-ending-in-test-fails")
            .runExpectingGateToFail();

        assertThat(result.getOutput())
            .contains("Sample.java:4: class 'SampleTest' holds tests and does not end in 'Tests'")
            .containsOnlyOnce("'SampleTest'");
    }

    // A group is what a suite is built of, so a class declaring one is a suite before its first
    // test lands.
    @Test
    void failsWhenAClassHoldsAGroupButNoTestYetInJava(@TempDir Path projectDir) {

        var result = GateProject
            .driving(GATE, projectDir)
            .holdingJavaTestSample("group-without-tests-fails")
            .runExpectingGateToFail();

        assertThat(result.getOutput())
            .contains("Sample.java:3: class 'SampleTest' holds tests and does not end in 'Tests'");
    }

    @Test
    void passesWhenASuiteEndsInTestsInJava(@TempDir Path projectDir) {

        GateProject
            .driving(GATE, projectDir)
            .holdingJavaTestSample("suite-ending-in-tests-passes")
            .runExpectingGateToPass();
    }

    @Test
    void passesWhenAnIntegrationSuiteEndsInIntegrationTestsInJava(@TempDir Path projectDir) {

        GateProject
            .driving(GATE, projectDir)
            .holdingJavaTestSample("integration-suite-passes")
            .runExpectingGateToPass();
    }

    // A helper holds no test, so its name is its own; a nested class it declares without @Nested is
    // no group either.
    @Test
    void passesWhenAHelperHoldsNoTestInJava(@TempDir Path projectDir) {

        GateProject
            .driving(GATE, projectDir)
            .holdingJavaTestSample("helper-without-tests-passes")
            .runExpectingGateToPass();
    }

    @Test
    void failsWhenASuiteEndsInTestInTestFixtures(@TempDir Path projectDir) {

        var result = GateProject
            .driving(GATE, projectDir)
            .holdingJavaSample(GateProject.TEST_FIXTURES_JAVA_ROOT, "suite-ending-in-test-fails")
            .runExpectingGateToFail();

        assertThat(result.getOutput())
            .contains("Sample.java:4: class 'SampleTest' holds tests");
    }

    // A Kotlin group is an 'inner class' and its tests are declared with 'fun'; the outermost
    // class is read the same as its Java form.
    @Test
    void failsWhenASuiteEndsInTestInKotlin(@TempDir Path projectDir) {

        var result = GateProject
            .driving(GATE, projectDir)
            .holdingKotlinTestSample("suite-ending-in-test-fails")
            .runExpectingGateToFail();

        assertThat(result.getOutput())
            .contains("Sample.kt:4: class 'SampleTest' holds tests and does not end in 'Tests'");
    }

    // A Kotlin file may hold several top-level classes; each is judged on its own, so a helper
    // above a suite is not blamed for the suite's name.
    @Test
    void failsOnTheSuiteRatherThanAHelperAboveItInKotlin(@TempDir Path projectDir) {

        var result = GateProject
            .driving(GATE, projectDir)
            .holdingKotlinTestSample("helper-before-a-suite-fails")
            .runExpectingGateToFail();

        assertThat(result.getOutput())
            .contains("Sample.kt:9: class 'SampleTest' holds tests")
            .doesNotContain("'SampleFixture'");
    }

    // A test outside any class is enforceTestsNested's finding. The class that closed above it
    // is not its suite, and naming it would send its author to rename a helper.
    @Test
    void passesWhenATestSitsOutsideAnyClassInKotlin(@TempDir Path projectDir) {

        GateProject
            .driving(GATE, projectDir)
            .holdingKotlinTestSample("test-outside-a-class-passes")
            .runExpectingGateToPass();
    }

    @Test
    void passesWhenThereIsNoTestTree(@TempDir Path projectDir) {

        GateProject
            .driving(GATE, projectDir)
            .runExpectingGateToPass();
    }
}
