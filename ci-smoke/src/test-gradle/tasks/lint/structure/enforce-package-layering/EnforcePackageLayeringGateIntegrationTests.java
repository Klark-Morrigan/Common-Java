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

// Integration test for the shared enforce-package-layering gate: it applies the
// real gate script into a throwaway project and runs the task, asserting the
// build outcome and the message a developer sees. Lives in the Common-Java
// ci-smoke project - the gate script is shared by every consumer, so the tests
// live here beside it. Fixtures load from .txt files under java/ and kotlin/
// subfolders so the gate, which scans src/main and src/test, never sees a
// fixture as real source here; this tree is src/test-gradle, deliberately out
// of its reach.
//
// The fixture packages are 'example.framework' and 'example.feature' rather
// than any real consumer's, because the gate owns only the mechanism and this
// repo knows no consumer's layers. Both trees are covered, since the rule the
// gate exists for is broken as easily by a test reaching across the line as by
// production code. Default package: the grouping folder is the source root, and
// its kebab name cannot be a Java package.
class EnforcePackageLayeringGateIntegrationTests {

    private static final String TASK_PATH = ":enforcePackageLayering";

    // What the consuming build declares, and what the fixtures are written
    // against: the framework root is closed to the feature root.
    private static final String CLOSED_PACKAGE_ROOT = "example.framework";
    private static final String FORBIDDEN_PACKAGE_ROOT = "example.feature";

    @Test
    void failsWhenProductionCodeImportsTheForbiddenRoot(@TempDir Path projectDir)
            throws IOException {

        writeJavaSource(projectDir, "src/main/java", "framework-importing-the-feature");

        var result = runGate(projectDir, true, true);

        assertThat(result.getOutput())
            .contains("example.framework may not import example.feature: "
                + "example.feature.view.FeatureView");
    }

    @Test
    void failsWhenTestCodeImportsTheForbiddenRoot(@TempDir Path projectDir)
            throws IOException {
        // The half most likely to slip: a suite reaching for a real type from
        // the far side is the shortest way to make it compile, and it inverts
        // the same arrow production code would.
        writeJavaSource(projectDir, "src/test/java", "framework-importing-the-feature");

        var result = runGate(projectDir, true, true);

        assertThat(result.getOutput())
            .contains("may not import example.feature");
    }

    @Test
    void failsWhenTheForbiddenRootArrivesByWildcardOrStaticImport(@TempDir Path projectDir)
            throws IOException {

        // Two import forms the plain 'import x.y.Z;' pattern does not cover: an
        // on-demand import keeps a trailing star, and a static one carries a
        // keyword between 'import' and the name.
        writeJavaSource(projectDir, "src/main/java", "wildcard-and-static-imports");

        var result = runGate(projectDir, true, true);

        assertThat(result.getOutput())
            .contains("example.feature.view.FeatureView.DEFAULT_VIEW")
            .contains("example.feature.view.*");
    }

    @Test
    void failsWhenKotlinCodeImportsTheForbiddenRoot(@TempDir Path projectDir)
            throws IOException {

        // Kotlin leaves the semicolon off its package declaration and may alias
        // an import, so both ends of the line differ from the Java shape.
        var dir = projectDir.resolve("src/main/kotlin");

        Files.createDirectories(dir);
        Files.writeString(
            dir.resolve("Sample.kt"),
            loadFixture("kotlin/framework-importing-the-feature"));

        var result = runGate(projectDir, true, true);

        assertThat(result.getOutput())
            .contains("may not import example.feature");
    }

    @Test
    void passesWhenNothingImportsTheForbiddenRoot(@TempDir Path projectDir)
            throws IOException {

        writeJavaSource(projectDir, "src/main/java", "framework-importing-nothing-forbidden");

        var result = runGate(projectDir, false, true);

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenTheImportingPackageOnlySharesAPrefix(@TempDir Path projectDir)
            throws IOException {

        // 'example.frameworkish' is not under 'example.framework', so the rule
        // does not reach it - only a whole-segment match may close a package.
        writeJavaSource(projectDir, "src/main/java", "package-sharing-a-prefix");

        var result = runGate(projectDir, false, true);

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenTheImportedNameOnlySharesAPrefix(@TempDir Path projectDir)
            throws IOException {

        // The same segment rule on the other side: 'example.featureless' is a
        // different package from 'example.feature' despite the string prefix.
        writeJavaSource(projectDir, "src/main/java", "import-sharing-a-prefix");

        var result = runGate(projectDir, false, true);

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenTheBuildDeclaresNoEdge(@TempDir Path projectDir)
            throws IOException {

        // Every consumer inherits the gate from the shared conventions, so a
        // build that never says what its layers are must check nothing rather
        // than guess at them.
        writeJavaSource(projectDir, "src/main/java", "framework-importing-the-feature");

        var result = runGate(projectDir, false, false);

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenThereIsNoSourceTree(@TempDir Path projectDir)
            throws IOException {

        var result = runGate(projectDir, false, true);

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    private BuildResult runGate(Path projectDir, boolean expectFailure, boolean declaresEdge)
            throws IOException {

        // An explicit settings file stops Gradle walking up into a real build.
        Files.writeString(
            projectDir.resolve("settings.gradle"),
            "rootProject.name = 'gate-fixture'\n");

        var edgeDeclaration = declaresEdge
            ? "enforcePackageLayering {\n"
                + "    forbidImport under: '" + CLOSED_PACKAGE_ROOT + "',"
                + " of: '" + FORBIDDEN_PACKAGE_ROOT + "'\n"
                + "}\n"
            : "";

        Files.writeString(
            projectDir.resolve("build.gradle"),
            "apply from: '" + scriptPath() + "'\n" + edgeDeclaration);

        var runner = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments("enforcePackageLayering");

        return expectFailure
            ? runner.buildAndFail()
            : runner.build();
    }

    // The source root is a parameter rather than fixed, because production and
    // test trees are two cases of the same rule and the gate must read both.
    private void writeJavaSource(Path projectDir, String sourceRoot, String fixture)
            throws IOException {

        var dir = projectDir.resolve(sourceRoot);

        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Sample.java"), loadFixture("java/" + fixture));
    }

    // The gate script path is handed in by the test task so the test does not
    // assume a working directory; forward slashes keep it valid inside the
    // generated build script on Windows.
    private String scriptPath() {
        return System.getProperty("package.layering.gate.script.path").replace('\\', '/');
    }

    // The fixture name carries its language subfolder (java/ or kotlin/), which
    // resolves under the classpath root the source set exposes for resources.
    private String loadFixture(String name)
            throws IOException {
                
        try (InputStream in = getClass().getResourceAsStream("/" + name + ".txt")) {
            if (in == null) {
                throw new IllegalStateException("Missing fixture: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
