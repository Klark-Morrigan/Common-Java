import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Integration test for the shared enforce-camel-case-test-names gate: it applies the real gate
// script into a throwaway project and runs the task, asserting the build outcome and the message a
// developer sees. Lives in the Common-Java ci-smoke project - the gate script is shared by every
// consumer; the tests live here beside it. Fixtures load from .txt files under java/ and kotlin/
// subfolders so the gate, which scans src/test, never sees a violating line here; this tree is
// src/test-gradle, deliberately out of its reach.
//
// The two cases that matter most are the ones about what the gate must NOT read: snake_case is how
// the game names its own data, so a faction id in a string and a method named in a comment both
// have to pass, and Kotlin's backtick-quoted test names are the language's own idiom rather than an
// identifier a camelCase rule can be stated over. Default package: the grouping folder is the
// source root, and its kebab name cannot be a Java package.
class EnforceCamelCaseTestNamesGateIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest("enforceCamelCaseTestNames", "camel.case.test.names.gate.script.path");

    private static final String TASK_PATH = ":enforceCamelCaseTestNames";

    @Test
    void passesWhenTestNamesAreCamelCase(@TempDir Path projectDir) {

        var result = javaProject(projectDir, "test-names-are-camel-case")
            .runExpectingSuccess();

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void failsWhenATestNameIsSnakeCase(@TempDir Path projectDir) {

        var result = javaProject(projectDir, "test-name-is-snake-case")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("'reads_the_owner_off_the_market'")
            .contains("must be named in camelCase");
    }

    // A helper is read in a report exactly as a case is, so the rule reaches it too - and catching
    // it at the call as well as the declaration is what makes the gate's list the whole worklist.
    @Test
    void failsWhenAHelperNameIsSnakeCase(@TempDir Path projectDir) {

        var result = javaProject(projectDir, "helper-name-is-snake-case")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("'build_colony'");
    }

    // snake_case is how the game names factions, entity types and settings keys. Those are data,
    // not names the gate has any business renaming, so a literal and a comment both pass - even
    // when the literal is spelt to look like a call.
    @Test
    void passesWhenSnakeCaseIsInALiteralOrAComment(@TempDir Path projectDir) {

        var result = javaProject(projectDir, "snake-case-in-a-literal-is-ignored")
            .runExpectingSuccess();

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenAKotlinTestNameIsBacktickQuoted(@TempDir Path projectDir) {

        var result = kotlinProject(projectDir, "backtick-name-is-ignored")
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
