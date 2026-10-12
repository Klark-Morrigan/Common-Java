import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Drives the shared enforce-doc-links-resolve gate.
class EnforceDocLinksResolveGateIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest("enforceDocLinksResolve", "lint/docs/enforce-doc-links-resolve");

    // The doc under test sits one directory down, so a fixture can link a sibling and a parent alike.
    private static final String GUIDE_PATH = "docs/guide.md";

    private static final String SIBLING_TARGET_PATH = "docs/other.md";

    // "cafe" with an acute e (U+0063 U+0061 U+0066 U+00E9): a file name a single-byte default decodes
    // differently from UTF-8.
    private static final String ACCENTED_NAME = "café";

    // Enough ASCII to fill the window a reader without an encoding guesses one from, about eight kilobytes.
    private static final String ASCII_FILLER_LINE = "A line of plain prose that links nowhere at all.\n";
    private static final int FILLER_LINE_COUNT = 200;

    // A broken link beyond doubt, so a case expecting a pass proves the file was skipped rather than sound.
    private static final String BROKEN_DOC_FIXTURE = "markdown/missing-relative-link";

    // The three targets the resolving fixture links to: a sibling, a parent, and a directory reached
    // root-relatively.
    private static GateProject drivingWithLinkTargets(Path projectDir) {

        return GateProject.driving(GATE, projectDir)
            .holdingText(SIBLING_TARGET_PATH, "# Other\n")
            .holdingText("README.md", "# Root\n")
            .holdingDirectory(GateProject.MAIN_JAVA_ROOT);
    }

    @Nested
    class Resolution {

        @Test
        void passesWhenRelativeLinksResolve(@TempDir Path projectDir) {

            drivingWithLinkTargets(projectDir)
                .holdingFixture(GUIDE_PATH, "markdown/resolving-relative-links")
                .runExpectingGateToPass();
        }

        @Test
        void failsWhenARelativeLinkTargetIsMissing(@TempDir Path projectDir) {

            var result = GateProject.driving(GATE, projectDir)
                .holdingFixture(GUIDE_PATH, BROKEN_DOC_FIXTURE)
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("guide.md:3: link target does not exist: renamed/README.md");
        }

        @Test
        void failsWhenALinkTargetIsSpeltInTheWrongCase(@TempDir Path projectDir) {

            var result = drivingWithLinkTargets(projectDir)
                .holdingFixture(GUIDE_PATH, "markdown/wrong-case-link")
                .runExpectingGateToFail();

            // Asserted on the target rather than on either verdict's wording: a case-insensitive filesystem
            // finds the file and reports the misspelling, while a case-sensitive one reports it missing.
            assertThat(result.getOutput())
                .contains("guide.md:3: link target")
                .contains("Other.md");
        }

        @Test
        void failsWhenOnlyTheFileHalfOfALinkCarryingAFragmentIsMissing(@TempDir Path projectDir) {

            var result = GateProject.driving(GATE, projectDir)
                .holdingFixture(GUIDE_PATH, "markdown/fragment-on-missing-file")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("guide.md:3: link target does not exist: missing.md#index");
        }

        @Test
        void failsWhenALinkCarryingATitleIsMissing(@TempDir Path projectDir) {

            // A title must not hide its link: each quoting CommonMark allows still yields the target.
            var result = GateProject.driving(GATE, projectDir)
                .holdingFixture(GUIDE_PATH, "markdown/links-carrying-titles")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("guide.md:3: link target does not exist: missing-one.md")
                .contains("guide.md:4: link target does not exist: missing-two.md")
                .contains("guide.md:5: link target does not exist: missing-three.md");
        }

        @Test
        void passesWhenAPercentEncodedSpaceNamesAFileWithASpace(@TempDir Path projectDir) {

            GateProject.driving(GATE, projectDir)
                .holdingText("docs/release notes.md", "# Notes\n")
                .holdingFixture(GUIDE_PATH, "markdown/percent-encoded-space")
                .runExpectingGateToPass();
        }

        @Test
        void passesWhenALinkNamesANonAsciiFile(@TempDir Path projectDir) {

            // A file read without an encoding is guessed from its first few kilobytes, and an ASCII opening
            // falls back to the build's default. The filler pushes the accented link past that window, so the
            // case fails wherever that default is not UTF-8 and the gate reads without its own encoding.
            var guide = "# Guide\n\n" + ASCII_FILLER_LINE.repeat(FILLER_LINE_COUNT) +
                "[accented](" + ACCENTED_NAME + ".md)\n";

            GateProject.driving(GATE, projectDir)
                .holdingText("docs/" + ACCENTED_NAME + ".md", "# Accented\n")
                .holdingText(GUIDE_PATH, guide)
                .runExpectingGateToPass();
        }

        @Test
        void passesWhenALinkNamesASymlinkInsideTheProject(@TempDir Path projectDir) {

            var project = drivingWithLinkTargets(projectDir)
                .holdingFixture(GUIDE_PATH, "markdown/link-through-a-symlink");

            // Creating a symlink needs a privilege Windows grants only in developer mode or to an
            // administrator, so the case is skipped where the filesystem refuses one.
            try {
                Files.createSymbolicLink(projectDir.resolve("docs/alias.md"), Path.of("other.md"));

            } catch (IOException | UnsupportedOperationException cannotLink) {
                Assumptions.abort("this filesystem refuses symlinks: " + cannotLink);
            }

            project.runExpectingGateToPass();
        }
    }

    @Nested
    class Scope {

        @Test
        void passesWhenLinksAreExternalOrPureFragments(@TempDir Path projectDir) {

            GateProject.driving(GATE, projectDir)
                .holdingFixture(GUIDE_PATH, "markdown/external-and-fragment-links")
                .runExpectingGateToPass();
        }

        @Test
        void passesWhenTargetsClimbOutOfTheProject(@TempDir Path projectDir) {

            // Where such a target lands depends on the machine rather than on this checkout.
            GateProject.driving(GATE, projectDir)
                .holdingFixture(GUIDE_PATH, "markdown/link-above-the-project-root")
                .runExpectingGateToPass();
        }

        @Test
        void failsOnlyBelowABacktickFenceShowingABrokenLink(@TempDir Path projectDir) {

            // The fence has to close again: one that latched open would swallow the broken link below it.
            var result = GateProject.driving(GATE, projectDir)
                .holdingFixture(GUIDE_PATH, "markdown/link-inside-fenced-block")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("guide.md:10: link target does not exist: after-the-fence.md")
                .doesNotContain("nowhere.md");
        }

        @Test
        void failsOnlyBelowATildeFenceHoldingABacktickLine(@TempDir Path projectDir) {

            // A fence closes only on the character it opened with, so the backtick line inside is content.
            var result = GateProject.driving(GATE, projectDir)
                .holdingFixture(GUIDE_PATH, "markdown/link-inside-tilde-fence")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("guide.md:11: link target does not exist: after-the-fence.md")
                .doesNotContain("nowhere.md");
        }

        @Test
        void failsOnlyOnALinkOutsideAnInlineCodeSpan(@TempDir Path projectDir) {

            // A sample link quoted in backticks shows syntax rather than navigating, as a fenced one does.
            var result = GateProject.driving(GATE, projectDir)
                .holdingText(GUIDE_PATH,
                    "Write `[text](nowhere.md)` or ``[text](`nowhere.md`)`` to link.\n" +
                    "\n" +
                    "See [the missing page](after-the-span.md).\n")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("guide.md:3: link target does not exist: after-the-span.md")
                .doesNotContain("nowhere.md");
        }

        @Test
        void passesWhenBrokenMarkdownSitsUnderADefaultExcludedDirectory(@TempDir Path projectDir) {

            GateProject.driving(GATE, projectDir)
                .holdingFixture("build/docs/guide.md", BROKEN_DOC_FIXTURE)
                .runExpectingGateToPass();
        }

        @Test
        void passesWhenBrokenMarkdownSitsUnderANestedExcludedDirectory(@TempDir Path projectDir) {

            GateProject.driving(GATE, projectDir)
                .holdingFixture("tools/node_modules/package/guide.md", BROKEN_DOC_FIXTURE)
                .runExpectingGateToPass();
        }

        @Test
        void passesWhenBrokenMarkdownSitsUnderADirectoryAddedAfterTheGateIsApplied(@TempDir Path projectDir) {

            // A consumer adds its own generated directory from a script that runs after the gate is applied.
            GateProject.driving(GATE, projectDir)
                .holdingFixture("generated/guide.md", BROKEN_DOC_FIXTURE)
                .configuringTheGate("authoredScanExcludedDirectoryNames << 'generated'\n")
                .runExpectingGateToPass();
        }

        @Test
        void passesWhenThereAreNoMarkdownFiles(@TempDir Path projectDir) {
            GateProject.driving(GATE, projectDir).runExpectingGateToPass();
        }
    }

    @Nested
    class UpToDateBehaviour {

        @Test
        void passesUpToDateWhenNothingChanged(@TempDir Path projectDir) {

            var project = drivingWithLinkTargets(projectDir)
                .holdingFixture(GUIDE_PATH, "markdown/resolving-relative-links");

            project.runExpectingGateToPass();

            assertThat(project.runExpectingSuccess().task(GATE.taskPath()).getOutcome())
                .isEqualTo(TaskOutcome.UP_TO_DATE);
        }

        @Test
        void failsWhenATargetIsDeletedWithoutTheMarkdownChanging(@TempDir Path projectDir) throws IOException {

            var project = drivingWithLinkTargets(projectDir)
                .holdingFixture(GUIDE_PATH, "markdown/resolving-relative-links");

            project.runExpectingGateToPass();
            Files.delete(projectDir.resolve(SIBLING_TARGET_PATH));

            assertThat(project.runExpectingGateToFail().getOutput())
                .contains("guide.md:3: link target does not exist: other.md");
        }
    }
}
