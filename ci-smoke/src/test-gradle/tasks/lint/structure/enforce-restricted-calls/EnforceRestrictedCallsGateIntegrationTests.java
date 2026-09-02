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

// Integration test for the shared enforce-restricted-calls gate: it applies the
// real gate script into a throwaway project and runs the task, asserting the
// build outcome and the message a developer sees. Lives in the Common-Java
// ci-smoke project - the gate script is shared by every consumer, so the tests
// live here beside it. Fixtures load from .txt files under java/ and kotlin/
// subfolders so the gate, which scans every source set, never sees a fixture as
// real source here; this tree is src/test-gradle, deliberately out of its reach.
//
// The fixture package is 'example.framework' and the contained call is
// 'Example.getGlobalHandle()' rather than any real consumer's, because the gate
// owns only the mechanism and this repo knows no consumer's architecture.
//
// Which type a fixture counts as is its file name, so every case writes its
// source under the name the declaration does or does not allow - that pairing is
// the whole of what the gate decides.
class EnforceRestrictedCallsGateIntegrationTests {

    private static final String TASK_PATH = ":enforceRestrictedCalls";

    // What the consuming build declares, and what the fixtures are written
    // against: one call contained beneath one root, to one type.
    private static final String CLOSED_PACKAGE_ROOT = "example.framework";
    private static final String RESTRICTED_CALL = "Example.getGlobalHandle()";
    private static final String SECOND_RESTRICTED_CALL = "Example.getGlobalClock()";

    // The seam allowed to make the call, and a type that is not.
    private static final String ALLOWED_TYPE = "Adapter";
    private static final String ORDINARY_TYPE = "Sample";

    private static final String NO_RESTRICTION_DECLARED = "";

    private static final String CONTAINED_TO_THE_ADAPTER = buildRestrictionDeclaration(
        "call: '" + RESTRICTED_CALL + "', under: '" + CLOSED_PACKAGE_ROOT
            + "', toTypes: ['" + ALLOWED_TYPE + "']");

    private static final String CONTAINED_TO_NOTHING = buildRestrictionDeclaration(
        "call: '" + RESTRICTED_CALL + "', under: '" + CLOSED_PACKAGE_ROOT + "'");

    private static final String BOTH_CALLS_CONTAINED = buildRestrictionDeclaration(
        "call: '" + RESTRICTED_CALL + "', under: '" + CLOSED_PACKAGE_ROOT
            + "', toTypes: ['" + ALLOWED_TYPE + "']",
        "call: '" + SECOND_RESTRICTED_CALL + "', under: '" + CLOSED_PACKAGE_ROOT
            + "', toTypes: ['" + ALLOWED_TYPE + "']");

    // A restriction naming only the call, which says nothing about where it is
    // contained - the shape a misspelt or forgotten 'under:' takes.
    private static final String HALF_DECLARED_RESTRICTION = buildRestrictionDeclaration(
        "call: '" + RESTRICTED_CALL + "'");

    @Test
    void failsWhenProductionCodeOutsideTheAllowedTypesMakesTheCall(@TempDir Path projectDir)
            throws IOException {

        writeJavaSource(projectDir, "src/main/java", ORDINARY_TYPE, "caller-in-the-closed-root");

        var result = runGateExpectingFailure(projectDir, CONTAINED_TO_THE_ADAPTER);

        assertThat(result.getOutput())
            .contains("example.framework may call Example.getGlobalHandle() only in "
                + "[Adapter]: Sample");
    }

    @Test
    void failsWhenTestCodeMakesTheCall(@TempDir Path projectDir)
            throws IOException {

        // A suite reaching for the ambient handle is the shortest way to make a
        // case compile, and it is the same reach the rule exists to contain -
        // one the production code it drives is then never posed against.
        writeJavaSource(projectDir, "src/test/java", ORDINARY_TYPE, "caller-in-the-closed-root");

        var result = runGateExpectingFailure(projectDir, CONTAINED_TO_THE_ADAPTER);

        assertThat(result.getOutput())
            .contains("may call Example.getGlobalHandle() only in");
    }

    @Test
    void failsWhenASourceSetBeyondMainAndTestMakesTheCall(@TempDir Path projectDir)
            throws IOException {

        // A mod's own extra source set - a dev tool, a stub tree - compiles
        // against the same API, so exempting it would leave the rule enforced
        // everywhere except where nobody was looking.
        writeJavaSource(projectDir, "src/utils/java", ORDINARY_TYPE, "caller-in-the-closed-root");

        var result = runGateExpectingFailure(projectDir, CONTAINED_TO_THE_ADAPTER);

        assertThat(result.getOutput())
            .contains("may call Example.getGlobalHandle() only in");
    }

    @Test
    void failsWhenKotlinCodeMakesTheCall(@TempDir Path projectDir)
            throws IOException {

        // Kotlin leaves the semicolon off its package declaration, so the line
        // the package is read from differs from the Java shape.
        var dir = projectDir.resolve("src/main/kotlin");

        Files.createDirectories(dir);
        Files.writeString(
            dir.resolve(ORDINARY_TYPE + ".kt"),
            loadFixture("kotlin/caller-in-the-closed-root"));

        var result = runGateExpectingFailure(projectDir, CONTAINED_TO_THE_ADAPTER);

        assertThat(result.getOutput())
            .contains("may call Example.getGlobalHandle() only in");
    }

    @Test
    void failsOnARestrictionBeyondTheFirstDeclared(@TempDir Path projectDir)
            throws IOException {

        // Two restrictions, and the breach is of the second: a gate that
        // honoured only the first declaration would pass every case above.
        writeJavaSource(
            projectDir, "src/main/java", ORDINARY_TYPE, "caller-of-the-second-restricted-call");

        var result = runGateExpectingFailure(projectDir, BOTH_CALLS_CONTAINED);

        assertThat(result.getOutput())
            .contains("may call Example.getGlobalClock() only in");
    }

    @Test
    void failsWhenTheCallIsAssembledAsAStringLiteral(@TempDir Path projectDir)
            throws IOException {

        // Prose about the rule is exempt; text that spells the call is not.
        // Reaching the ambient handle by name is the thing being contained, and
        // doing it through a string must not be cheaper than declaring the seam.
        writeJavaSource(
            projectDir, "src/main/java", ORDINARY_TYPE, "calling-through-a-string-literal");

        var result = runGateExpectingFailure(projectDir, CONTAINED_TO_THE_ADAPTER);

        assertThat(result.getOutput())
            .contains("may call Example.getGlobalHandle() only in");
    }

    @Test
    void failsWhenTheCallFollowsAClosedBlockCommentOnOneLine(@TempDir Path projectDir)
            throws IOException {

        // The other half of reading comments out: a stripper that ran to the end
        // of the line would blank real code sitting after the comment closes,
        // and every case above would still pass.
        writeJavaSource(
            projectDir, "src/main/java", ORDINARY_TYPE, "calling-after-a-block-comment-closes");

        var result = runGateExpectingFailure(projectDir, CONTAINED_TO_THE_ADAPTER);

        assertThat(result.getOutput())
            .contains("may call Example.getGlobalHandle() only in");
    }

    @Test
    void failsWhenTheAllowedTypeMakesACallContainedToNothing(@TempDir Path projectDir)
            throws IOException {

        // A restriction with no allowed types closes the root to the call
        // outright, which is what a mistyped 'toTypes:' key silently becomes -
        // so it has to fail loudly rather than allow the call everywhere.
        writeJavaSource(projectDir, "src/main/java", ALLOWED_TYPE, "caller-in-the-closed-root");

        var result = runGateExpectingFailure(projectDir, CONTAINED_TO_NOTHING);

        assertThat(result.getOutput())
            .contains("may call Example.getGlobalHandle() only in []: Adapter");
    }

    @Test
    void failsWhenARestrictionIsDeclaredWithoutTheRootItAppliesUnder(@TempDir Path projectDir)
            throws IOException {

        // A half-declared restriction asserts nothing, so it must be rejected
        // where it is written rather than accepted as a rule that never fires.
        writeJavaSource(projectDir, "src/main/java", ORDINARY_TYPE, "caller-in-the-closed-root");

        var result = runGateExpectingFailure(projectDir, HALF_DECLARED_RESTRICTION);

        assertThat(result.getOutput())
            .contains("restrictCall needs both 'call'");
    }

    @Test
    void failsAgainAfterAPassingRunOnceARestrictionIsDeclared(@TempDir Path projectDir)
            throws IOException {

        // The restrictions are a task input, not just a closure the action
        // reads. If they were not, this second run would be UP-TO-DATE against
        // the first and a newly declared rule would silently never run.
        writeJavaSource(projectDir, "src/main/java", ORDINARY_TYPE, "caller-in-the-closed-root");
        runGateExpectingSuccess(projectDir, NO_RESTRICTION_DECLARED);

        var result = runGateExpectingFailure(projectDir, CONTAINED_TO_THE_ADAPTER);

        assertThat(result.getOutput())
            .contains("may call Example.getGlobalHandle() only in");
    }

    @Test
    void passesWhenTheAllowedTypeMakesTheCall(@TempDir Path projectDir)
            throws IOException {

        // The seam the whole rule exists to leave open. Without this the gate
        // could simply forbid the call outright and pass every failing case.
        writeJavaSource(projectDir, "src/main/java", ALLOWED_TYPE, "caller-in-the-closed-root");

        var result = runGateExpectingSuccess(projectDir, CONTAINED_TO_THE_ADAPTER);

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenTheCallIsMadeOutsideTheClosedRoot(@TempDir Path projectDir)
            throws IOException {

        // The rule is one root's, not the repo's: a package the consuming build
        // said nothing about is free to call whatever it likes.
        writeJavaSource(
            projectDir, "src/main/java", ORDINARY_TYPE, "caller-outside-the-closed-root");

        var result = runGateExpectingSuccess(projectDir, CONTAINED_TO_THE_ADAPTER);

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenTheCallingPackageOnlySharesAPrefix(@TempDir Path projectDir)
            throws IOException {

        // 'example.frameworkish' is not under 'example.framework', so the rule
        // does not reach it - only a whole-segment match may close a package.
        writeJavaSource(
            projectDir, "src/main/java", ORDINARY_TYPE, "caller-in-a-package-sharing-a-prefix");

        var result = runGateExpectingSuccess(projectDir, CONTAINED_TO_THE_ADAPTER);

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenTheCallIsOnlyNamedInProse(@TempDir Path projectDir)
            throws IOException {

        // Where a contained call is most often written down: the types forbidden
        // to make it explain what they do instead. A gate reading those
        // sentences as calls would fail the build for the documentation of its
        // own rule. All three comment forms, since a Javadoc spans lines.
        writeJavaSource(projectDir, "src/main/java", ORDINARY_TYPE, "naming-the-call-in-prose-only");

        var result = runGateExpectingSuccess(projectDir, CONTAINED_TO_THE_ADAPTER);

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenTheCallingFileIsInTheDefaultPackage(@TempDir Path projectDir)
            throws IOException {

        // A file with no package declaration sits under no root, so no
        // restriction names it - and reading its calls against one would be
        // guesswork.
        writeJavaSource(projectDir, "src/main/java", ORDINARY_TYPE, "caller-in-the-default-package");

        var result = runGateExpectingSuccess(projectDir, CONTAINED_TO_THE_ADAPTER);

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenNothingMakesTheRestrictedCall(@TempDir Path projectDir)
            throws IOException {

        writeJavaSource(projectDir, "src/main/java", ORDINARY_TYPE, "calling-nothing-restricted");

        var result = runGateExpectingSuccess(projectDir, CONTAINED_TO_THE_ADAPTER);

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenTheBuildDeclaresNoRestriction(@TempDir Path projectDir)
            throws IOException {

        // Every consumer inherits the gate from the shared conventions, so a
        // build that never says what its seams are must check nothing rather
        // than guess at them.
        writeJavaSource(projectDir, "src/main/java", ORDINARY_TYPE, "caller-in-the-closed-root");

        var result = runGateExpectingSuccess(projectDir, NO_RESTRICTION_DECLARED);

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenThereIsNoSourceTree(@TempDir Path projectDir)
            throws IOException {

        var result = runGateExpectingSuccess(projectDir, CONTAINED_TO_THE_ADAPTER);

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    // One place owns the block's syntax, so a case that varies the restrictions
    // varies only the restrictions. Each argument is one 'restrictCall' argument
    // list.
    private static String buildRestrictionDeclaration(String... restrictCallArgumentLists) {

        var declaration = new StringBuilder("enforceRestrictedCalls {\n");

        for (var argumentList : restrictCallArgumentLists) {
            declaration.append("    restrictCall ").append(argumentList).append('\n');
        }
        return declaration.append("}\n").toString();
    }

    // The expected outcome is in the method name rather than a flag, so a call
    // site reads as what it asserts. Both forward to one runner: the pair
    // differs only in which TestKit terminal it drives.
    private BuildResult runGateExpectingFailure(Path projectDir, String restrictionDeclaration)
            throws IOException {

        return buildRunner(projectDir, restrictionDeclaration).buildAndFail();
    }

    private BuildResult runGateExpectingSuccess(Path projectDir, String restrictionDeclaration)
            throws IOException {

        return buildRunner(projectDir, restrictionDeclaration).build();
    }

    private GradleRunner buildRunner(Path projectDir, String restrictionDeclaration)
            throws IOException {

        // An explicit settings file stops Gradle walking up into a real build.
        Files.writeString(
            projectDir.resolve("settings.gradle"),
            "rootProject.name = 'gate-fixture'\n");

        Files.writeString(
            projectDir.resolve("build.gradle"),
            "apply from: '" + scriptPath() + "'\n" + restrictionDeclaration);

        return GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments("enforceRestrictedCalls");
    }

    // The source root and the type name are both parameters: every source set is
    // a case of the same rule, and which type a file counts as is the thing the
    // allowed list is matched against.
    private void writeJavaSource(
            Path projectDir,
            String sourceRoot,
            String typeName,
            String fixture) throws IOException {

        var dir = projectDir.resolve(sourceRoot);

        Files.createDirectories(dir);
        Files.writeString(dir.resolve(typeName + ".java"), loadFixture("java/" + fixture));
    }

    // The gate script path is handed in by the test task so the test does not
    // assume a working directory; forward slashes keep it valid inside the
    // generated build script on Windows.
    private String scriptPath() {
        return System.getProperty("restricted.calls.gate.script.path").replace('\\', '/');
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
