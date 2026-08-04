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

// Integration test for the shared enforce-doc-links-resolve gate: it applies
// the real gate script into a throwaway project and runs the task, asserting
// the build outcome and the message a developer sees. Lives in the Common-Java
// ci-smoke project - the gate script is shared by every consumer; the tests
// live here beside it. Fixtures load from .txt files under a markdown/
// subfolder rather than being written as .md, so the gate never scans this
// tree's own fixtures when it runs over Common-Java itself. Default package:
// the grouping folder is the source root, and its kebab name cannot be a Java
// package.
class EnforceDocLinksResolveGateIntegrationTests {
    private static final String TASK_PATH = ":enforceDocLinksResolve";

    @Test
    void passesWhenRelativeLinksResolve(@TempDir Path projectDir) throws IOException {
        writeLinkTargets(projectDir);
        writeDoc(projectDir, "resolving-relative-links");

        var result = runGate(projectDir, false);

        assertThat(result.task(TASK_PATH).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void failsWhenARelativeLinkTargetIsMissing(@TempDir Path projectDir) throws IOException {
        writeDoc(projectDir, "missing-relative-link");

        var result = runGate(projectDir, true);

        assertThat(result.getOutput()).contains("link target does not exist: renamed/README.md");
    }

    @Test
    void failsWhenALinkTargetIsSpeltInTheWrongCase(@TempDir Path projectDir) throws IOException {
        writeLinkTargets(projectDir);
        writeDoc(projectDir, "wrong-case-link");

        var result = runGate(projectDir, true);

        // Asserted on the target rather than on either verdict's wording,
        // because which verdict is correct depends on the filesystem under the
        // build: a case-insensitive one (Windows, macOS) finds the file and
        // reports the misspelling, while a case-sensitive one never finds it
        // and reports it missing. The link is wrong either way, and pinning one
        // wording would make this test pass only on the machine that wrote it -
        // the very asymmetry the gate exists to close.
        assertThat(result.getOutput()).contains("Other.md");
    }

    @Test
    void ignoresExternalAndFragmentTargets(@TempDir Path projectDir) throws IOException {
        // The two shapes the gate declines to resolve: a scheme leaves the
        // repository, and a bare #fragment names a heading rather than a path.
        writeDoc(projectDir, "external-and-fragment-links");

        var result = runGate(projectDir, false);

        assertThat(result.task(TASK_PATH).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void resolvesTheFileHalfOfALinkCarryingAFragment(@TempDir Path projectDir) throws IOException {
        // `missing.md#index` is still a broken link: the fragment is dropped and
        // the file half checked, rather than the whole target being waved past
        // because it carries a #.
        writeDoc(projectDir, "fragment-on-missing-file");

        var result = runGate(projectDir, true);

        assertThat(result.getOutput()).contains("link target does not exist: missing.md#index");
    }

    @Test
    void ignoresTargetsThatClimbOutOfTheProject(@TempDir Path projectDir) throws IOException {
        // A doc may point at the game install or a sibling checkout. Where that
        // lands depends on the machine rather than on this checkout, so it is
        // no more this gate's to resolve than an http target is.
        writeDoc(projectDir, "link-above-the-project-root");

        var result = runGate(projectDir, false);

        assertThat(result.task(TASK_PATH).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void ignoresLinksInsideAFenceAndResumesBelowIt(@TempDir Path projectDir) throws IOException {
        // One fixture, two proofs. A markdown example demonstrating link syntax
        // is sample text rather than navigation, so the path it names need not
        // exist - and the fence has to close again, since a fence that latched
        // open would swallow the genuinely broken link below it and let the
        // gate pass in silence.
        writeDoc(projectDir, "link-inside-fenced-block");

        var result = runGate(projectDir, true);

        assertThat(result.getOutput())
                .contains("link target does not exist: after-the-fence.md")
                .doesNotContain("nowhere.md");
    }

    @Test
    void ignoresMarkdownUnderADefaultExcludedDirectory(@TempDir Path projectDir) throws IOException {
        // A build directory holds copies of authored docs, so judging them
        // would report every violation once per copy.
        writeBrokenDocUnder(projectDir, "build/docs");

        var result = runGate(projectDir, false);

        assertThat(result.task(TASK_PATH).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void honoursAnExcludedDirectoryAddedAfterTheGateIsApplied(@TempDir Path projectDir)
            throws IOException {
        // The consumer seam: a project whose own tooling generates markdown
        // names that directory from its own build script, which runs after this
        // gate is applied. The exclusion has to be read at scan time for a late
        // addition to count.
        writeBrokenDocUnder(projectDir, "generated");

        var result = runGateWithExtraConfiguration(projectDir, false,
                "docLinkScanExcludedDirectoryNames << 'generated'\n");

        assertThat(result.task(TASK_PATH).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenThereAreNoMarkdownFiles(@TempDir Path projectDir) throws IOException {
        var result = runGate(projectDir, false);

        assertThat(result.task(TASK_PATH).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }

    private BuildResult runGate(Path projectDir, boolean expectFailure) throws IOException {
        return runGateWithExtraConfiguration(projectDir, expectFailure, "");
    }

    // Only the exclusion seam needs to configure the gate after applying it, so
    // the extra script stays out of every other case's call.
    private BuildResult runGateWithExtraConfiguration(
            Path projectDir, boolean expectFailure, String extraBuildScript) throws IOException {
        // An explicit settings file stops Gradle walking up into a real build.
        Files.writeString(projectDir.resolve("settings.gradle"),
                "rootProject.name = 'gate-fixture'\n");
        Files.writeString(projectDir.resolve("build.gradle"),
                "apply from: '" + scriptPath() + "'\n" + extraBuildScript);

        var runner = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withArguments("enforceDocLinksResolve");

        return expectFailure ? runner.buildAndFail() : runner.build();
    }

    // A doc whose link is broken beyond doubt, so a case that expects success
    // is proving the directory was skipped rather than that the link was sound.
    private void writeBrokenDocUnder(Path projectDir, String directory) throws IOException {
        var dir = projectDir.resolve(directory);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("guide.md"), loadFixture("markdown/missing-relative-link"));
    }

    // The doc under test sits one directory down, so a fixture can exercise a
    // parent-relative target as well as a sibling one.
    private void writeDoc(Path projectDir, String fixture) throws IOException {
        var dir = projectDir.resolve("docs");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("guide.md"), loadFixture("markdown/" + fixture));
    }

    // The three targets the resolving fixture links to: a sibling, a parent,
    // and a directory reached root-relatively.
    private void writeLinkTargets(Path projectDir) throws IOException {
        var dir = projectDir.resolve("docs");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("other.md"), "# Other\n");
        Files.writeString(projectDir.resolve("README.md"), "# Root\n");
        Files.createDirectories(projectDir.resolve("src/main/java"));
    }

    // The gate script path is handed in by the test task so the test does not
    // assume a working directory; forward slashes keep it valid inside the
    // generated build script on Windows.
    private String scriptPath() {
        return System.getProperty("doc.links.gate.script.path").replace('\\', '/');
    }

    // The fixture name carries its markdown/ subfolder, which resolves under the
    // classpath root the source set exposes for resources.
    private String loadFixture(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/" + name + ".txt")) {
            if (in == null) {
                throw new IllegalStateException("Missing fixture: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
