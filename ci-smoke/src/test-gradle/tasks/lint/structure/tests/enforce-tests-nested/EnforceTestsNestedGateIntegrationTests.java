import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

    private static final GateUnderTest GATE =
        new GateUnderTest("enforceTestsNested", "nested.gate.script.path");

    private static final String TASK_PATH = ":enforceTestsNested";
    @Test
    void passesWhenTestIsNestedInJava(@TempDir Path projectDir) {

        var result = javaProject(projectDir, "nested-test-passes")
            .runExpectingSuccess();

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void failsWhenTestIsAtTopLevelInJava(@TempDir Path projectDir) {

        var result = javaProject(projectDir, "top-level-test-is-flagged")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("must live inside a @Nested class");
    }

    @Test
    void ignoresAnnotationsAndBracesInLiteralsInJava(@TempDir Path projectDir) {

        var result = javaProject(projectDir, "literals-do-not-confuse-the-scan")
            .runExpectingSuccess();

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenTestIsNestedInKotlin(@TempDir Path projectDir) {

        var result = kotlinProject(projectDir, "nested-test-passes")
            .runExpectingSuccess();

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void failsWhenTestIsAtTopLevelInKotlin(@TempDir Path projectDir) {

        var result = kotlinProject(projectDir, "top-level-test-is-flagged")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("must live inside a @Nested class");
    }

    @Test
    void ignoresAnnotationsAndBracesInLiteralsInKotlin(@TempDir Path projectDir) {
        // The Kotlin fixture hides the stray braces and @Test inside a """ raw
        // string that spans lines, exercising the gate's cross-line raw-string
        // threading - the Java counterpart can only reach the single-line
        // string-literal path.
        var result = kotlinProject(projectDir, "literals-do-not-confuse-the-scan")
            .runExpectingSuccess();

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void failsWhenKotlinNestedClassIsNotInner(@TempDir Path projectDir) {
        // A Kotlin @Nested class declared as a plain 'class' (not 'inner')
        // compiles but is silently skipped by JUnit5, so the gate must flag it
        // even though the @Test is technically inside a @Nested class.
        var result = kotlinProject(projectDir, "nested-without-inner-is-flagged")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("must be declared `inner class`");
    }

    @Test
    void ignoresApostropheInKotlinBacktickTestName(@TempDir Path projectDir) {
        // A Kotlin backtick test name can hold an apostrophe (calculator's),
        // which must not be read as a char-literal opener that swallows the
        // method brace and drifts the scan into flagging later nested tests.
        var result = kotlinProject(projectDir, "backtick-name-with-apostrophe-passes")
            .runExpectingSuccess();

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenThereIsNoTestTree(@TempDir Path projectDir) {

        var result = GateProject.driving(GATE, projectDir).runExpectingSuccess();

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    private static GateProject javaProject(Path projectDir, String fixture) {

        return GateProject
            .driving(GATE, projectDir)
            .holdingFixture("src/test/java/Sample.java", "java/" + fixture);
    }

    private static GateProject kotlinProject(Path projectDir, String fixture) {

        return GateProject
            .driving(GATE, projectDir)
            .holdingFixture("src/test/kotlin/Sample.kt", "kotlin/" + fixture);
    }
}
