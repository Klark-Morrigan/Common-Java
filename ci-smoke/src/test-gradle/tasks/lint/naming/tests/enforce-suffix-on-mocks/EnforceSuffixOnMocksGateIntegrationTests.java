import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Integration test for the shared enforce-suffix-on-mocks gate: it applies the
// real gate script into a throwaway project and runs the task, asserting the
// build outcome and the message a developer sees. Lives in the Common-Java ci-smoke project - the
// gate script is shared by every consumer; the tests live here beside it. Fixtures load from
// .txt files under java/ and kotlin/ subfolders so the gate, which scans
// src/test, never sees a violating line here; this tree is src/test-gradle,
// deliberately out of its reach. The variable-suffix rule applies to both
// languages, so the suffixed-pass, bare-local, and bare-static cases run in
// each; the lateinit, named-argument, and apply-block cases are Kotlin-only
// syntax with no Java analogue. Default package: the grouping folder is the
// source root, and its kebab name cannot be a Java package.
class EnforceSuffixOnMocksGateIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest("enforceSuffixOnMocks", "mock.gate.script.path");

    private static final String TASK_PATH = ":enforceSuffixOnMocks";
    @Test
    void passesWhenMockVariablesAreSuffixedInJava(@TempDir Path projectDir) {

        var result = javaProject(projectDir, "mock-variables-are-suffixed")
            .runExpectingSuccess();

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void failsWhenMockVariableIsBareNamedInJava(@TempDir Path projectDir) {

        var result = javaProject(projectDir, "mock-variable-is-bare-named")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("'sector'")
            .contains("holds a mock");
    }

    @Test
    void failsWhenStaticMockIsBareNamedInJava(@TempDir Path projectDir) {

        var result = javaProject(projectDir, "static-mock-is-bare-named")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("'global'")
            .contains("holds a mock");
    }

    @Test
    void passesWhenMockVariablesAreSuffixedInKotlin(@TempDir Path projectDir) {

        var result = kotlinProject(projectDir, "mock-variables-are-suffixed")
            .runExpectingSuccess();

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void failsWhenMockVariableIsBareNamedInKotlin(@TempDir Path projectDir) {

        var result = kotlinProject(projectDir, "mock-variable-is-bare-named")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("'sector'")
            .contains("holds a mock");
    }

    @Test
    void failsWhenStaticMockIsBareNamedInKotlin(@TempDir Path projectDir) {

        var result = kotlinProject(projectDir, "static-mock-is-bare-named")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("'global'")
            .contains("holds a mock");
    }

    @Test
    void failsWhenKotlinLateinitFieldIsBareNamed(@TempDir Path projectDir) {

        var result = kotlinProject(projectDir, "lateinit-field-is-bare-named")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("'sector'")
            .contains("holds a mock");
    }

    // A type annotation puts the type, not the holder, before '=' - the gate
    // must still read the declared name and flag it.
    @Test
    void failsWhenKotlinTypedDeclarationIsBareNamed(@TempDir Path projectDir) {

        var result = kotlinProject(projectDir, "typed-declaration-is-bare-named")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("'sector'")
            .contains("holds a mock");
    }

    // The mockito-kotlin reified 'mock<T>()' form ends the call with '<', not
    // '(' - the gate's factory anchor admits both.
    @Test
    void failsWhenKotlinReifiedMockIsBareNamed(@TempDir Path projectDir) {

        var result = kotlinProject(projectDir, "reified-mock-is-bare-named")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("'faction'")
            .contains("holds a mock");
    }

    // A Kotlin named argument reuses the 'name = mock(...)' shape but names a
    // constructor parameter, not a holder the test can rename - the gate must
    // leave it alone.
    @Test
    void passesWhenMockIsANamedArgument(@TempDir Path projectDir) {

        var result = kotlinProject(projectDir, "mock-as-named-argument-is-ignored")
            .runExpectingSuccess();

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    // 'intel = mock(...)' inside an apply block sets a production property on a
    // domain object; the name is not the test's to rename, so the gate ignores
    // it even though the value is a mock.
    @Test
    void passesWhenMockIsSetOnAnAppliedProperty(@TempDir Path projectDir) {

        var result = kotlinProject(projectDir, "mock-on-applied-property-is-ignored")
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
