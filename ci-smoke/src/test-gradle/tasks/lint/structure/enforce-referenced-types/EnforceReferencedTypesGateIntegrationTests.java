import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Integration test for the shared enforce-referenced-types gate: it applies the
// real gate script into a throwaway project, compiles that project, and runs the
// task - asserting the build outcome and the message a developer sees. Lives in
// the Common-Java ci-smoke project, the gate script being shared by every
// consumer.
//
// The fixtures are a library standing in for any whose internal names are not
// promised, and a consumer built against it. Both are compiled by the throwaway
// project, since what this gate reads is class files: a fixture filed as source
// alone would leave it with nothing to scan.
//
// The library sits under the declared namespace, so the gate passes over its own
// classes and judges the consumer's - which is the shape a real build has, the
// library arriving as a jar rather than as sources.
class EnforceReferencedTypesGateIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest("enforceReferencedTypes", "referenced.types.gate.script.path");

    private static final String TASK_PATH = ":enforceReferencedTypes";

    // What the consuming build declares, and what the fixtures are written
    // against: one namespace, one published package inside it, one type named on
    // its own.
    private static final String NAMESPACE = "example.library";
    private static final String PUBLISHED_PACKAGE = "example.library.api";
    private static final String SEAM_TYPE = "example.library.internal.Seam";
    private static final String UNPROMISED_TYPE = "example/library/internal/Renamed";

    private static final String NOTHING_DECLARED = "";

    private static final String PUBLISHED_PACKAGE_AND_THE_SEAM = buildNamespaceDeclaration(
        "namespace: '" + NAMESPACE
            + "', allowingPackages: ['" + PUBLISHED_PACKAGE
            + "'], allowingTypes: ['" + SEAM_TYPE + "']");

    private static final String THE_PUBLISHED_PACKAGE_ALONE = buildNamespaceDeclaration(
        "namespace: '" + NAMESPACE + "', allowingPackages: ['" + PUBLISHED_PACKAGE + "']");

    private static final String NOTHING_IN_THE_NAMESPACE = buildNamespaceDeclaration(
        "namespace: '" + NAMESPACE + "'");

    // A declaration naming only what is allowed, which says nothing about where -
    // the shape a misspelt or forgotten 'namespace:' takes.
    private static final String HALF_DECLARED_NAMESPACE = buildNamespaceDeclaration(
        "allowingPackages: ['" + PUBLISHED_PACKAGE + "']");

    @Test
    void failsWhenCompiledCodeCarriesAnUnpromisedNameTheSourceNeverSpells(
            @TempDir Path projectDir) {

        // The case the gate exists for, and the one no source-text pass can
        // reach: the consumer names the seam, and the compiler writes the type
        // that seam hands back into its own descriptor. That descriptor is what
        // the runtime resolves, so a build spelling the type differently fails at
        // the call.
        var result = buildProject(projectDir, PUBLISHED_PACKAGE_AND_THE_SEAM)
            .holdingFixture(
                "src/main/java/example/consumer/Caller.java",
                "java/caller-taking-a-value-from-the-seam")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("Caller in java/main references " + UNPROMISED_TYPE
                + ", which example.library does not promise to keep spelled that way");
    }

    @Test
    void failsWhenASiblingOfAnAllowedTypeIsNamedOutright(@TempDir Path projectDir) {

        // The plain form of the same defect, and the case that says an allowance
        // is per type rather than per package: the type named here sits in the
        // seam's own package, and letting the seam's allowance carry its
        // neighbours would admit the next one unremarked.
        var result = buildProject(projectDir, PUBLISHED_PACKAGE_AND_THE_SEAM)
            .holdingFixture(
                "src/main/java/example/consumer/Caller.java",
                "java/caller-naming-the-unpromised-type")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("references " + UNPROMISED_TYPE);
    }

    @Test
    void failsWhenTestCodeNamesAnUnpromisedType(@TempDir Path projectDir) {

        // A suite is compiled against the same library, so a rule that stopped at
        // the production tree would be a rule with a hole in it.
        var result = buildProject(projectDir, PUBLISHED_PACKAGE_AND_THE_SEAM)
            .holdingFixture(
                "src/test/java/example/consumer/Caller.java",
                "java/caller-naming-the-unpromised-type")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("Caller in java/test references " + UNPROMISED_TYPE);
    }

    @Test
    void passesWhenOnlyThePublishedPackageIsNamed(@TempDir Path projectDir) {

        var result = buildProject(projectDir, THE_PUBLISHED_PACKAGE_ALONE)
            .holdingFixture(
                "src/main/java/example/consumer/Caller.java",
                "java/caller-naming-the-published-type")
            .runExpectingSuccess();

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenATypeNestedInAnAllowedOneIsNamed(@TempDir Path projectDir) {

        // A nested type is declared with its outer one, promised by the same
        // decision and renamed by the same release, so the allowance covers it.
        var result = buildProject(projectDir, PUBLISHED_PACKAGE_AND_THE_SEAM)
            .holdingFixture(
                "src/main/java/example/consumer/Caller.java",
                "java/caller-naming-a-type-nested-in-the-seam")
            .runExpectingSuccess();

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesOverTheNamespacesOwnClasses(@TempDir Path projectDir) {

        // The library's own seam hands back the unpromised type, so a scan that
        // judged it would report the library to itself. Nothing in the namespace
        // is asked to keep a promise it is the one making.
        var result = buildProject(projectDir, NOTHING_IN_THE_NAMESPACE)
            .runExpectingSuccess();

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenNoNamespaceIsDeclared(@TempDir Path projectDir) {

        // What every consumer that never declares one inherits, which is what
        // lets the conventions file apply the gate everywhere.
        var result = buildProject(projectDir, NOTHING_DECLARED)
            .holdingFixture(
                "src/main/java/example/consumer/Caller.java",
                "java/caller-naming-the-unpromised-type")
            .runExpectingSuccess();

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void passesWhenThereIsNothingCompiled(@TempDir Path projectDir) {

        var result = GateProject
            .driving(GATE, projectDir)
            .applyingTheJavaPlugin()
            .configuringTheGate(PUBLISHED_PACKAGE_AND_THE_SEAM)
            .runExpectingSuccess();

        assertThat(result.task(TASK_PATH).getOutcome())
            .isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void failsTheBuildWhenTheDeclarationNamesNoNamespace(@TempDir Path projectDir) {

        // Half a rule is not a state anything may hold: without the namespace
        // there is nothing to judge, and a build that accepted it would report
        // nothing while reading as though it were enforcing something.
        var result = buildProject(projectDir, HALF_DECLARED_NAMESPACE)
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("restrictReferences needs 'namespace'");
    }

    // One place owns the block's syntax, so a case that varies the declaration
    // varies only the declaration. Each argument is one 'restrictReferences'
    // argument list.
    private static String buildNamespaceDeclaration(String... restrictReferencesArgumentLists) {

        var declaration = new StringBuilder("enforceReferencedTypes {\n");

        for (var argumentList : restrictReferencesArgumentLists) {

            declaration
                .append("    restrictReferences ")
                .append(argumentList)
                .append('\n');
        }
        return declaration
            .append("}\n")
            .toString();
    }

    // The library every case is posed against, staged into the project so the
    // throwaway build compiles it alongside the consumer. Staged for every case,
    // including the ones that name none of it: a consumer compiles against the
    // whole library whichever part of it that consumer reaches for.
    private GateProject buildProject(Path projectDir, String declaration) {

        return GateProject
            .driving(GATE, projectDir)
            .applyingTheJavaPlugin()
            .configuringTheGate(declaration)
            .holdingFixture(
                "src/main/java/example/library/internal/Seam.java",
                "java/library-seam")
            .holdingFixture(
                "src/main/java/example/library/internal/Renamed.java",
                "java/library-renamed")
            .holdingFixture(
                "src/main/java/example/library/api/Published.java",
                "java/library-published");
    }
}
