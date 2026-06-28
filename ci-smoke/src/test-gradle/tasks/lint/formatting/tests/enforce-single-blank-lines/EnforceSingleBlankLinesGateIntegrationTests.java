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

// Integration test for the shared enforce-single-blank-lines gate: it applies
// the real gate script into a throwaway project and runs the task, asserting
// the build outcome and the message a developer sees. Lives in the Common-Java ci-smoke project - the
// gate script is shared by every consumer; the tests live here beside it. Fixtures load from
// .txt files under java/ and kotlin/ subfolders, so the gate, which scans
// src/test, never sees a violating line here; this tree is src/test-gradle,
// deliberately out of its reach. Default package: the grouping folder is the
// source root, and its kebab name cannot be a Java package.
class EnforceSingleBlankLinesGateIntegrationTests {
    private static final String TASK_PATH = ":enforceSingleBlankLines";

    @Test
    void passesWhenBlankLinesAreSingleInJava(@TempDir Path projectDir) throws IOException {
        writeJavaSource(projectDir, "single-blank-separators");

        var result = runGate(projectDir, false);

        assertThat(result.task(TASK_PATH).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void failsOnTwoConsecutiveBlankLinesInJava(@TempDir Path projectDir) throws IOException {
        writeJavaSource(projectDir, "two-consecutive-blank-lines");

        var result = runGate(projectDir, true);

        assertThat(result.getOutput()).contains("consecutive blank line");
    }

    @Test
    void treatsWhitespaceOnlyLineAsBlankInJava(@TempDir Path projectDir) throws IOException {
        // An empty line followed by a spaces-only line is two blanks in a row:
        // the gate trims before testing emptiness, so the spaces-only line must
        // count toward the run rather than reset it.
        writeJavaSource(projectDir, "whitespace-only-line-counts-as-blank");

        var result = runGate(projectDir, true);

        assertThat(result.getOutput()).contains("consecutive blank line");
    }

    @Test
    void passesWhenBlankLinesAreSingleInKotlin(@TempDir Path projectDir) throws IOException {
        writeKotlinSource(projectDir, "single-blank-separators");

        var result = runGate(projectDir, false);

        assertThat(result.task(TASK_PATH).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void failsOnTwoConsecutiveBlankLinesInKotlin(@TempDir Path projectDir) throws IOException {
        // Doubles as the proof the scan reaches .kt, not only .java.
        writeKotlinSource(projectDir, "two-consecutive-blank-lines");

        var result = runGate(projectDir, true);

        assertThat(result.getOutput()).contains("consecutive blank line");
    }

    @Test
    void treatsWhitespaceOnlyLineAsBlankInKotlin(@TempDir Path projectDir) throws IOException {
        writeKotlinSource(projectDir, "whitespace-only-line-counts-as-blank");

        var result = runGate(projectDir, true);

        assertThat(result.getOutput()).contains("consecutive blank line");
    }

    @Test
    void passesWhenThereIsNoTestTree(@TempDir Path projectDir) throws IOException {
        var result = runGate(projectDir, false);

        assertThat(result.task(TASK_PATH).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }

    private BuildResult runGate(Path projectDir, boolean expectFailure) throws IOException {
        // An explicit settings file stops Gradle walking up into a real build.
        Files.writeString(projectDir.resolve("settings.gradle"),
                "rootProject.name = 'gate-fixture'\n");
        Files.writeString(projectDir.resolve("build.gradle"),
                "apply from: '" + scriptPath() + "'\n");

        var runner = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withArguments("enforceSingleBlankLines");

        return expectFailure ? runner.buildAndFail() : runner.build();
    }

    private void writeJavaSource(Path projectDir, String fixture) throws IOException {
        var dir = projectDir.resolve("src/test/java");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Sample.java"), loadFixture("java/" + fixture));
    }

    private void writeKotlinSource(Path projectDir, String fixture) throws IOException {
        var dir = projectDir.resolve("src/test/kotlin");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Sample.kt"), loadFixture("kotlin/" + fixture));
    }

    // The gate script path is handed in by the test task so the test does not
    // assume a working directory; forward slashes keep it valid inside the
    // generated build script on Windows.
    private String scriptPath() {
        return System.getProperty("single.blank.gate.script.path").replace('\\', '/');
    }

    // The fixture name carries its language subfolder (java/ or kotlin/), which
    // resolves under the classpath root the source set exposes for resources.
    private String loadFixture(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/" + name + ".txt")) {
            if (in == null) {
                throw new IllegalStateException("Missing fixture: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
