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
    void ignoresLinksInsideAFencedCodeBlock(@TempDir Path projectDir) throws IOException {
        // A markdown example demonstrating link syntax is sample text, not
        // navigation, so the path it names need not exist.
        writeDoc(projectDir, "link-inside-fenced-block");

        var result = runGate(projectDir, false);

        assertThat(result.task(TASK_PATH).getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenThereAreNoMarkdownFiles(@TempDir Path projectDir) throws IOException {
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
                .withArguments("enforceDocLinksResolve");

        return expectFailure ? runner.buildAndFail() : runner.build();
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
