import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

// Drives the shared enforce-referenced-types gate, compiling the throwaway project before the task
// runs.
//
// The fixtures are a library standing in for any whose internal names are not promised, and a
// consumer built against it. Both are compiled by the throwaway project, since what this gate reads
// is class files: a fixture filed as source alone would leave it with nothing to scan.
//
// The library sits under the declared namespace, so the gate passes over its own classes and judges
// the consumer's - which is the shape a real build has, the library arriving as a jar rather than as
// sources.
class EnforceReferencedTypesGateIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest("enforceReferencedTypes", "referenced.types.gate.script.path");

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

    // A namespace closed in the shipped tree alone - the shape of a rule about what
    // the runtime refuses to load, which a suite on a plain JVM is free to use.
    private static final String NOTHING_IN_THE_NAMESPACE_IN_MAIN = buildNamespaceDeclaration(
        "namespace: '" + NAMESPACE + "', inSourceSets: ['main']");

    private static final String RUNTIME_REFUSAL = "the runtime refuses to load it";

    private static final String NOTHING_IN_THE_NAMESPACE_WITH_A_REASON = buildNamespaceDeclaration(
        "namespace: '" + NAMESPACE + "', reason: '" + RUNTIME_REFUSAL + "'");

    private static final String NOTHING_IN_THE_NAMESPACE_IN_A_MISSPELT_SOURCE_SET = buildNamespaceDeclaration(
        "namespace: '" + NAMESPACE + "', inSourceSets: ['mian']");

    private static final String THE_NAMESPACE_DECLARED_TWICE = buildNamespaceDeclaration(
        "namespace: '" + NAMESPACE + "', inSourceSets: ['main']",
        "namespace: '" + NAMESPACE + "', inSourceSets: ['test']");

    // A declaration naming only what is allowed, which says nothing about where -
    // the shape a misspelt or forgotten 'namespace:' takes.
    private static final String HALF_DECLARED_NAMESPACE = buildNamespaceDeclaration(
        "allowingPackages: ['" + PUBLISHED_PACKAGE + "']");

    // The class file the unreadable-class case poses, by the names the format
    // gives each part. Roomier than the file needs to be, the buffer being
    // trimmed to what was written.
    private static final int CLASS_FILE_CAPACITY = 64;
    private static final int CLASS_FILE_MAGIC = 0xCAFEBABE;
    private static final short CLASS_FILE_MINOR_VERSION = 0;
    private static final short CLASS_FILE_MAJOR_VERSION = 99;

    // The pool is numbered from one and counted one past its last entry, so this
    // says two: the text constant, and the tag the walk gives up on.
    private static final short POOL_ENTRY_COUNT = 3;

    private static final byte CONSTANT_UTF8_TAG = 1;

    // No class-file version has defined this tag, which is what makes it stand
    // for one from a version the gate has not been taught.
    private static final byte UNDEFINED_CONSTANT_TAG = (byte) 254;

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
    void passesWhenTestCodeNamesATypeTheRuleHoldsOnlyInMain(@TempDir Path projectDir) {

        // A rule scoped to the shipped tree leaves the suites alone: what the
        // runtime refuses is no concern of a JVM that never runs the game.
        buildProject(projectDir, NOTHING_IN_THE_NAMESPACE_IN_MAIN)
            .holdingFixture(
                "src/test/java/example/consumer/Caller.java",
                "java/caller-naming-the-unpromised-type")
            .runExpectingGateToPass();
    }

    @Test
    void failsWhenMainCodeNamesATypeTheRuleHoldsOnlyInMain(@TempDir Path projectDir) {

        var result = buildProject(projectDir, NOTHING_IN_THE_NAMESPACE_IN_MAIN)
            .holdingFixture(
                "src/main/java/example/consumer/Caller.java",
                "java/caller-naming-the-unpromised-type")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("Caller in java/main references " + UNPROMISED_TYPE);
    }

    @Test
    void failsWhenARuleNamesASourceSetTheBuildDoesNotHave(@TempDir Path projectDir) {

        // A misspelt source set matches no compiled tree, so a rule held to it
        // would check nothing and pass - the failure direction the gate refuses.
        var result = buildProject(projectDir, NOTHING_IN_THE_NAMESPACE_IN_A_MISSPELT_SOURCE_SET)
            .holdingFixture(
                "src/main/java/example/consumer/Caller.java",
                "java/caller-naming-the-unpromised-type")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("names source set(s) [mian] this build does not have");
    }

    @Test
    void failsTheBuildWhenANamespaceIsDeclaredTwice(@TempDir Path projectDir) {

        // What was named is gathered per namespace, so two rules over one would
        // judge each other's allowances against a merged set.
        var result = buildProject(projectDir, THE_NAMESPACE_DECLARED_TWICE)
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("restrictReferences declares '" + NAMESPACE + "' a second time");
    }

    @Test
    void endsAFindingWithTheReasonTheRuleGives(@TempDir Path projectDir) {

        // The default sentence is about unstable spellings, which is false for a
        // namespace closed for any other reason.
        var result = buildProject(projectDir, NOTHING_IN_THE_NAMESPACE_WITH_A_REASON)
            .holdingFixture(
                "src/main/java/example/consumer/Caller.java",
                "java/caller-naming-the-unpromised-type")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("references " + UNPROMISED_TYPE + ", which " + RUNTIME_REFUSAL)
            .doesNotContain("does not promise to keep spelled that way");
    }

    @Test
    void passesWhenOnlyThePublishedPackageIsNamed(@TempDir Path projectDir) {

        buildProject(projectDir, THE_PUBLISHED_PACKAGE_ALONE)
            .holdingFixture(
                "src/main/java/example/consumer/Caller.java",
                "java/caller-naming-the-published-type")
            .runExpectingGateToPass();
    }

    @Test
    void passesWhenATypeNestedInAnAllowedOneIsNamed(@TempDir Path projectDir) {

        // A nested type is declared with its outer one, promised by the same
        // decision and renamed by the same release, so the allowance covers it.
        buildProject(projectDir, PUBLISHED_PACKAGE_AND_THE_SEAM)
            .holdingFixture(
                "src/main/java/example/consumer/Caller.java",
                "java/caller-naming-a-type-nested-in-the-seam")
            .runExpectingGateToPass();
    }

    @Test
    void passesOverTheNamespacesOwnClasses(@TempDir Path projectDir) {

        // The library's own seam hands back the unpromised type, so a scan that
        // judged it would report the library to itself. Nothing in the namespace
        // is asked to keep a promise it is the one making.
        buildProject(projectDir, NOTHING_IN_THE_NAMESPACE)
            .runExpectingGateToPass();
    }

    @Test
    void failsWhenAnAllowedTypeIsNamedByNothing(@TempDir Path projectDir) {

        // The same defect from the other end, and the one the scan alone cannot
        // show: the allowance reads as a dependency this project has, and
        // whoever writes the next reference to that type finds it pre-approved.
        // Failed rather than warned because a warning here is read by nobody -
        // the gate is skipped while its inputs hold, so it would be said once.
        var result = buildProject(projectDir, PUBLISHED_PACKAGE_AND_THE_SEAM)
            .holdingFixture(
                "src/main/java/example/consumer/Caller.java",
                "java/caller-naming-the-published-type")
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains(SEAM_TYPE + " is allowed for " + NAMESPACE
                + " and nothing compiled here names it");
    }

    @Test
    void passesWhenNoNamespaceIsDeclared(@TempDir Path projectDir) {

        // What every consumer that never declares one inherits, which is what
        // lets the conventions file apply the gate everywhere.
        buildProject(projectDir, NOTHING_DECLARED)
            .holdingFixture(
                "src/main/java/example/consumer/Caller.java",
                "java/caller-naming-the-unpromised-type")
            .runExpectingGateToPass();
    }

    @Test
    void passesWhenThereIsNothingCompiled(@TempDir Path projectDir) {

        GateProject
            .driving(GATE, projectDir)
            .applyingTheJavaPlugin()
            .configuringTheGate(PUBLISHED_PACKAGE_AND_THE_SEAM)
            .runExpectingGateToPass();
    }

    @Test
    void failsWhenAClassCarriesAConstantPoolTagItCannotFollow(@TempDir Path projectDir) {

        // The gate's own gap rather than the code's: a tag from a class-file
        // version this walk does not know leaves it reading the class as holding
        // nothing, which is indistinguishable from a class that passed. Failed
        // rather than warned all the same - a gate that cannot read a class
        // cannot promise the one thing it exists to promise, and what it guards
        // against only shows up on a machine the author does not have.
        //
        // Staged into a tree no compile task owns, so the file is still there
        // when the gate reads: Gradle clears the stale outputs of a task that
        // owns a directory, which would take a staged file with them. It is
        // scanned all the same, the gate taking the build's class output whole.
        var result = buildProject(projectDir, THE_PUBLISHED_PACKAGE_ALONE)
            .holdingBytes(
                "build/classes/java/staged/example/consumer/Odd.class",
                buildClassFileCarryingAnUnknownTag())
            .runExpectingFailure();

        assertThat(result.getOutput())
            .contains("Classes this gate could not read")
            .contains("example/consumer/Odd");
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

    // A class file the walk gets one entry into and then cannot follow: a header,
    // a pool of two entries, one text constant, and a tag no class-file version
    // has defined. Stated as bytes because no compiler emits one - what is being
    // posed is a file from a future the gate has not been taught, and the only
    // way to have one today is to write it.
    private static byte[] buildClassFileCarryingAnUnknownTag() {

        var classFile = ByteBuffer.allocate(CLASS_FILE_CAPACITY);

        classFile.putInt(CLASS_FILE_MAGIC);
        classFile.putShort(CLASS_FILE_MINOR_VERSION);
        classFile.putShort(CLASS_FILE_MAJOR_VERSION);
        classFile.putShort(POOL_ENTRY_COUNT);

        // A text constant naming the namespace, so a walk that carried on past
        // the tag below would have something to report - which is what says the
        // gate stopped rather than merely found nothing.
        classFile.put(CONSTANT_UTF8_TAG);

        var text = ("L" + UNPROMISED_TYPE + ";").getBytes(StandardCharsets.UTF_8);

        classFile.putShort((short) text.length);
        classFile.put(text);
        classFile.put(UNDEFINED_CONSTANT_TAG);

        return Arrays.copyOf(classFile.array(), classFile.position());
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
