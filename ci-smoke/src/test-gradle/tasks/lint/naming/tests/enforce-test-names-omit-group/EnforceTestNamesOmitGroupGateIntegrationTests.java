import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Drives the shared enforce-test-names-omit-group gate.
//
// The case that matters most is the one the gate must NOT flag: a name opening with the group's own
// word and going on in lower case is a different word, so a prefix match alone would be wrong.
class EnforceTestNamesOmitGroupGateIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest("enforceTestNamesOmitGroup", "test.names.omit.group.gate.script.path");

    @Test
    void failsWhenATestNameRepeatsItsGroup(@TempDir Path projectDir) {

        var result = GateProject
            .driving(GATE, projectDir)
            .holdingJavaTestSample("group-prefixed-name-is-flagged")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("'getDrawnLayerIsNullBeforeAnyLayerIsRegistered'")
            .contains("repeats its @Nested group 'GetDrawnLayer'");
    }

    // A parameterised test carries a source annotation between its own and the method, and that
    // annotation's argument list reads like a call; the name judged is still the method's.
    @Test
    void failsWhenARepeatSitsBelowAnotherAnnotation(@TempDir Path projectDir) {

        var result = GateProject
            .driving(GATE, projectDir)
            .holdingJavaTestSample("group-prefixed-name-is-flagged")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("'readLevelClampsAnOutOfRangeValue'")
            .contains("repeats its @Nested group 'ReadLevel'");
    }

    @Test
    void passesWhenTestNamesStateTheOutcomeAlone(@TempDir Path projectDir) {

        GateProject
            .driving(GATE, projectDir)
            .holdingJavaTestSample("outcome-and-condition-names-pass")
            .runExpectingGateToPass();
    }

    @Test
    void passesWhenANameMerelySharesTheGroupsLeadingWord(@TempDir Path projectDir) {

        GateProject
            .driving(GATE, projectDir)
            .holdingJavaTestSample("shared-leading-word-passes")
            .runExpectingGateToPass();
    }

    // A test named for its top-level class is not held to a group it does not sit in: that it sits
    // in none is another gate's finding, and reporting it twice would name one fault two ways.
    @Test
    void passesWhenATestSitsOutsideAnyGroup(@TempDir Path projectDir) {

        GateProject
            .driving(GATE, projectDir)
            .holdingJavaTestSample("test-outside-a-group-is-left-alone")
            .runExpectingGateToPass();
    }

    // A Kotlin group is an 'inner class', and its tests are declared with 'fun'; the rule reads
    // both the same as their Java forms.
    @Test
    void failsWhenAKotlinTestNameRepeatsItsInnerClassGroup(@TempDir Path projectDir) {

        var result = GateProject
            .driving(GATE, projectDir)
            .holdingKotlinTestSample("group-prefixed-name-is-flagged")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("'getDrawnLayerIsNullBeforeAnyLayerIsRegistered'")
            .contains("repeats its @Nested group 'GetDrawnLayer'");
    }

    @Test
    void passesWhenAKotlinTestNameIsBacktickQuoted(@TempDir Path projectDir) {

        GateProject
            .driving(GATE, projectDir)
            .holdingKotlinTestSample("backtick-name-under-a-group-passes")
            .runExpectingGateToPass();
    }

    @Test
    void passesWhenThereIsNoTestTree(@TempDir Path projectDir) {

        GateProject
            .driving(GATE, projectDir)
            .runExpectingGateToPass();
    }
}
