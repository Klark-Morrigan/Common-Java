import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Drives the shared enforce-no-magic-literals-kotlin gate with its real detekt.yml, which the gate
// resolves relative to its own location. Each detekt run is a real CLI invocation, so these cases
// are heavier than the pure-Groovy gate suites.
class EnforceNoMagicLiteralsKotlinGateIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest(
            "enforceNoMagicLiteralsKotlin",
            "lint/literals/enforce-no-magic-literals-kotlin");

    // The one piece of a consumer's build the gate needs beyond the java plugin: it resolves the
    // detekt CLI it runs from there.
    private static final String DECLARES_MAVEN_CENTRAL = "repositories { mavenCentral() }\n";

    // A Kotlin tree reaches the gate the way it reaches the compiler: because a source set declares
    // it. The Kotlin plugin does that in a real Kotlin build; declaring it by hand exercises the
    // same contract without resolving that plugin.
    private static final String KOTLIN_TREE_ON_MAIN_SOURCE_SET =
        "sourceSets.main.java.srcDir '" + GateProject.MAIN_KOTLIN_ROOT + "'\n";

    private static final String KOTLIN_TREE_ON_TEST_SOURCE_SET =
        "sourceSets.test.java.srcDir '" + GateProject.TEST_KOTLIN_ROOT + "'\n";

    private static final String TOOLING_KOTLIN_ROOT = "src/tooling/kotlin";

    private static final String DECLARES_A_TOOLING_SOURCE_SET =
        "sourceSets { tooling { java.srcDirs = ['" + TOOLING_KOTLIN_ROOT + "'] } }\n";

    // Where detekt reports the fixture's inline number, and the rule it names.
    private static final String INLINE_NUMBER_FINDING = "Sample.kt:2:";
    private static final String MAGIC_NUMBER_RULE = "[MagicNumber]";

    @Test
    void failsOnAnInlineMagicNumber(@TempDir Path projectDir) {

        var result = project(projectDir)
            .withBuildPreamble(KOTLIN_TREE_ON_MAIN_SOURCE_SET)
            .holdingKotlinSample(GateProject.MAIN_KOTLIN_ROOT, "inline-number-is-flagged")
            .runExpectingGateToFail();

        assertThat(result.getOutput())
            .contains(INLINE_NUMBER_FINDING)
            .contains(MAGIC_NUMBER_RULE);
    }

    @Test
    void passesWhenNumbersAreNamed(@TempDir Path projectDir) {

        project(projectDir)
            .withBuildPreamble(KOTLIN_TREE_ON_MAIN_SOURCE_SET)
            .holdingKotlinSample(GateProject.MAIN_KOTLIN_ROOT, "named-numbers-pass")
            .runExpectingGateToPass();
    }

    @Test
    void skipsAProjectWithNoKotlin(@TempDir Path projectDir) {

        var result = project(projectDir)
            .runExpectingSuccess();

        assertThat(result.task(GATE.taskPath()).getOutcome())
            .isEqualTo(TaskOutcome.SKIPPED);
    }

    @Test
    void leavesTheTestSourceSetUnjudged(@TempDir Path projectDir) {

        // A clean main sample, so detekt runs and is seen to pass over the test tree's number.
        project(projectDir)
            .withBuildPreamble(KOTLIN_TREE_ON_MAIN_SOURCE_SET + KOTLIN_TREE_ON_TEST_SOURCE_SET)
            .holdingKotlinSample(GateProject.MAIN_KOTLIN_ROOT, "named-numbers-pass")
            .holdingKotlinSample(GateProject.TEST_KOTLIN_ROOT, "inline-number-is-flagged")
            .runExpectingGateToPass();
    }

    @Test
    void judgesKotlinFiledUnderTheJavaTree(@TempDir Path projectDir) {

        // A source set's Kotlin is judged wherever the source set keeps it, not only under kotlin/.
        var result = project(projectDir)
            .holdingKotlinSample(GateProject.MAIN_JAVA_ROOT, "inline-number-is-flagged")
            .runExpectingGateToFail();

        assertThat(result.getOutput())
            .contains(INLINE_NUMBER_FINDING)
            .contains(MAGIC_NUMBER_RULE);
    }

    @Test
    void judgesKotlinInASourceSetTheBuildDeclares(@TempDir Path projectDir) {

        var result = project(projectDir)
            .configuringTheGate(DECLARES_A_TOOLING_SOURCE_SET)
            .holdingKotlinSample(TOOLING_KOTLIN_ROOT, "inline-number-is-flagged")
            .runExpectingGateToFail();

        assertThat(result.getOutput())
            .contains(INLINE_NUMBER_FINDING)
            .contains(MAGIC_NUMBER_RULE);
    }

    @Test
    void refusesABuildWithoutTheJavaPlugin(@TempDir Path projectDir) {

        // Refused before the gate can run, so there is no gate outcome to read: only the message.
        var result = GateProject
            .driving(GATE, projectDir)
            .withBuildPreamble(DECLARES_MAVEN_CENTRAL)
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("enforceNoMagicLiteralsKotlin needs the java plugin");
    }

    private static GateProject project(Path projectDir) {

        return GateProject
            .driving(GATE, projectDir)
            .applyingTheJavaPlugin()
            .withBuildPreamble(DECLARES_MAVEN_CENTRAL);
    }
}
