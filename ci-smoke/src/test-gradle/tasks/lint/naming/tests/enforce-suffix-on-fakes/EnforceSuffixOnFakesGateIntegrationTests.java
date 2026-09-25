import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Integration test for the shared enforce-suffix-on-fakes gate: it applies the
// real gate script into a throwaway project and runs the task, asserting the
// build outcome and the message a developer sees. Lives in the Common-Java ci-smoke project - the
// gate script is shared by every consumer; the tests live here beside it. Fixtures load from
// .txt files under java/ and kotlin/ subfolders so the gate, which scans
// src/test, never sees a violating line here; this tree is src/test-gradle,
// deliberately out of its reach. The gate is one source-text rule, so every
// behaviour is exercised in both languages. Default package: the grouping
// folder is the source root, and its kebab name cannot be a Java package.
class EnforceSuffixOnFakesGateIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest("enforceSuffixOnFakes", "gate.script.path");

    private static final String TASK_PATH = ":enforceSuffixOnFakes";

    @Test
    void passesWhenTypesAndVariablesAreSuffixedInJava(@TempDir Path projectDir) {

        var result = javaProject(projectDir, "types-and-variables-are-suffixed")
            .runExpectingSuccess();

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void failsWhenTypeIsPrefixedRatherThanSuffixedInJava(@TempDir Path projectDir) {

        var result = javaProject(projectDir, "type-is-prefixed-rather-than-suffixed")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("FakeConditionRepository")
            .contains("must be suffixed 'Fake'");
    }

    @Test
    void failsWhenVariableHoldingAFakeIsBareNamedInJava(@TempDir Path projectDir) {

        var result = javaProject(projectDir, "variable-holding-a-double-is-bare-named")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("'market'")
            .contains("holds a fake");
    }

    // A double held in a 'static final' is named in SCREAMING_SNAKE by Java's own convention, which
    // no camel-case suffix can end in - so the suffix is spelled '_FAKE' there, and a gate reading
    // only 'Fake' would leave such a constant with no compliant spelling at all.
    @Test
    void passesWhenConstantHoldingAFakeCarriesTheConstantSuffixInJava(@TempDir Path projectDir) {

        var result = javaProject(projectDir, "constant-holding-a-double-is-suffixed")
            .runExpectingSuccess();

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    // The other half of that allowance: naming a constant is not exempted from the rule, only given
    // a second spelling, so a bare one is still caught.
    @Test
    void failsWhenConstantHoldingAFakeIsBareNamedInJava(@TempDir Path projectDir) {

        var result = javaProject(projectDir, "constant-holding-a-double-is-bare-named")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("'EMPTY'")
            .contains("holds a fake");
    }

    @Test
    void passesWhenConstantHoldingAFakeCarriesTheConstantSuffixInKotlin(@TempDir Path projectDir) {

        var result = kotlinProject(projectDir, "constant-holding-a-double-is-suffixed")
            .runExpectingSuccess();

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void failsWhenConstantHoldingAFakeIsBareNamedInKotlin(@TempDir Path projectDir) {

        var result = kotlinProject(projectDir, "constant-holding-a-double-is-bare-named")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("'EMPTY'")
            .contains("holds a fake");
    }

    @Test
    void passesWhenTypesAndVariablesAreSuffixedInKotlin(@TempDir Path projectDir) {

        var result = kotlinProject(projectDir, "types-and-variables-are-suffixed")
            .runExpectingSuccess();

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void failsWhenTypeIsPrefixedRatherThanSuffixedInKotlin(@TempDir Path projectDir) {

        var result = kotlinProject(projectDir, "type-is-prefixed-rather-than-suffixed")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("FakeConditionRepository")
            .contains("must be suffixed 'Fake'");
    }

    @Test
    void failsWhenVariableHoldingAFakeIsBareNamedInKotlin(@TempDir Path projectDir) {

        var result = kotlinProject(projectDir, "variable-holding-a-double-is-bare-named")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("'market'")
            .contains("holds a fake");
    }

    // A type annotation puts the type, not the holder, before '=' - the gate
    // must still read the declared name and flag it.
    @Test
    void failsWhenKotlinTypedVariableIsBareNamed(@TempDir Path projectDir) {

        var result = kotlinProject(projectDir, "typed-variable-is-bare-named")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("'market'")
            .contains("holds a fake");
    }

    // A Kotlin named argument reuses the 'name = XFake(...)' shape but names a
    // constructor parameter, not a holder the test can rename - the gate must
    // leave it alone.
    @Test
    void passesWhenFakeIsANamedArgument(@TempDir Path projectDir) {

        var result = kotlinProject(projectDir, "fake-as-named-argument-is-ignored")
            .runExpectingSuccess();

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    // 'intel = XFake(...)' inside an apply block sets a production property on a
    // domain object; the name is not the test's to rename, so the gate ignores
    // it even though the value is a fake.
    @Test
    void passesWhenFakeIsSetOnAnAppliedProperty(@TempDir Path projectDir) {

        var result = kotlinProject(projectDir, "fake-on-applied-property-is-ignored")
            .runExpectingSuccess();

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    // A suite over a fake is named for what it covers, so it holds the fake's whole
    // name with 'Test' after it. It is the one type carrying 'Fake' that is not a
    // double, and flagging it would leave a fixture nobody may write a suite for
    // under its own name.
    @Test
    void passesWhenTypeIsASuiteOverAFakeInJava(@TempDir Path projectDir) {

        var result = javaProject(projectDir, "suite-over-a-fake-is-not-a-double")
            .runExpectingSuccess();

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenTypeIsASuiteOverAFakeInKotlin(@TempDir Path projectDir) {

        var result = kotlinProject(projectDir, "suite-over-a-fake-is-not-a-double")
            .runExpectingSuccess();

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenThereIsNoTestTree(@TempDir Path projectDir) {

        var result = GateProject.driving(GATE, projectDir).runExpectingSuccess();

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
