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

// Integration test for the shared enforce-methods-ordered-by-visibility gate: it
// applies the real gate script into a throwaway project and runs the task,
// asserting the build outcome and the message a developer sees. Lives in the
// Common-Java ci-smoke project - the gate script is shared by every consumer, so
// the tests live here beside it. Fixtures load from .txt files under java/ and
// kotlin/ subfolders so the gate, which scans src/main, never sees a fixture as
// real source here; this tree is src/test-gradle, deliberately out of its reach.
// The ladder rule and its language-specific default-visibility split (a bare
// method is package-private in Java, public in Kotlin, and public inside a Java
// interface) are covered in both languages, along with the constructor, enum
// constant, local-function, and property-initializer exclusions. Default
// package: the grouping folder is the source root, and its kebab name cannot be
// a Java package.
class EnforceMethodsOrderedByVisibilityGateIntegrationTests {
    private static final String TASK_PATH = ":enforceMethodsOrderedByVisibility";

    @Test
    void passesWhenJavaMethodsDescendTheLadder(@TempDir Path projectDir) throws IOException {
        writeJavaSource(projectDir, "ordered-ladder-passes");

        var result = runGate(projectDir, false);

        assertThat(result.task(TASK_PATH).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void failsWhenJavaPublicMethodSitsBelowPrivate(@TempDir Path projectDir) throws IOException {
        writeJavaSource(projectDir, "public-below-private-is-flagged");

        var result = runGate(projectDir, true);

        assertThat(result.getOutput())
                .contains("public method declared below a private method");
    }

    @Test
    void ignoresJavaConstructorsAndEnumConstants(@TempDir Path projectDir) throws IOException {
        // A public constructor placed below a private method would look like a
        // ladder violation, and an enum constant with an argument list looks like
        // a bare method - both must be skipped, so this ordered fixture passes.
        writeJavaSource(projectDir, "constructor-and-enum-ignored-passes");

        var result = runGate(projectDir, false);

        assertThat(result.task(TASK_PATH).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void ignoresJavaMultiLineFieldInitializers(@TempDir Path projectDir) throws IOException {
        // A field whose value sits on the line after the '=' - a 'Type.factory(...)'
        // or a 'new Type(...)' - looks like a bare package-private method on that
        // continuation line, since the same-line '=' guard cannot see the '=' on the
        // previous line. The gate must skip a continuation line (its previous line
        // ends in '=') so the public methods below the fields are not flagged against
        // a phantom method.
        writeJavaSource(projectDir, "multiline-factory-field-initializer-ignored-passes");

        var result = runGate(projectDir, false);

        assertThat(result.task(TASK_PATH).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void flagsJavaInterfaceImplicitPublicBelowPrivate(@TempDir Path projectDir) throws IOException {
        // A no-modifier method is package-private in a class but public in an
        // interface. This fixture only fails if the gate ranks the bare interface
        // method as public and so sees it jump above the private method above it.
        writeJavaSource(projectDir, "interface-implicit-public-below-private-is-flagged");

        var result = runGate(projectDir, true);

        assertThat(result.getOutput())
                .contains("public (implicit) method declared below a private method");
    }

    @Test
    void passesWhenKotlinMethodsDescendTheLadder(@TempDir Path projectDir) throws IOException {
        writeKotlinSource(projectDir, "ordered-ladder-passes");

        var result = runGate(projectDir, false);

        assertThat(result.task(TASK_PATH).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void failsWhenKotlinPublicMethodSitsBelowPrivate(@TempDir Path projectDir) throws IOException {
        // A bare Kotlin 'fun' is public, so it must not appear below a private
        // one; the gate reports it as an implicit-public method.
        writeKotlinSource(projectDir, "public-below-private-is-flagged");

        var result = runGate(projectDir, true);

        assertThat(result.getOutput())
                .contains("public (implicit) method declared below a private method");
    }

    @Test
    void ignoresKotlinLocalFunctionsAndPropertyInitializers(@TempDir Path projectDir)
            throws IOException {
        // A 'fun' nested in a method body sits deeper than the class-body depth,
        // and a 'fun' following '=' is a function-expression property value -
        // neither is a member declaration, so this ordered fixture passes.
        writeKotlinSource(projectDir, "local-fun-and-property-init-ignored-passes");

        var result = runGate(projectDir, false);

        assertThat(result.task(TASK_PATH).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenThereIsNoMainTree(@TempDir Path projectDir) throws IOException {
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
                .withArguments("enforceMethodsOrderedByVisibility");

        return expectFailure ? runner.buildAndFail() : runner.build();
    }

    private void writeJavaSource(Path projectDir, String fixture) throws IOException {
        var dir = projectDir.resolve("src/main/java");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Sample.java"), loadFixture("java/" + fixture));
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
        return System.getProperty("methods.order.gate.script.path").replace('\\', '/');
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
