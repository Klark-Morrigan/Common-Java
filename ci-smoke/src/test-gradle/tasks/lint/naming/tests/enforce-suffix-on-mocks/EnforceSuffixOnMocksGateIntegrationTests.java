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

// Integration test for the shared enforce-suffix-on-mocks gate: it applies the
// real gate script into a throwaway project and runs the task, asserting the
// build outcome and the message a developer sees. Lives in the Common-Java ci-smoke project - the
// gate script is shared by every consumer; the tests live here beside it. Fixtures load from
// .txt files under java/ and kotlin/ subfolders so the gate, which scans
// src/test, never sees a violating line here; this tree is src/test-gradle,
// deliberately out of its reach. The variable-suffix rule applies to both
// languages, so the suffixed-pass, bare-local, and bare-static cases run in
// each; the lateinit, named-argument, and apply-block cases are Kotlin-only
// syntax with no Java analogue. Default package: the grouping folder is the
// source root, and its kebab name cannot be a Java package.
class EnforceSuffixOnMocksGateIntegrationTests {
    private static final String TASK_PATH = ":enforceSuffixOnMocks";

    @Test
    void passesWhenMockVariablesAreSuffixedInJava(@TempDir Path projectDir) throws IOException {
        writeJavaSource(projectDir, "mock-variables-are-suffixed");

        var result = runGate(projectDir, false);

        assertThat(result.task(TASK_PATH).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void failsWhenMockVariableIsBareNamedInJava(@TempDir Path projectDir) throws IOException {
        writeJavaSource(projectDir, "mock-variable-is-bare-named");

        var result = runGate(projectDir, true);

        assertThat(result.getOutput())
                .contains("'sector'")
                .contains("holds a mock");
    }

    @Test
    void failsWhenStaticMockIsBareNamedInJava(@TempDir Path projectDir) throws IOException {
        writeJavaSource(projectDir, "static-mock-is-bare-named");

        var result = runGate(projectDir, true);

        assertThat(result.getOutput())
                .contains("'global'")
                .contains("holds a mock");
    }

    @Test
    void passesWhenMockVariablesAreSuffixedInKotlin(@TempDir Path projectDir) throws IOException {
        writeKotlinSource(projectDir, "mock-variables-are-suffixed");

        var result = runGate(projectDir, false);

        assertThat(result.task(TASK_PATH).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void failsWhenMockVariableIsBareNamedInKotlin(@TempDir Path projectDir) throws IOException {
        writeKotlinSource(projectDir, "mock-variable-is-bare-named");

        var result = runGate(projectDir, true);

        assertThat(result.getOutput())
                .contains("'sector'")
                .contains("holds a mock");
    }

    @Test
    void failsWhenStaticMockIsBareNamedInKotlin(@TempDir Path projectDir) throws IOException {
        writeKotlinSource(projectDir, "static-mock-is-bare-named");

        var result = runGate(projectDir, true);

        assertThat(result.getOutput())
                .contains("'global'")
                .contains("holds a mock");
    }

    @Test
    void failsWhenKotlinLateinitFieldIsBareNamed(@TempDir Path projectDir) throws IOException {
        writeKotlinSource(projectDir, "lateinit-field-is-bare-named");

        var result = runGate(projectDir, true);

        assertThat(result.getOutput())
                .contains("'sector'")
                .contains("holds a mock");
    }

    // A type annotation puts the type, not the holder, before '=' - the gate
    // must still read the declared name and flag it.
    @Test
    void failsWhenKotlinTypedDeclarationIsBareNamed(@TempDir Path projectDir) throws IOException {
        writeKotlinSource(projectDir, "typed-declaration-is-bare-named");

        var result = runGate(projectDir, true);

        assertThat(result.getOutput())
                .contains("'sector'")
                .contains("holds a mock");
    }

    // The mockito-kotlin reified 'mock<T>()' form ends the call with '<', not
    // '(' - the gate's factory anchor admits both.
    @Test
    void failsWhenKotlinReifiedMockIsBareNamed(@TempDir Path projectDir) throws IOException {
        writeKotlinSource(projectDir, "reified-mock-is-bare-named");

        var result = runGate(projectDir, true);

        assertThat(result.getOutput())
                .contains("'faction'")
                .contains("holds a mock");
    }

    // A Kotlin named argument reuses the 'name = mock(...)' shape but names a
    // constructor parameter, not a holder the test can rename - the gate must
    // leave it alone.
    @Test
    void passesWhenMockIsANamedArgument(@TempDir Path projectDir) throws IOException {
        writeKotlinSource(projectDir, "mock-as-named-argument-is-ignored");

        var result = runGate(projectDir, false);

        assertThat(result.task(TASK_PATH).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }

    // 'intel = mock(...)' inside an apply block sets a production property on a
    // domain object; the name is not the test's to rename, so the gate ignores
    // it even though the value is a mock.
    @Test
    void passesWhenMockIsSetOnAnAppliedProperty(@TempDir Path projectDir) throws IOException {
        writeKotlinSource(projectDir, "mock-on-applied-property-is-ignored");

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
                .withArguments("enforceSuffixOnMocks");

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
        return System.getProperty("mock.gate.script.path").replace('\\', '/');
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
