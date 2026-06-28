import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
    private static final String TASK_PATH = ":enforceNoMagicLiteralsKotlin";

    @Test
    void flagsAnInlineMagicNumber(@TempDir Path projectDir) throws IOException {
        writeKotlinSource(projectDir, "inline-number-is-flagged");

        var result = runGate(projectDir, true);

        assertThat(result.getOutput()).contains("MagicNumber");
    }

    @Test
    void passesWhenNumbersAreNamed(@TempDir Path projectDir) throws IOException {
        writeKotlinSource(projectDir, "named-numbers-pass");

        var result = runGate(projectDir, false);

        assertThat(result.task(TASK_PATH).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }

    private BuildResult runGate(Path projectDir, boolean expectFailure) throws IOException {
        // An explicit settings file stops Gradle walking up into a real build.
        Files.writeString(projectDir.resolve("settings.gradle"),
                "rootProject.name = 'gate-fixture'\n");
        // mavenCentral is the one piece of the real chain the gate needs: it
        // resolves the detekt CLI the gate runs. Nothing else is required - the
        // gate scans Kotlin source text, no compile or plugin.
        Files.writeString(projectDir.resolve("build.gradle"),
                "repositories { mavenCentral() }\n"
                + "apply from: '" + scriptPath() + "'\n");

        var runner = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withArguments("enforceNoMagicLiteralsKotlin");

        return expectFailure ? runner.buildAndFail() : runner.build();
    }

    private void writeKotlinSource(Path projectDir, String fixture) throws IOException {
        var dir = projectDir.resolve("src/main/kotlin");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Sample.kt"), loadFixture("kotlin/" + fixture));
    }

    // The gate script path is handed in by the test task so the test does not
    // assume a working directory; forward slashes keep it valid inside the
    // generated build script on Windows.
    private String scriptPath() {
        return System.getProperty("kotlin.literals.gate.script.path").replace('\\', '/');
    }

    private String loadFixture(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/" + name + ".txt")) {
            if (in == null) {
                throw new IllegalStateException("Missing fixture: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
