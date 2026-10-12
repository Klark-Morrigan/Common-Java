import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

// Drives the shared formatted-source-line-gate helper through every gate built on it: which files a
// line gate reads, and how it walks them. Each gate's own suite covers only its rule.
class FormattedSourceLineGateIntegrationTests {

    // A source set of a build's own, named nothing the java plugin knows about.
    private static final String TOOLING_JAVA_ROOT = "src/tooling/java";

    private static final String DECLARES_A_TOOLING_SOURCE_SET =
        "sourceSets { tooling { java.srcDirs = ['" + TOOLING_JAVA_ROOT + "'] } }\n";

    // The knob that holds a source set out of the formatter, which holds it out of these gates too.
    private static final String EXEMPTS_THE_TOOLING_SOURCE_SET =
        "ext.formatterExcludedSourceSets = ['tooling']\n";

    // A Kotlin tree reaches a gate the way it reaches the compiler: because a source set declares
    // it. The Kotlin plugin does that in a real Kotlin build; declaring it by hand exercises the
    // same contract without resolving that plugin.
    private static final String KOTLIN_TREE_ON_MAIN_SOURCE_SET =
        "sourceSets.main.java.srcDir '" + GateProject.MAIN_KOTLIN_ROOT + "'\n";

    // Clean under every line gate, with Windows line endings.
    private static final String CLEAN_CRLF_SOURCE = "class Sample {\r\n\r\n    int value;\r\n}\r\n";

    // The line each gate's violating fixture is rejected at.
    private static final int INDENTED_BLANK_LINE = 4;
    private static final int SECOND_CONSECUTIVE_BLANK_LINE = 5;

    /**
     * A gate built on the helper, with a sample it rejects. The fixture is filed under both java/
     * and kotlin/ in the gate's own suite folder, and is rejected at the same line in each.
     */
    record LineGate(GateUnderTest gate, String violatingFixture, int violatingLine, String finding) {

        @Override
        public String toString() {
            return gate.taskName();
        }
    }

    static Stream<LineGate> readLineGates() {

        return Stream.of(
            new LineGate(
                new GateUnderTest("enforceNoTrailingWhitespace", "lint/formatting/enforce-no-trailing-whitespace"),
                "blank-line-carrying-indent",
                INDENTED_BLANK_LINE,
                "line ends in whitespace"),
            new LineGate(
                new GateUnderTest("enforceSingleBlankLines", "lint/formatting/enforce-single-blank-lines"),
                "two-consecutive-blank-lines",
                SECOND_CONSECUTIVE_BLANK_LINE,
                "consecutive blank line"));
    }

    @ParameterizedTest
    @MethodSource("readLineGates")
    void passesWhenThereAreNoSources(LineGate lineGate, @TempDir Path projectDir) {

        javaProject(lineGate, projectDir)
            .runExpectingGateToPass();
    }

    @ParameterizedTest
    @MethodSource("readLineGates")
    void failsOnAViolationInJava(LineGate lineGate, @TempDir Path projectDir) {

        var result = javaProject(lineGate, projectDir)
            .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, lineGate.violatingFixture())
            .runExpectingGateToFail();

        assertThat(result.getOutput())
            .contains("Sample.java:" + lineGate.violatingLine() + ": " + lineGate.finding());
    }

    @ParameterizedTest
    @MethodSource("readLineGates")
    void failsOnAViolationInKotlin(LineGate lineGate, @TempDir Path projectDir) {

        var result = javaProject(lineGate, projectDir)
            .withBuildPreamble(KOTLIN_TREE_ON_MAIN_SOURCE_SET)
            .holdingKotlinSample(GateProject.MAIN_KOTLIN_ROOT, lineGate.violatingFixture())
            .runExpectingGateToFail();

        assertThat(result.getOutput())
            .contains("Sample.kt:" + lineGate.violatingLine() + ": " + lineGate.finding());
    }

    @ParameterizedTest
    @MethodSource("readLineGates")
    void passesOnACleanSourceWithWindowsLineEndings(LineGate lineGate, @TempDir Path projectDir) {

        // The carriage return of a CRLF ending is the line's terminator, never part of the line.
        javaProject(lineGate, projectDir)
            .holdingBytes(
                GateProject.MAIN_JAVA_ROOT + "/Sample.java",
                CLEAN_CRLF_SOURCE.getBytes(StandardCharsets.UTF_8))
            .runExpectingGateToPass();
    }

    @ParameterizedTest
    @MethodSource("readLineGates")
    void failsOnAViolationInASourceSetTheBuildDeclares(LineGate lineGate, @TempDir Path projectDir) {

        // The reason the gates read source sets rather than a list of tree names: named trees cover
        // main and test and silently stop there.
        var result = javaProject(lineGate, projectDir)
            .withBuildPreamble(DECLARES_A_TOOLING_SOURCE_SET)
            .holdingJavaSample(TOOLING_JAVA_ROOT, lineGate.violatingFixture())
            .runExpectingGateToFail();

        assertThat(result.getOutput())
            .contains("Sample.java:" + lineGate.violatingLine() + ": " + lineGate.finding());
    }

    @ParameterizedTest
    @MethodSource("readLineGates")
    void failsOnAViolationInASourceSetDeclaredAfterTheGate(LineGate lineGate, @TempDir Path projectDir) {

        // A consumer declares its own source sets after the conventions apply the gate.
        var result = javaProject(lineGate, projectDir)
            .configuringTheGate(DECLARES_A_TOOLING_SOURCE_SET)
            .holdingJavaSample(TOOLING_JAVA_ROOT, lineGate.violatingFixture())
            .runExpectingGateToFail();

        assertThat(result.getOutput())
            .contains("Sample.java:" + lineGate.violatingLine() + ": " + lineGate.finding());
    }

    @ParameterizedTest
    @MethodSource("readLineGates")
    void passesWhenTheOffendingSourceSetIsExemptFromTheFormatter(LineGate lineGate, @TempDir Path projectDir) {

        // Exempt because its shape is not the repo's to choose, and its lines are part of that shape.
        javaProject(lineGate, projectDir)
            .withBuildPreamble(DECLARES_A_TOOLING_SOURCE_SET + EXEMPTS_THE_TOOLING_SOURCE_SET)
            .holdingJavaSample(TOOLING_JAVA_ROOT, lineGate.violatingFixture())
            .runExpectingGateToPass();
    }

    @ParameterizedTest
    @MethodSource("readLineGates")
    void passesWhenTheExemptionIsDeclaredAfterTheGate(LineGate lineGate, @TempDir Path projectDir) {

        javaProject(lineGate, projectDir)
            .configuringTheGate(DECLARES_A_TOOLING_SOURCE_SET + EXEMPTS_THE_TOOLING_SOURCE_SET)
            .holdingJavaSample(TOOLING_JAVA_ROOT, lineGate.violatingFixture())
            .runExpectingGateToPass();
    }

    @ParameterizedTest
    @MethodSource("readLineGates")
    void refusesABuildWithoutTheJavaPlugin(LineGate lineGate, @TempDir Path projectDir) {

        // Refused before the gate can run, so there is no gate outcome to read: only the message.
        var result = GateProject
            .driving(lineGate.gate(), projectDir)
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains(lineGate.gate().taskName() + " needs the java plugin");
    }

    private static GateProject javaProject(LineGate lineGate, Path projectDir) {

        return GateProject
            .driving(lineGate.gate(), projectDir)
            .applyingTheJavaPlugin();
    }
}
