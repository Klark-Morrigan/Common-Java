import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Drives the shared enforce-suffix-on-fakes gate. The gate is one source-text rule, so every
// behaviour is exercised in both languages.
class EnforceSuffixOnFakesGateIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest("enforceSuffixOnFakes", "gate.script.path");

    @Test
    void passesWhenTypesAndVariablesAreSuffixedInJava(@TempDir Path projectDir) {

        GateProject
            .driving(GATE, projectDir)
            .holdingJavaTestSample("types-and-variables-are-suffixed")
            .runExpectingGateToPass();
    }

    @Test
    void failsWhenTypeIsPrefixedRatherThanSuffixedInJava(@TempDir Path projectDir) {

        var result = GateProject
            .driving(GATE, projectDir)
            .holdingJavaTestSample("type-is-prefixed-rather-than-suffixed")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("FakeConditionRepository")
            .contains("must be suffixed 'Fake'");
    }

    @Test
    void failsWhenVariableHoldingAFakeIsBareNamedInJava(@TempDir Path projectDir) {

        var result = GateProject
            .driving(GATE, projectDir)
            .holdingJavaTestSample("variable-holding-a-double-is-bare-named")
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

        GateProject
            .driving(GATE, projectDir)
            .holdingJavaTestSample("constant-holding-a-double-is-suffixed")
            .runExpectingGateToPass();
    }

    // The other half of that allowance: naming a constant is not exempted from the rule, only given
    // a second spelling, so a bare one is still caught.
    @Test
    void failsWhenConstantHoldingAFakeIsBareNamedInJava(@TempDir Path projectDir) {

        var result = GateProject
            .driving(GATE, projectDir)
            .holdingJavaTestSample("constant-holding-a-double-is-bare-named")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("'EMPTY'")
            .contains("holds a fake");
    }

    @Test
    void passesWhenConstantHoldingAFakeCarriesTheConstantSuffixInKotlin(@TempDir Path projectDir) {

        GateProject
            .driving(GATE, projectDir)
            .holdingKotlinTestSample("constant-holding-a-double-is-suffixed")
            .runExpectingGateToPass();
    }

    @Test
    void failsWhenConstantHoldingAFakeIsBareNamedInKotlin(@TempDir Path projectDir) {

        var result = GateProject
            .driving(GATE, projectDir)
            .holdingKotlinTestSample("constant-holding-a-double-is-bare-named")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("'EMPTY'")
            .contains("holds a fake");
    }

    // The constant spelling belongs to a constant's name. A camel-case holder tacking it onto the
    // end is not a constant spelled its own way, and passing it would open a second spelling for
    // every holder rather than only for the one that has no other.
    @Test
    void failsWhenCamelCaseHolderCarriesTheConstantSuffixInJava(@TempDir Path projectDir) {

        var result = GateProject
            .driving(GATE, projectDir)
            .holdingJavaTestSample("camel-holder-carries-the-constant-suffix")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("'market_FAKE'")
            .contains("holds a fake");
    }

    @Test
    void failsWhenCamelCaseHolderCarriesTheConstantSuffixInKotlin(@TempDir Path projectDir) {

        var result = GateProject
            .driving(GATE, projectDir)
            .holdingKotlinTestSample("camel-holder-carries-the-constant-suffix")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("'market_FAKE'")
            .contains("holds a fake");
    }

    @Test
    void passesWhenTypesAndVariablesAreSuffixedInKotlin(@TempDir Path projectDir) {

        GateProject
            .driving(GATE, projectDir)
            .holdingKotlinTestSample("types-and-variables-are-suffixed")
            .runExpectingGateToPass();
    }

    @Test
    void failsWhenTypeIsPrefixedRatherThanSuffixedInKotlin(@TempDir Path projectDir) {

        var result = GateProject
            .driving(GATE, projectDir)
            .holdingKotlinTestSample("type-is-prefixed-rather-than-suffixed")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("FakeConditionRepository")
            .contains("must be suffixed 'Fake'");
    }

    @Test
    void failsWhenVariableHoldingAFakeIsBareNamedInKotlin(@TempDir Path projectDir) {

        var result = GateProject
            .driving(GATE, projectDir)
            .holdingKotlinTestSample("variable-holding-a-double-is-bare-named")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("'market'")
            .contains("holds a fake");
    }

    // A type annotation puts the type, not the holder, before '=' - the gate
    // must still read the declared name and flag it.
    @Test
    void failsWhenKotlinTypedVariableIsBareNamed(@TempDir Path projectDir) {

        var result = GateProject
            .driving(GATE, projectDir)
            .holdingKotlinTestSample("typed-variable-is-bare-named")
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

        GateProject
            .driving(GATE, projectDir)
            .holdingKotlinTestSample("fake-as-named-argument-is-ignored")
            .runExpectingGateToPass();
    }

    // 'intel = XFake(...)' inside an apply block sets a production property on a
    // domain object; the name is not the test's to rename, so the gate ignores
    // it even though the value is a fake.
    @Test
    void passesWhenFakeIsSetOnAnAppliedProperty(@TempDir Path projectDir) {

        GateProject
            .driving(GATE, projectDir)
            .holdingKotlinTestSample("fake-on-applied-property-is-ignored")
            .runExpectingGateToPass();
    }

    // A suite over a fake is named for what it covers, so it holds the fake's whole
    // name with 'Tests' after it. It is the one type carrying 'Fake' that is not a
    // double, and flagging it would leave a fixture nobody may write a suite for
    // under its own name.
    @Test
    void passesWhenTypeIsASuiteOverAFakeInJava(@TempDir Path projectDir) {

        GateProject
            .driving(GATE, projectDir)
            .holdingJavaTestSample("suite-over-a-fake-is-not-a-double")
            .runExpectingGateToPass();
    }

    @Test
    void passesWhenTypeIsASuiteOverAFakeInKotlin(@TempDir Path projectDir) {

        GateProject
            .driving(GATE, projectDir)
            .holdingKotlinTestSample("suite-over-a-fake-is-not-a-double")
            .runExpectingGateToPass();
    }

    // A suite over a fake spelled in the singular is misnamed, but as a suite: that finding is
    // enforceSuffixOnSuites', and one here would tell its author to end the name in 'Fake'.
    @Test
    void passesWhenASuiteOverAFakeEndsInTheSingular(@TempDir Path projectDir) {

        GateProject
            .driving(GATE, projectDir)
            .holdingJavaTestSample("suite-over-a-fake-in-the-singular-is-left-alone")
            .runExpectingGateToPass();
    }

    @Test
    void passesWhenThereIsNoTestTree(@TempDir Path projectDir) {

        GateProject.driving(GATE, projectDir).runExpectingGateToPass();
    }
}
