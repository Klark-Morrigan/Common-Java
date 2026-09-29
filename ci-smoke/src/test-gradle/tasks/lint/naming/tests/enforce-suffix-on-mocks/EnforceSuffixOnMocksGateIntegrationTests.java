import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Drives the shared enforce-suffix-on-mocks gate. The variable-suffix rule applies to both
// languages, so the suffixed-pass, bare-local, and bare-static cases run in each; the lateinit,
// named-argument, and apply-block cases are Kotlin-only syntax with no Java analogue.
class EnforceSuffixOnMocksGateIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest("enforceSuffixOnMocks", "mock.gate.script.path");

    @Test
    void passesWhenMockVariablesAreSuffixedInJava(@TempDir Path projectDir) {

        GateProject
            .driving(GATE, projectDir)
            .holdingJavaTestSample("mock-variables-are-suffixed")
            .runExpectingGateToPass();
    }

    @Test
    void failsWhenMockVariableIsBareNamedInJava(@TempDir Path projectDir) {

        var result = GateProject
            .driving(GATE, projectDir)
            .holdingJavaTestSample("mock-variable-is-bare-named")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("'sector'")
            .contains("holds a mock");
    }

    @Test
    void failsWhenStaticMockIsBareNamedInJava(@TempDir Path projectDir) {

        var result = GateProject
            .driving(GATE, projectDir)
            .holdingJavaTestSample("static-mock-is-bare-named")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("'global'")
            .contains("holds a mock");
    }

    @Test
    void passesWhenMockVariablesAreSuffixedInKotlin(@TempDir Path projectDir) {

        GateProject
            .driving(GATE, projectDir)
            .holdingKotlinTestSample("mock-variables-are-suffixed")
            .runExpectingGateToPass();
    }

    @Test
    void failsWhenMockVariableIsBareNamedInKotlin(@TempDir Path projectDir) {

        var result = GateProject
            .driving(GATE, projectDir)
            .holdingKotlinTestSample("mock-variable-is-bare-named")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("'sector'")
            .contains("holds a mock");
    }

    @Test
    void failsWhenStaticMockIsBareNamedInKotlin(@TempDir Path projectDir) {

        var result = GateProject
            .driving(GATE, projectDir)
            .holdingKotlinTestSample("static-mock-is-bare-named")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("'global'")
            .contains("holds a mock");
    }

    @Test
    void failsWhenKotlinLateinitFieldIsBareNamed(@TempDir Path projectDir) {

        var result = GateProject
            .driving(GATE, projectDir)
            .holdingKotlinTestSample("lateinit-field-is-bare-named")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("'sector'")
            .contains("holds a mock");
    }

    // A type annotation puts the type, not the holder, before '=' - the gate
    // must still read the declared name and flag it.
    @Test
    void failsWhenKotlinTypedDeclarationIsBareNamed(@TempDir Path projectDir) {

        var result = GateProject
            .driving(GATE, projectDir)
            .holdingKotlinTestSample("typed-declaration-is-bare-named")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("'sector'")
            .contains("holds a mock");
    }

    // The mockito-kotlin reified 'mock<T>()' form ends the call with '<', not
    // '(' - the gate's factory anchor admits both.
    @Test
    void failsWhenKotlinReifiedMockIsBareNamed(@TempDir Path projectDir) {

        var result = GateProject
            .driving(GATE, projectDir)
            .holdingKotlinTestSample("reified-mock-is-bare-named")
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

        GateProject
            .driving(GATE, projectDir)
            .holdingKotlinTestSample("mock-as-named-argument-is-ignored")
            .runExpectingGateToPass();
    }

    // 'intel = mock(...)' inside an apply block sets a production property on a
    // domain object; the name is not the test's to rename, so the gate ignores
    // it even though the value is a mock.
    @Test
    void passesWhenMockIsSetOnAnAppliedProperty(@TempDir Path projectDir) {

        GateProject
            .driving(GATE, projectDir)
            .holdingKotlinTestSample("mock-on-applied-property-is-ignored")
            .runExpectingGateToPass();
    }

    @Test
    void passesWhenThereIsNoTestTree(@TempDir Path projectDir) {

        GateProject
            .driving(GATE, projectDir)
            .runExpectingGateToPass();
    }
}
