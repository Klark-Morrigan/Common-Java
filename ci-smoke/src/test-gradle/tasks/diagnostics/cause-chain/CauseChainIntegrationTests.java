import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Drives the shared cause-chain helper through a task each case declares, which builds a throwable
// chain and prints what the helper walked of it. The helper registers no task of its own, so the
// case's task is what the run is judged by.
class CauseChainIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest(
            "reportCauseChain",
            "diagnostics/cause-chain");

    // Builds the chain a case states and prints the messages the helper walked, outermost first.
    private static String declareReportOf(String chainStatements, String walkedFailure) {

        return "tasks.register('reportCauseChain') {\n"
            + "    doLast {\n"
            + chainStatements
            + "        println 'walked: ' + readCauseChain(" + walkedFailure + ")*.message.join(' <- ')\n"
            + "    }\n"
            + "}\n";
    }

    @Nested
    class ReadCauseChain {

        @Test
        void walksEveryCauseOutermostFirst(@TempDir Path projectDir) {

            // ARRANGE
            var expectedLine = "walked: outer <- middle <- root";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(declareReportOf(
                    "        def root = new Error('root')\n"
                        + "        def middle = new IllegalStateException('middle', root)\n"
                        + "        def outer = new RuntimeException('outer', middle)\n",
                    "outer"));

            // ACT
            var actualResult = project.runExpectingGateToPass();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedLine);
        }

        // Two throwables naming each other as their cause would loop a walk that followed causes
        // alone; each is listed once.
        @Test
        void stopsWhereTheChainLoopsBackOnItself(@TempDir Path projectDir) {

            // ARRANGE
            var expectedLine = "walked: first <- second";
            var unexpectedLine = "walked: first <- second <- first";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(declareReportOf(
                    "        def first = new RuntimeException('first')\n"
                        + "        def second = new RuntimeException('second')\n"
                        + "        first.initCause(second)\n"
                        + "        second.initCause(first)\n",
                    "first"));

            // ACT
            var actualResult = project.runExpectingGateToPass();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedLine)
                .doesNotContain(unexpectedLine);
        }

        @Test
        void answersNothingForNoFailure(@TempDir Path projectDir) {

            // ARRANGE
            var expectedLine = "walked: \n";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(declareReportOf("", "null"));

            // ACT
            var actualResult = project.runExpectingGateToPass();

            // ASSERT
            assertThat(actualResult.getOutput().replace("\r\n", "\n"))
                .contains(expectedLine);
        }
    }
}
