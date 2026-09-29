import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Drives the shared enforce-tests-nested gate. The nested/top-level rule and the literal-stripping
// that protects it run in both languages; the Kotlin literals fixture uses a """ raw string and the
// backtick fixture an escaped identifier, both Kotlin-only lexical forms with no Java analogue.
class EnforceTestsNestedGateIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest("enforceTestsNested", "nested.gate.script.path");

    @Test
    void passesWhenTestIsNestedInJava(@TempDir Path projectDir) {

        GateProject
            .driving(GATE, projectDir)
            .holdingJavaTestSample("nested-test-passes")
            .runExpectingGateToPass();
    }

    @Test
    void failsWhenTestIsAtTopLevelInJava(@TempDir Path projectDir) {

        var result = GateProject
            .driving(GATE, projectDir)
            .holdingJavaTestSample("top-level-test-is-flagged")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("must live inside a @Nested class");
    }

    @Test
    void ignoresAnnotationsAndBracesInLiteralsInJava(@TempDir Path projectDir) {

        GateProject
            .driving(GATE, projectDir)
            .holdingJavaTestSample("literals-do-not-confuse-the-scan")
            .runExpectingGateToPass();
    }

    @Test
    void passesWhenTestIsNestedInKotlin(@TempDir Path projectDir) {

        GateProject
            .driving(GATE, projectDir)
            .holdingKotlinTestSample("nested-test-passes")
            .runExpectingGateToPass();
    }

    @Test
    void failsWhenTestIsAtTopLevelInKotlin(@TempDir Path projectDir) {

        var result = GateProject
            .driving(GATE, projectDir)
            .holdingKotlinTestSample("top-level-test-is-flagged")
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
        GateProject
            .driving(GATE, projectDir)
            .holdingKotlinTestSample("literals-do-not-confuse-the-scan")
            .runExpectingGateToPass();
    }

    @Test
    void failsWhenKotlinNestedClassIsNotInner(@TempDir Path projectDir) {
        // A Kotlin @Nested class declared as a plain 'class' (not 'inner')
        // compiles but is silently skipped by JUnit5, so the gate must flag it
        // even though the @Test is technically inside a @Nested class.
        var result = GateProject
            .driving(GATE, projectDir)
            .holdingKotlinTestSample("nested-without-inner-is-flagged")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("must be declared `inner class`");
    }

    @Test
    void ignoresApostropheInKotlinBacktickTestName(@TempDir Path projectDir) {
        // A Kotlin backtick test name can hold an apostrophe (calculator's),
        // which must not be read as a char-literal opener that swallows the
        // method brace and drifts the scan into flagging later nested tests.
        GateProject
            .driving(GATE, projectDir)
            .holdingKotlinTestSample("backtick-name-with-apostrophe-passes")
            .runExpectingGateToPass();
    }

    @Test
    void passesWhenThereIsNoTestTree(@TempDir Path projectDir) {

        GateProject.driving(GATE, projectDir).runExpectingGateToPass();
    }
}
