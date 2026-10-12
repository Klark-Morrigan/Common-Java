import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

// Integration test for the shared lint-gate stamp, which is what lets every gate go UP-TO-DATE
// instead of re-scanning the whole source tree on each build. It is driven through a real gate
// because a stamp is only observable through one: the helper declares an output on somebody's task
// and appends an action to it, so there is nothing to call on its own.
//
// The trailing-whitespace gate is the one driven here, on no ground other than that a violation of
// it is a single trailing space - what is being tested is the skipping, not the rule.
//
// This is worth its own suite because the failure is silent in the direction that matters. A gate
// wrongly held UP-TO-DATE reports success over sources nobody scanned, and every consumer inherits
// that: the build stays green while the convention stops being enforced. The gate suites next door
// all drive a first run, so none of them can see it.
class LintGateIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest("enforceNoTrailingWhitespace", "lint/formatting/enforce-no-trailing-whitespace");

    private static final String SAMPLE_PATH = GateProject.MAIN_JAVA_ROOT + "/Sample.java";
    private static final String STAMP_PATH = "build/lint/enforceNoTrailingWhitespace.stamp";
    private static final String BUILD_DIRECTORY = "build";

    private static final String CLEAN_SOURCE = "class Sample {\n}\n";
    private static final String CLEAN_SOURCE_EDITED = "class Sample {\n    // edited\n}\n";
    private static final String SOURCE_WITH_TRAILING_SPACE = "class Sample { \n}\n";

    // Turns the build cache on for the project alone, without a command-line flag.
    private static final String ENABLES_THE_BUILD_CACHE = "org.gradle.caching=true\n";

    @Test
    void skipsAGateWhoseSourcesHaveNotChanged(@TempDir Path projectDir) {

        project(projectDir, CLEAN_SOURCE)
            .runExpectingGateToPass();

        var result = project(projectDir, CLEAN_SOURCE)
            .runExpectingSuccess();

        assertThat(result.task(GATE.taskPath()).getOutcome())
            .isEqualTo(TaskOutcome.UP_TO_DATE);
    }

    @Test
    void runsAGateAgainWhenASourceItReadsChanges(@TempDir Path projectDir) {

        // The other half of skipping, and the one that makes it safe: a gate held UP-TO-DATE across
        // an edit would report a pass over text nobody read.
        project(projectDir, CLEAN_SOURCE)
            .runExpectingGateToPass();

        project(projectDir, CLEAN_SOURCE_EDITED)
            .runExpectingGateToPass();
    }

    @Test
    void catchesAViolationIntroducedAfterAPassingRun(@TempDir Path projectDir) {

        // The whole point stated end to end: a gate that passed once must still fail on the edit
        // that breaks it, rather than riding its stamp into a green build.
        project(projectDir, CLEAN_SOURCE)
            .runExpectingGateToPass();

        project(projectDir, SOURCE_WITH_TRAILING_SPACE)
            .runExpectingGateToFail();
    }

    @Test
    void runsAFailingGateAgainRatherThanSkippingIt(@TempDir Path projectDir) {

        // A failed gate leaves no stamp, so the next build re-runs it and fails again. Were the
        // stamp written regardless, a second build over unchanged bad sources would go UP-TO-DATE
        // and read as green.
        project(projectDir, SOURCE_WITH_TRAILING_SPACE)
            .runExpectingGateToFail();

        project(projectDir, SOURCE_WITH_TRAILING_SPACE)
            .runExpectingGateToFail();
    }

    @Test
    void writesTheStampOnlyOnceTheGateHasPassed(@TempDir Path projectDir) {

        project(projectDir, SOURCE_WITH_TRAILING_SPACE)
            .runExpectingGateToFail();

        assertThat(projectDir.resolve(STAMP_PATH))
            .doesNotExist();

        project(projectDir, CLEAN_SOURCE)
            .runExpectingGateToPass();

        assertThat(projectDir.resolve(STAMP_PATH))
            .exists();
    }

    @Test
    void replaysAPassFromTheBuildCache(@TempDir Path projectDir) throws IOException {

        // The stamp is the whole result, so a cached pass stands in for a scan of the same sources
        // after a clean build or a branch switch. The source is unique to this run, so the first
        // build cannot already find it in a cache an earlier run filled.
        var uniqueCleanSource = "class Sample {\n    // " + UUID.randomUUID() + "\n}\n";

        project(projectDir, uniqueCleanSource)
            .holdingText("gradle.properties", ENABLES_THE_BUILD_CACHE)
            .runExpectingGateToPass();

        deleteBuildDirectory(projectDir);

        var result = project(projectDir, uniqueCleanSource)
            .runExpectingSuccess();

        assertThat(result.task(GATE.taskPath()).getOutcome())
            .isEqualTo(TaskOutcome.FROM_CACHE);
    }

    @Test
    void bringsAGateAlongWhenCheckIsAskedFor(@TempDir Path projectDir) {

        // The helper's other half, and the one whose failure is invisible: a gate nothing depends on
        // is a gate nobody runs, and nobody running it looks exactly like everybody passing it.
        project(projectDir, SOURCE_WITH_TRAILING_SPACE)
            .runningTask("check")
            .runExpectingGateToFail();
    }

    @Test
    void bringsAGateAlongWhenTestIsAskedFor(@TempDir Path projectDir) {

        // 'test' as well as 'check', so a CI that runs only the tests still meets every gate first.
        project(projectDir, SOURCE_WITH_TRAILING_SPACE)
            .runningTask("test")
            .runExpectingGateToFail();
    }

    // These cases are about what a second run makes of what a first one left. Everything a project
    // holds is on disk, so each run states the project afresh rather than carrying one between them.
    private static GateProject project(Path projectDir, String sampleSource) {

        return GateProject
            .driving(GATE, projectDir)
            .applyingTheJavaPlugin()
            .holdingText(SAMPLE_PATH, sampleSource);
    }

    private static void deleteBuildDirectory(Path projectDir) throws IOException {

        try (Stream<Path> paths = Files.walk(projectDir.resolve(BUILD_DIRECTORY))) {

            for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
