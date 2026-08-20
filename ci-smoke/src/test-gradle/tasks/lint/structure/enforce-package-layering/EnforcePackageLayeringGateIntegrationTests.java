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
// subfolders so the gate, which scans every source set, never sees a fixture as
// real source here; this tree is src/test-gradle, deliberately out of its reach.
//
// The fixture packages are 'example.framework' and 'example.feature' rather
// than any real consumer's, because the gate owns only the mechanism and this
// repo knows no consumer's layers. Default package: the grouping folder is the
// source root, and its kebab name cannot be a Java package.
class EnforcePackageLayeringGateIntegrationTests {

    private static final String TASK_PATH = ":enforcePackageLayering";

    // What the consuming build declares, and what the fixtures are written
    // against: the framework root is closed to the feature root.
    private static final String CLOSED_PACKAGE_ROOT = "example.framework";
    private static final String FORBIDDEN_PACKAGE_ROOT = "example.feature";
    private static final String SECOND_FORBIDDEN_PACKAGE_ROOT = "example.otherfeature";

    private static final String NO_EDGE_DECLARED = "";
    private static final String CLOSED_TO_THE_FEATURE = buildEdgeDeclaration(
        "under: '" + CLOSED_PACKAGE_ROOT + "', of: '" + FORBIDDEN_PACKAGE_ROOT + "'");

    private static final String CLOSED_TO_BOTH_FEATURES = buildEdgeDeclaration(
        "under: '" + CLOSED_PACKAGE_ROOT + "', of: '" + FORBIDDEN_PACKAGE_ROOT + "'",
        "under: '" + CLOSED_PACKAGE_ROOT + "', of: '" + SECOND_FORBIDDEN_PACKAGE_ROOT + "'");

    // The same rule at the narrower width: the closed package alone, with its
    // own subpackages left free to import what it may not.
    private static final String CLOSED_EXACTLY_TO_THE_FEATURE = buildEdgeDeclaration(
        "exactly: '" + CLOSED_PACKAGE_ROOT + "', of: '" + FORBIDDEN_PACKAGE_ROOT + "'");

    // An edge naming only the closed root, which says nothing about what it is
    // closed to - the shape a misspelt or forgotten 'of:' takes.
    private static final String HALF_DECLARED_EDGE = buildEdgeDeclaration(
        "under: '" + CLOSED_PACKAGE_ROOT + "'");

    // An edge closing one root at both widths at once, which states two
    // different rules and cannot mean both.
    private static final String EDGE_CLOSED_AT_BOTH_WIDTHS = buildEdgeDeclaration(
        "under: '" + CLOSED_PACKAGE_ROOT + "', exactly: '" + CLOSED_PACKAGE_ROOT
            + "', of: '" + FORBIDDEN_PACKAGE_ROOT + "'");

    @Test
    void failsWhenProductionCodeImportsTheForbiddenRoot(@TempDir Path projectDir)
            throws IOException {

        writeJavaSource(projectDir, "src/main/java", "framework-importing-the-feature");

        var result = runGateExpectingFailure(projectDir, CLOSED_TO_THE_FEATURE);

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

        var result = runGateExpectingFailure(projectDir, CLOSED_TO_THE_FEATURE);

        assertThat(result.getOutput())
            .contains("may not import example.feature");
    }

    @Test
    void failsWhenASourceSetBeyondMainAndTestImportsTheForbiddenRoot(@TempDir Path projectDir)
            throws IOException {

        // A mod's own extra source set - a dev tool, a stub tree - sits in the
        // same packages as the code it is built from, so exempting it would
        // leave the rule enforced everywhere except where nobody was looking.
        writeJavaSource(projectDir, "src/utils/java", "framework-importing-the-feature");

        var result = runGateExpectingFailure(projectDir, CLOSED_TO_THE_FEATURE);

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

        var result = runGateExpectingFailure(projectDir, CLOSED_TO_THE_FEATURE);

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

        var result = runGateExpectingFailure(projectDir, CLOSED_TO_THE_FEATURE);

        assertThat(result.getOutput())
            .contains("may not import example.feature");
    }

    @Test
    void failsOnAnEdgeBeyondTheFirstDeclared(@TempDir Path projectDir)
            throws IOException {

        // Two edges, and the violation is of the second: a gate that honoured
        // only the first declaration would pass every single-edge case above.
        writeJavaSource(projectDir, "src/main/java", "framework-importing-the-second-feature");

        var result = runGateExpectingFailure(projectDir, CLOSED_TO_BOTH_FEATURES);

        assertThat(result.getOutput())
            .contains("may not import example.otherfeature");
    }

    @Test
    void failsWhenAnEdgeIsDeclaredWithoutTheRootItCloses(@TempDir Path projectDir)
            throws IOException {

        // A half-declared edge asserts nothing, so it must be rejected where it
        // is written rather than accepted as a rule that can never fire.
        writeJavaSource(projectDir, "src/main/java", "framework-importing-the-feature");

        var result = runGateExpectingFailure(projectDir, HALF_DECLARED_EDGE);

        assertThat(result.getOutput())
            .contains("forbidImport needs the package root that is closed");
    }

    @Test
    void failsWhenTheClosedPackageItselfImportsTheForbiddenRootAtTheNarrowWidth(
            @TempDir Path projectDir) throws IOException {

        // The package named is closed at either width; what differs is how far
        // the rule reaches beneath it.
        writeJavaSource(projectDir, "src/main/java", "framework-root-importing-the-feature");

        var result = runGateExpectingFailure(projectDir, CLOSED_EXACTLY_TO_THE_FEATURE);

        assertThat(result.getOutput())
            .contains("example.framework itself may not import example.feature: "
                + "example.feature.view.FeatureView");
    }

    @Test
    void failsWhenTheClosedPackageItselfImportsTheForbiddenRootAtTheWideWidth(
            @TempDir Path projectDir) throws IOException {

        // The root of a closed subtree is inside it, so the wide width reaches
        // the same file the narrow one does - the two differ only below it.
        writeJavaSource(projectDir, "src/main/java", "framework-root-importing-the-feature");

        var result = runGateExpectingFailure(projectDir, CLOSED_TO_THE_FEATURE);

        assertThat(result.getOutput())
            .contains("example.framework may not import example.feature");
    }

    @Test
    void failsWhenAnEdgeClosesItsRootAtBothWidthsAtOnce(@TempDir Path projectDir)
            throws IOException {

        // Two different rules over one pair of roots. Reading either and
        // ignoring the other would enforce something nobody declared.
        writeJavaSource(projectDir, "src/main/java", "framework-importing-the-feature");

        var result = runGateExpectingFailure(projectDir, EDGE_CLOSED_AT_BOTH_WIDTHS);

        assertThat(result.getOutput())
            .contains("forbidImport closes a package root one way or the other");
    }

    @Test
    void failsAgainAfterAPassingRunOnceAnEdgeIsDeclared(@TempDir Path projectDir)
            throws IOException {

        // The edges are a task input, not just a closure the action reads. If
        // they were not, this second run would be UP-TO-DATE against the first
        // and a newly declared rule would silently never run.
        writeJavaSource(projectDir, "src/main/java", "framework-importing-the-feature");
        runGateExpectingSuccess(projectDir, NO_EDGE_DECLARED);

        var result = runGateExpectingFailure(projectDir, CLOSED_TO_THE_FEATURE);

        assertThat(result.getOutput())
            .contains("may not import example.feature");
    }

    @Test
    void passesWhenNothingImportsTheForbiddenRoot(@TempDir Path projectDir)
            throws IOException {

        writeJavaSource(projectDir, "src/main/java", "framework-importing-nothing-forbidden");

        var result = runGateExpectingSuccess(projectDir, CLOSED_TO_THE_FEATURE);

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenOnlyASubpackageOfTheNarrowlyClosedPackageImportsTheForbiddenRoot(
            @TempDir Path projectDir) throws IOException {

        // The whole reason the narrow width exists. A package closed to one of
        // its own subpackages cannot be closed as a subtree, or the rule would
        // shut that subpackage off from its siblings too - and there would be
        // no way to state the edge at all.
        writeJavaSource(projectDir, "src/main/java", "framework-importing-the-feature");

        var result = runGateExpectingSuccess(projectDir, CLOSED_EXACTLY_TO_THE_FEATURE);

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenTheImportingPackageOnlySharesAPrefix(@TempDir Path projectDir)
            throws IOException {

        // 'example.frameworkish' is not under 'example.framework', so the rule
        // does not reach it - only a whole-segment match may close a package.
        writeJavaSource(projectDir, "src/main/java", "package-sharing-a-prefix");

        var result = runGateExpectingSuccess(projectDir, CLOSED_TO_THE_FEATURE);

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenTheImportedNameOnlySharesAPrefix(@TempDir Path projectDir)
            throws IOException {

        // The same segment rule on the other side: 'example.featureless' is a
        // different package from 'example.feature' despite the string prefix.
        writeJavaSource(projectDir, "src/main/java", "import-sharing-a-prefix");

        var result = runGateExpectingSuccess(projectDir, CLOSED_TO_THE_FEATURE);

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenTheImportingFileIsInTheDefaultPackage(@TempDir Path projectDir)
            throws IOException {

        // A file with no package declaration sits under no root, so no edge can
        // name it - and reading its imports against one would be guesswork.
        writeJavaSource(projectDir, "src/main/java", "default-package-file");

        var result = runGateExpectingSuccess(projectDir, CLOSED_TO_THE_FEATURE);

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

        var result = runGateExpectingSuccess(projectDir, NO_EDGE_DECLARED);

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenThereIsNoSourceTree(@TempDir Path projectDir)
            throws IOException {

        var result = runGateExpectingSuccess(projectDir, CLOSED_TO_THE_FEATURE);

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    // One place owns the block's syntax, so a case that varies the edges varies
    // only the edges. Each argument is one 'forbidImport' argument list.
    private static String buildEdgeDeclaration(String... forbidImportArgumentLists) {
        var declaration = new StringBuilder("enforcePackageLayering {\n");

        for (var argumentList : forbidImportArgumentLists) {
            declaration.append("    forbidImport ").append(argumentList).append('\n');
        }
        return declaration.append("}\n").toString();
    }

    // The expected outcome is in the method name rather than a flag, so a call
    // site reads as what it asserts. Both forward to one runner: the pair
    // differs only in which TestKit terminal it drives.
    private BuildResult runGateExpectingFailure(Path projectDir, String edgeDeclaration)
            throws IOException {

        return buildRunner(projectDir, edgeDeclaration).buildAndFail();
    }

    private BuildResult runGateExpectingSuccess(Path projectDir, String edgeDeclaration)
            throws IOException {

        return buildRunner(projectDir, edgeDeclaration).build();
    }

    private GradleRunner buildRunner(Path projectDir, String edgeDeclaration)
            throws IOException {

        // An explicit settings file stops Gradle walking up into a real build.
        Files.writeString(
            projectDir.resolve("settings.gradle"),
            "rootProject.name = 'gate-fixture'\n");

        Files.writeString(
            projectDir.resolve("build.gradle"),
            "apply from: '" + scriptPath() + "'\n" + edgeDeclaration);

        return GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments("enforcePackageLayering");
    }

    // The source root is a parameter rather than fixed, because every source
    // set is a case of the same rule and the gate must read all of them.
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
