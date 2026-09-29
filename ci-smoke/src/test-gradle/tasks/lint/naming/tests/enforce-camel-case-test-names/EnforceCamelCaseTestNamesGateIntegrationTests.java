import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Drives the shared enforce-camel-case-test-names gate.
//
// The two cases that matter most are the ones about what the gate must NOT read: snake_case is how
// the game names its own data, so a faction ID in a string and a method named in a comment both
// have to pass, and Kotlin's backtick-quoted test names are the language's own idiom rather than an
// identifier a camelCase rule can be stated over.
class EnforceCamelCaseTestNamesGateIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest("enforceCamelCaseTestNames", "camel.case.test.names.gate.script.path");

    @Test
    void passesWhenTestNamesAreCamelCase(@TempDir Path projectDir) {

        GateProject
            .driving(GATE, projectDir)
            .holdingJavaTestSample("test-names-are-camel-case")
            .runExpectingGateToPass();
    }

    @Test
    void failsWhenATestNameIsSnakeCase(@TempDir Path projectDir) {

        var result = GateProject
            .driving(GATE, projectDir)
            .holdingJavaTestSample("test-name-is-snake-case")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("'reads_the_owner_off_the_market'")
            .contains("must be named in camelCase");
    }

    // A helper is read in a report exactly as a case is, so the rule reaches it too - and catching
    // it at the call as well as the declaration is what makes the gate's list the whole worklist.
    @Test
    void failsWhenAHelperNameIsSnakeCase(@TempDir Path projectDir) {

        var result = GateProject
            .driving(GATE, projectDir)
            .holdingJavaTestSample("helper-name-is-snake-case")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("'build_colony'");
    }

    // snake_case is how the game names factions, entity types and settings keys. Those are data,
    // not names the gate has any business renaming, so a literal and a comment both pass - even
    // when the literal is spelt to look like a call.
    @Test
    void passesWhenSnakeCaseIsInALiteralOrAComment(@TempDir Path projectDir) {

        GateProject
            .driving(GATE, projectDir)
            .holdingJavaTestSample("snake-case-in-a-literal-is-ignored")
            .runExpectingGateToPass();
    }

    // A Kotlin raw string runs across lines, and a line inside it carries no quote of its own for a
    // line-at-a-time strip to see, so passing here proves the strip carries the string's open state
    // from one line to the next.
    @Test
    void passesWhenSnakeCaseIsInAKotlinRawString(@TempDir Path projectDir) {

        GateProject
            .driving(GATE, projectDir)
            .holdingKotlinTestSample("snake-case-in-a-raw-string-is-ignored")
            .runExpectingGateToPass();
    }

    @Test
    void passesWhenAKotlinTestNameIsBacktickQuoted(@TempDir Path projectDir) {

        GateProject
            .driving(GATE, projectDir)
            .holdingKotlinTestSample("backtick-name-is-ignored")
            .runExpectingGateToPass();
    }

    @Test
    void passesWhenThereIsNoTestTree(@TempDir Path projectDir) {

        GateProject
            .driving(GATE, projectDir)
            .runExpectingGateToPass();
    }
}
