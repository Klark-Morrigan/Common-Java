import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

// Drives the shared enforce-referenced-types gate, compiling the throwaway project before the task
// runs: what this gate reads is class files.
//
// The fixtures are a library whose internal names are not promised, and a consumer built against
// it. The library sits under the declared namespace, so the gate passes over its classes and judges
// the consumer's - the shape a real build has, with the library arriving as a jar.
class EnforceReferencedTypesGateIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest("enforceReferencedTypes", "lint/structure/enforce-referenced-types");

    private static final String EXTENSION = "enforceReferencedTypes";

    // One namespace, one published package inside it, one type named on its own.
    private static final String NAMESPACE = "example.library";
    private static final String PUBLISHED_PACKAGE = "example.library.api";
    private static final String SEAM_TYPE = "example.library.internal.Seam";
    private static final String UNPROMISED_TYPE = "example/library/internal/Renamed";
    private static final String UNRELATED_NAMESPACE = "example.unrelated";

    private static final String CALLER_PATH = "/example/consumer/Caller.java";

    private static final String NOTHING_DECLARED = "";

    private static final String PUBLISHED_PACKAGE_AND_THE_SEAM = GateProject.buildConfigurationBlock(
        EXTENSION,
        "restrictReferences namespace: '" + NAMESPACE + "', allowingPackages: ['" + PUBLISHED_PACKAGE +
            "'], allowingTypes: ['" + SEAM_TYPE + "']");

    private static final String THE_PUBLISHED_PACKAGE_ALONE = GateProject.buildConfigurationBlock(
        EXTENSION,
        "restrictReferences namespace: '" + NAMESPACE + "', allowingPackages: ['" + PUBLISHED_PACKAGE + "']");

    private static final String NOTHING_IN_THE_NAMESPACE = GateProject.buildConfigurationBlock(
        EXTENSION,
        "restrictReferences namespace: '" + NAMESPACE + "'");

    // Closed in the shipped tree alone: a rule about what the runtime refuses to load.
    private static final String NOTHING_IN_THE_NAMESPACE_IN_MAIN = GateProject.buildConfigurationBlock(
        EXTENSION,
        "restrictReferences namespace: '" + NAMESPACE + "', inSourceSets: 'main'");

    private static final String RUNTIME_REFUSAL = "the runtime refuses to load it";

    private static final String NOTHING_IN_THE_NAMESPACE_WITH_A_REASON = GateProject.buildConfigurationBlock(
        EXTENSION,
        "restrictReferences namespace: '" + NAMESPACE + "', reason: '" + RUNTIME_REFUSAL + "'");

    private static final String NOTHING_IN_THE_NAMESPACE_IN_A_MISSPELT_SOURCE_SET =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "restrictReferences namespace: '" + NAMESPACE + "', inSourceSets: ['mian']");

    private static final String THE_NAMESPACE_DECLARED_TWICE = GateProject.buildConfigurationBlock(
        EXTENSION,
        "restrictReferences namespace: '" + NAMESPACE + "', inSourceSets: ['main']",
        "restrictReferences namespace: '" + NAMESPACE + "', inSourceSets: ['test']");

    // The shape a misspelt or forgotten 'namespace:' takes.
    private static final String HALF_DECLARED_NAMESPACE = GateProject.buildConfigurationBlock(
        EXTENSION,
        "restrictReferences allowingPackages: ['" + PUBLISHED_PACKAGE + "']");

    // A rule held to the test tree, where nothing is compiled, beside a rule that reads every class.
    private static final String THE_SEAM_IN_AN_EMPTY_TREE_BESIDE_ANOTHER_RULE = GateProject.buildConfigurationBlock(
        EXTENSION,
        "restrictReferences namespace: '" + NAMESPACE + "', inSourceSets: ['test'], allowingTypes: '" +
            SEAM_TYPE + "'",
        "restrictReferences namespace: '" + UNRELATED_NAMESPACE + "'");

    // The class file the unreadable-class case poses, by the names the format gives each part. The
    // buffer is roomier than the file and trimmed to what was written.
    private static final int CLASS_FILE_CAPACITY = 64;
    private static final int CLASS_FILE_MAGIC = 0xCAFEBABE;
    private static final short CLASS_FILE_MINOR_VERSION = 0;
    private static final short CLASS_FILE_MAJOR_VERSION = 99;

    // Numbered from one and counted one past the last entry: the text constant, then the tag the
    // walk gives up on.
    private static final short POOL_ENTRY_COUNT = 3;

    private static final byte CONSTANT_UTF8_TAG = 1;

    // No class-file version has defined this tag.
    private static final byte UNDEFINED_CONSTANT_TAG = (byte) 254;

    @Nested
    class Declarations {

        @Test
        void failsWhenTheDeclarationNamesNoNamespace(@TempDir Path projectDir) {

            var result = stageTheLibrary(projectDir, HALF_DECLARED_NAMESPACE)
                .runExpectingFailure();

            assertThat(result.getOutput())
                .contains("restrictReferences needs 'namespace'");
        }

        @Test
        void failsWhenANamespaceIsDeclaredTwice(@TempDir Path projectDir) {

            // Two rules over one namespace would judge each other's allowances against a merged set.
            var result = stageTheLibrary(projectDir, THE_NAMESPACE_DECLARED_TWICE)
                .runExpectingFailure();

            assertThat(result.getOutput())
                .contains("restrictReferences declares '" + NAMESPACE + "' a second time");
        }

        @Test
        void failsWhenARuleNamesASourceSetTheBuildDoesNotHave(@TempDir Path projectDir) {

            // A misspelt source set matches no compiled tree, so its rule would check nothing.
            var result = stageTheLibrary(projectDir, NOTHING_IN_THE_NAMESPACE_IN_A_MISSPELT_SOURCE_SET)
                .holdingFixture(GateProject.MAIN_JAVA_ROOT + CALLER_PATH, "java/caller-naming-the-unpromised-type")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("names source set(s) [mian] this build does not have");
        }

        @Test
        void passesWhenNoNamespaceIsDeclared(@TempDir Path projectDir) {

            stageTheLibrary(projectDir, NOTHING_DECLARED)
                .holdingFixture(GateProject.MAIN_JAVA_ROOT + CALLER_PATH, "java/caller-naming-the-unpromised-type")
                .runExpectingGateToPass();
        }

        @Test
        void failsAgainAfterAPassingRunOnceARuleIsDeclared(@TempDir Path projectDir) {

            // Were the rules not a task input, the second run would be UP-TO-DATE.
            stageTheLibrary(projectDir, NOTHING_DECLARED)
                .holdingFixture(GateProject.MAIN_JAVA_ROOT + CALLER_PATH, "java/caller-naming-the-unpromised-type")
                .runExpectingGateToPass();

            var result = stageTheLibrary(projectDir, NOTHING_IN_THE_NAMESPACE)
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("example/consumer/Caller in java/main references " + UNPROMISED_TYPE);
        }
    }

    @Nested
    class References {

        @Test
        void failsWhenCompiledCodeCarriesAnUnpromisedNameTheSourceNeverSpells(@TempDir Path projectDir) {

            // The case no source-text pass can reach: the compiler writes the type the seam hands
            // back into the caller's own descriptor.
            var result = stageTheLibrary(projectDir, PUBLISHED_PACKAGE_AND_THE_SEAM)
                .holdingFixture(GateProject.MAIN_JAVA_ROOT + CALLER_PATH, "java/caller-taking-a-value-from-the-seam")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("example/consumer/Caller in java/main references " + UNPROMISED_TYPE +
                    ", which example.library does not promise to keep spelled that way");
        }

        @Test
        void failsWhenASiblingOfAnAllowedTypeIsNamedOutright(@TempDir Path projectDir) {

            // An allowance is per type: the type named here sits in the seam's own package.
            var result = stageTheLibrary(projectDir, PUBLISHED_PACKAGE_AND_THE_SEAM)
                .holdingFixture(GateProject.MAIN_JAVA_ROOT + CALLER_PATH, "java/caller-naming-the-unpromised-type")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("example/consumer/Caller in java/main references " + UNPROMISED_TYPE);
        }

        @Test
        void failsWhenTheUnpromisedTypeFollowsAPrimitiveInADescriptor(@TempDir Path projectDir) {

            // '(ILexample/library/internal/Renamed;)V': the descriptor's 'L' follows a primitive.
            var result = stageTheLibrary(projectDir, NOTHING_IN_THE_NAMESPACE)
                .holdingFixture(
                    GateProject.MAIN_JAVA_ROOT + CALLER_PATH,
                    "java/caller-taking-the-unpromised-type-after-a-primitive")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("example/consumer/Caller in java/main references " + UNPROMISED_TYPE);
        }

        @Test
        void passesWhenATypeOnlyContainsTheNamespaceBelowAnotherPackage(@TempDir Path projectDir) {

            // 'other/example/library/Lookalike' holds the namespace's segments but is not under it.
            stageTheLibrary(projectDir, NOTHING_IN_THE_NAMESPACE)
                .holdingFixture(
                    GateProject.MAIN_JAVA_ROOT + "/other/example/library/Lookalike.java",
                    "java/library-lookalike")
                .holdingFixture(GateProject.MAIN_JAVA_ROOT + CALLER_PATH, "java/caller-naming-a-lookalike-type")
                .runExpectingGateToPass();
        }

        @Test
        void failsEndingAFindingWithTheReasonTheRuleGives(@TempDir Path projectDir) {

            // The default sentence is about unstable spellings, which is false for any other reason.
            var result = stageTheLibrary(projectDir, NOTHING_IN_THE_NAMESPACE_WITH_A_REASON)
                .holdingFixture(GateProject.MAIN_JAVA_ROOT + CALLER_PATH, "java/caller-naming-the-unpromised-type")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("references " + UNPROMISED_TYPE + ", which " + RUNTIME_REFUSAL)
                .doesNotContain("does not promise to keep spelled that way");
        }

        @Test
        void passesWhenOnlyThePublishedPackageIsNamed(@TempDir Path projectDir) {

            stageTheLibrary(projectDir, THE_PUBLISHED_PACKAGE_ALONE)
                .holdingFixture(GateProject.MAIN_JAVA_ROOT + CALLER_PATH, "java/caller-naming-the-published-type")
                .runExpectingGateToPass();
        }

        @Test
        void passesWhenATypeNestedInAnAllowedOneIsNamed(@TempDir Path projectDir) {

            stageTheLibrary(projectDir, PUBLISHED_PACKAGE_AND_THE_SEAM)
                .holdingFixture(
                    GateProject.MAIN_JAVA_ROOT + CALLER_PATH,
                    "java/caller-naming-a-type-nested-in-the-seam")
                .runExpectingGateToPass();
        }

        @Test
        void passesOverTheNamespacesOwnClasses(@TempDir Path projectDir) {

            // The library's own seam hands back the unpromised type; nothing in the namespace is
            // held to the promise it is the one making.
            stageTheLibrary(projectDir, NOTHING_IN_THE_NAMESPACE)
                .runExpectingGateToPass();
        }
    }

    @Nested
    class SourceSets {

        @Test
        void failsWhenTestCodeNamesAnUnpromisedType(@TempDir Path projectDir) {

            var result = stageTheLibrary(projectDir, PUBLISHED_PACKAGE_AND_THE_SEAM)
                .holdingFixture(GateProject.TEST_JAVA_ROOT + CALLER_PATH, "java/caller-naming-the-unpromised-type")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("example/consumer/Caller in java/test references " + UNPROMISED_TYPE);
        }

        @Test
        void passesWhenTestCodeNamesATypeTheRuleHoldsOnlyInMain(@TempDir Path projectDir) {

            stageTheLibrary(projectDir, NOTHING_IN_THE_NAMESPACE_IN_MAIN)
                .holdingFixture(GateProject.TEST_JAVA_ROOT + CALLER_PATH, "java/caller-naming-the-unpromised-type")
                .runExpectingGateToPass();
        }

        @Test
        void failsWhenMainCodeNamesATypeTheRuleHoldsOnlyInMain(@TempDir Path projectDir) {

            var result = stageTheLibrary(projectDir, NOTHING_IN_THE_NAMESPACE_IN_MAIN)
                .holdingFixture(GateProject.MAIN_JAVA_ROOT + CALLER_PATH, "java/caller-naming-the-unpromised-type")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("example/consumer/Caller in java/main references " + UNPROMISED_TYPE);
        }
    }

    @Nested
    class Allowances {

        @Test
        void failsWhenAnAllowedTypeIsNamedByNothing(@TempDir Path projectDir) {

            // The allowance reads as a dependency this project has, and pre-approves the next one.
            var result = stageTheLibrary(projectDir, PUBLISHED_PACKAGE_AND_THE_SEAM)
                .holdingFixture(GateProject.MAIN_JAVA_ROOT + CALLER_PATH, "java/caller-naming-the-published-type")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains(SEAM_TYPE + " is allowed for " + NAMESPACE + " and nothing compiled here names it");
        }

        @Test
        void passesWhenTheRuleAllowingTheTypeReadsNoClass(@TempDir Path projectDir) {

            // The other rule reading every class must not count as this rule having read one.
            stageTheLibrary(projectDir, THE_SEAM_IN_AN_EMPTY_TREE_BESIDE_ANOTHER_RULE)
                .holdingFixture(GateProject.MAIN_JAVA_ROOT + CALLER_PATH, "java/caller-naming-the-published-type")
                .runExpectingGateToPass();
        }

        @Test
        void passesWhenThereIsNothingCompiled(@TempDir Path projectDir) {

            GateProject.driving(GATE, projectDir)
                .applyingTheJavaPlugin()
                .configuringTheGate(PUBLISHED_PACKAGE_AND_THE_SEAM)
                .runExpectingGateToPass();
        }
    }

    @Nested
    class ClassFiles {

        @Test
        void failsWhenAClassCarriesAConstantPoolTagItCannotFollow(@TempDir Path projectDir) {

            // A tag from a version the walk does not know leaves the class checked for nothing.
            //
            // Staged into a tree no compile task owns, since Gradle clears a compile task's stale
            // outputs. The gate takes the build's class output whole, so it is scanned all the same.
            var result = stageTheLibrary(projectDir, THE_PUBLISHED_PACKAGE_ALONE)
                .holdingBytes(
                    "build/classes/java/staged/example/consumer/Odd.class",
                    buildClassFileCarryingAnUnknownTag())
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Classes this gate could not read")
                .contains("example/consumer/Odd");
        }
    }

    // A header, a pool of two entries, one text constant naming the namespace, and a tag no
    // class-file version has defined. The text constant is what a walk carrying on past the tag would
    // report, which says the gate stopped rather than found nothing.
    private static byte[] buildClassFileCarryingAnUnknownTag() {

        var classFile = ByteBuffer.allocate(CLASS_FILE_CAPACITY);

        classFile.putInt(CLASS_FILE_MAGIC);
        classFile.putShort(CLASS_FILE_MINOR_VERSION);
        classFile.putShort(CLASS_FILE_MAJOR_VERSION);
        classFile.putShort(POOL_ENTRY_COUNT);
        classFile.put(CONSTANT_UTF8_TAG);

        var text = ("L" + UNPROMISED_TYPE + ";").getBytes(StandardCharsets.UTF_8);

        classFile.putShort((short) text.length);
        classFile.put(text);
        classFile.put(UNDEFINED_CONSTANT_TAG);

        return Arrays.copyOf(classFile.array(), classFile.position());
    }

    // Stages the library every case is posed against, since a consumer compiles against all of it
    // whichever part it reaches for.
    private static GateProject stageTheLibrary(Path projectDir, String declaration) {

        return GateProject.driving(GATE, projectDir)
            .applyingTheJavaPlugin()
            .configuringTheGate(declaration)
            .holdingFixture(GateProject.MAIN_JAVA_ROOT + "/example/library/internal/Seam.java", "java/library-seam")
            .holdingFixture(
                GateProject.MAIN_JAVA_ROOT + "/example/library/internal/Renamed.java",
                "java/library-renamed")
            .holdingFixture(
                GateProject.MAIN_JAVA_ROOT + "/example/library/api/Published.java",
                "java/library-published");
    }
}
