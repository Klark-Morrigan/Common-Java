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

// Integration test for the shared enforce-tests-nested gate: it applies the
// real gate script into a throwaway project and runs the task, asserting the
// build outcome and the message a developer sees. Lives in the Common-Java ci-smoke project - the
// gate script is shared by every consumer; the tests live here beside it. Fixtures load from
// .txt files under java/ and kotlin/ subfolders so the gate, which scans
// src/test, never sees a violating line here; this tree is src/test-gradle,
// deliberately out of its reach. The nested/top-level rule and the
// literal-stripping that protects it run in both languages; the Kotlin literals
// fixture uses a """ raw string and the backtick fixture an escaped identifier,
// both Kotlin-only lexical forms with no Java analogue. Default package: the
// grouping folder is the source root, and its kebab name cannot be a Java
// package.
class EnforceTestsNestedGateIntegrationTests {
    private static final String TASK_PATH = ":enforceTestsNested";

    @Test
    void passesWhenTestIsNestedInJava(@TempDir Path projectDir) throws IOException {
        writeJavaSource(projectDir, "nested-test-passes");

        var result = runGate(projectDir, false);

        assertThat(result.task(TASK_PATH).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void failsWhenTestIsAtTopLevelInJava(@TempDir Path projectDir) throws IOException {
        writeJavaSource(projectDir, "top-level-test-is-flagged");

        var result = runGate(projectDir, true);

        assertThat(result.getOutput()).contains("must live inside a @Nested class");
    }

    @Test
    void ignoresAnnotationsAndBracesInLiteralsInJava(@TempDir Path projectDir) throws IOException {
        writeJavaSource(projectDir, "literals-do-not-confuse-the-scan");

        var result = runGate(projectDir, false);

        assertThat(result.task(TASK_PATH).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenTestIsNestedInKotlin(@TempDir Path projectDir) throws IOException {
        writeKotlinSource(projectDir, "nested-test-passes");

        var result = runGate(projectDir, false);

        assertThat(result.task(TASK_PATH).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void failsWhenTestIsAtTopLevelInKotlin(@TempDir Path projectDir) throws IOException {
        writeKotlinSource(projectDir, "top-level-test-is-flagged");

        var result = runGate(projectDir, true);

        assertThat(result.getOutput()).contains("must live inside a @Nested class");
    }

    @Test
    void ignoresAnnotationsAndBracesInLiteralsInKotlin(@TempDir Path projectDir) throws IOException {
        // The Kotlin fixture hides the stray braces and @Test inside a """ raw
        // string that spans lines, exercising the gate's cross-line raw-string
        // threading - the Java counterpart can only reach the single-line
        // string-literal path.
        writeKotlinSource(projectDir, "literals-do-not-confuse-the-scan");

        var result = runGate(projectDir, false);

        assertThat(result.task(TASK_PATH).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void failsWhenKotlinNestedClassIsNotInner(@TempDir Path projectDir) throws IOException {
        // A Kotlin @Nested class declared as a plain 'class' (not 'inner')
        // compiles but is silently skipped by JUnit5, so the gate must flag it
        // even though the @Test is technically inside a @Nested class.
        writeKotlinSource(projectDir, "nested-without-inner-is-flagged");

        var result = runGate(projectDir, true);

        assertThat(result.getOutput()).contains("must be declared `inner class`");
    }

    @Test
    void ignoresApostropheInKotlinBacktickTestName(@TempDir Path projectDir) throws IOException {
        // A Kotlin backtick test name can hold an apostrophe (calculator's),
        // which must not be read as a char-literal opener that swallows the
        // method brace and drifts the scan into flagging later nested tests.
        writeKotlinSource(projectDir, "backtick-name-with-apostrophe-passes");

        var result = runGate(projectDir, false);

        assertThat(result.task(TASK_PATH).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
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
                .withArguments("enforceTestsNested");

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
        return System.getProperty("nested.gate.script.path").replace('\\', '/');
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
