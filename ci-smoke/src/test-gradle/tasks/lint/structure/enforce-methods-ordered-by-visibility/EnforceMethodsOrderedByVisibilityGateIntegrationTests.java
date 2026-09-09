import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

    private static final GateUnderTest GATE =
        new GateUnderTest("enforceMethodsOrderedByVisibility", "methods.order.gate.script.path");

    private static final String TASK_PATH = ":enforceMethodsOrderedByVisibility";
    @Test
    void passesWhenJavaMethodsDescendTheLadder(@TempDir Path projectDir) {

        var result = javaProject(projectDir, "ordered-ladder-passes")
            .runExpectingSuccess();

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void failsWhenJavaPublicMethodSitsBelowPrivate(@TempDir Path projectDir) {

        var result = javaProject(projectDir, "public-below-private-is-flagged")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("public method declared below a private method");
    }

    @Test
    void ignoresJavaConstructorsAndEnumConstants(@TempDir Path projectDir) {
        // A public constructor placed below a private method would look like a
        // ladder violation, and an enum constant with an argument list looks like
        // a bare method - both must be skipped, so this ordered fixture passes.
        var result = javaProject(projectDir, "constructor-and-enum-ignored-passes")
            .runExpectingSuccess();

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void ignoresJavaMultiLineFieldInitializers(@TempDir Path projectDir) {
        // A field whose value sits on the line after the '=' - a 'Type.factory(...)'
        // or a 'new Type(...)' - looks like a bare package-private method on that
        // continuation line, since the same-line '=' guard cannot see the '=' on the
        // previous line. The gate must skip a continuation line (its previous line
        // ends in '=') so the public methods below the fields are not flagged against
        // a phantom method.
        var result = javaProject(projectDir, "multiline-factory-field-initializer-ignored-passes")
            .runExpectingSuccess();

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void flagsJavaInterfaceImplicitPublicBelowPrivate(@TempDir Path projectDir) {
        // A no-modifier method is package-private in a class but public in an
        // interface. This fixture only fails if the gate ranks the bare interface
        // method as public and so sees it jump above the private method above it.
        var result = javaProject(projectDir, "interface-implicit-public-below-private-is-flagged")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("public (implicit) method declared below a private method");
    }

    @Test
    void passesWhenKotlinMethodsDescendTheLadder(@TempDir Path projectDir) {

        var result = kotlinProject(projectDir, "ordered-ladder-passes")
            .runExpectingSuccess();

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void failsWhenKotlinPublicMethodSitsBelowPrivate(@TempDir Path projectDir) {
        // A bare Kotlin 'fun' is public, so it must not appear below a private
        // one; the gate reports it as an implicit-public method.
        var result = kotlinProject(projectDir, "public-below-private-is-flagged")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("public (implicit) method declared below a private method");
    }

    @Test
    void ignoresKotlinLocalFunctionsAndPropertyInitializers(@TempDir Path projectDir) {
        // A 'fun' nested in a method body sits deeper than the class-body depth,
        // and a 'fun' following '=' is a function-expression property value -
        // neither is a member declaration, so this ordered fixture passes.
        var result = kotlinProject(projectDir, "local-fun-and-property-init-ignored-passes")
            .runExpectingSuccess();

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenThereIsNoMainTree(@TempDir Path projectDir) {

        var result = GateProject
            .driving(GATE, projectDir)
            .runExpectingSuccess();

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    private static GateProject javaProject(Path projectDir, String fixture) {

        return GateProject
            .driving(GATE, projectDir)
            .holdingFixture("src/main/java/Sample.java", "java/" + fixture);
    }

    private static GateProject kotlinProject(Path projectDir, String fixture) {

        return GateProject
            .driving(GATE, projectDir)
            .holdingFixture("src/main/kotlin/Sample.kt", "kotlin/" + fixture);
    }
}
