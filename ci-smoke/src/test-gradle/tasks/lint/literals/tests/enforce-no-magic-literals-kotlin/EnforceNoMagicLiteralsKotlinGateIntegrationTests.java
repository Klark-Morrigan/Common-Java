import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Drives the shared enforce-no-magic-literals-kotlin gate with its real detekt.yml, which the gate
// resolves relative to its own location.
//
// The throwaway build supplies only mavenCentral, which java-conventions normally provides: the gate
// runs the detekt CLI as a resolved dependency, so it must be fetchable. The gate is not
// group-guarded (it no-ops on the absence of src/main/kotlin), so the fixture only needs the Kotlin
// source it writes. Each detekt run is a real CLI invocation, so these cases are heavier than the
// pure-Groovy gate suites.
class EnforceNoMagicLiteralsKotlinGateIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest(
            "enforceNoMagicLiteralsKotlin",
            "kotlin.literals.gate.script.path");

    @Test
    void flagsAnInlineMagicNumber(@TempDir Path projectDir) {

        var result = kotlinProject(projectDir, "inline-number-is-flagged")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("MagicNumber");
    }

    @Test
    void passesWhenNumbersAreNamed(@TempDir Path projectDir) {

        kotlinProject(projectDir, "named-numbers-pass")
            .runExpectingGateToPass();
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
