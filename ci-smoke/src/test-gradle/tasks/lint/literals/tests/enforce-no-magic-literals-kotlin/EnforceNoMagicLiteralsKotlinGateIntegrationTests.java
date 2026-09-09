import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Integration test for the shared enforce-no-magic-literals-kotlin gate: it
// applies the real gate (and its real detekt.yml, resolved by the gate relative
// to its own location) into a throwaway project and runs the task, asserting the
// outcome and the rule a developer sees. Lives in the Common-Java ci-smoke project - the gate is
// shared by every consumer; the tests live here beside it. Fixtures load from .txt files under
// kotlin/ so this src/test-gradle tree never holds a violating .kt line.
//
// The throwaway build supplies only mavenCentral, which java-conventions
// normally provides: the gate runs the detekt CLI as a resolved dependency, so
// it must be fetchable. The gate is not group-guarded (it no-ops on the absence
// of src/main/kotlin), so the fixture only needs the Kotlin source it writes.
// Each detekt run is a real CLI invocation, so these tests are heavier than the
// pure-Groovy gate tests. Default package: the grouping folder is the source
// root, and its kebab name cannot be a Java package.
class EnforceNoMagicLiteralsKotlinGateIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest(
            "enforceNoMagicLiteralsKotlin",
            "kotlin.literals.gate.script.path");

    private static final String TASK_PATH = ":enforceNoMagicLiteralsKotlin";
    @Test
    void flagsAnInlineMagicNumber(@TempDir Path projectDir) {

        var result = kotlinProject(projectDir, "inline-number-is-flagged")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("MagicNumber");
    }

    @Test
    void passesWhenNumbersAreNamed(@TempDir Path projectDir) {

        var result = kotlinProject(projectDir, "named-numbers-pass")
            .runExpectingSuccess();

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    private static GateProject kotlinProject(Path projectDir, String fixture) {
        // mavenCentral is the one piece of the real chain the gate needs: it resolves the detekt
        // CLI the gate runs. Nothing else is required - the gate scans Kotlin source text, no
        // compile or plugin.
        return GateProject
            .driving(GATE, projectDir)
            .withBuildPreamble("repositories { mavenCentral() }\n")
            .holdingFixture("src/main/kotlin/Sample.kt", "kotlin/" + fixture);
    }
}
