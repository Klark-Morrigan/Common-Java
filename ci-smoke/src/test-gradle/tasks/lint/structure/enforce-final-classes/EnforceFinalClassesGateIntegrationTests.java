import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

// Drives the shared enforce-final-classes gate, compiling the throwaway project before the task runs:
// what this gate reads is the access flags in class files.
//
// The fixtures sit under one root, 'example.app', which keeps a published subtree, 'example.app.api',
// open on purpose. Each fixture declares one class closed to extension or open to it.
class EnforceFinalClassesGateIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest(
            "enforceFinalClasses",
            "lint/structure/enforce-final-classes");

    private static final String EXTENSION = "enforceFinalClasses";

    private static final String APP_ROOT_PATH = "/example/app/";
    private static final String OPEN_CLASS_PATH = APP_ROOT_PATH + "OpenClass.java";
    private static final String OPEN_CLASS_FIXTURE = "java/extension-open-class";
    private static final String FINAL_CLASS_PATH = APP_ROOT_PATH + "FinalClass.java";
    private static final String FINAL_CLASS_FIXTURE = "java/extension-closed-by-final";

    private static final String NOTHING_DECLARED = "";

    private static final String THE_APP_ROOT = GateProject.buildConfigurationBlock(
        EXTENSION,
        "requireFinal under: 'example.app'");

    private static final String THE_APP_ROOT_LESS_ITS_PUBLISHED_ONE = GateProject.buildConfigurationBlock(
        EXTENSION,
        "requireFinal under: 'example.app', except: 'example.app.api'");

    private static final String PUBLISHED_ROOT_REASON = "only example.app.api is published to build on";

    private static final String THE_APP_ROOT_WITH_ITS_REASON = GateProject.buildConfigurationBlock(
        EXTENSION,
        "requireFinal under: 'example.app', reason: '" + PUBLISHED_ROOT_REASON + "'");

    private static final String THE_APP_ROOT_IN_MAIN = GateProject.buildConfigurationBlock(
        EXTENSION,
        "requireFinal under: 'example.app', inSourceSets: 'main'");

    // Both rules hold every class under the inner root.
    private static final String THE_APP_ROOT_TWICE = GateProject.buildConfigurationBlock(
        EXTENSION,
        "requireFinal under: 'example'",
        "requireFinal under: 'example.app'");

    private static final String THE_APP_ROOT_IN_A_MISSPELT_SOURCE_SET = GateProject.buildConfigurationBlock(
        EXTENSION,
        "requireFinal under: 'example.app', inSourceSets: ['mian']");

    // The shape a misspelt or forgotten 'under:' takes.
    private static final String A_RULE_WITH_NO_ROOT = GateProject.buildConfigurationBlock(
        EXTENSION,
        "requireFinal inSourceSets: ['main']");

    private static final String AN_EXCEPTION_OUTSIDE_THE_ROOT = GateProject.buildConfigurationBlock(
        EXTENSION,
        "requireFinal under: 'example.app', except: 'example.other'");

    private static final String AN_ALLOWANCE_WITH_NO_REASON = GateProject.buildConfigurationBlock(
        EXTENSION,
        "requireFinal under: 'example.app'",
        "allowExtendableClass type: 'example.app.OpenClass'");

    private static final String THE_APP_ROOT_ALLOWING_THE_OPEN_CLASS = GateProject.buildConfigurationBlock(
        EXTENSION,
        "requireFinal under: 'example.app'",
        "allowExtendableClass type: 'example.app.OpenClass', reason: 'its siblings extend it'");

    private static final String THE_APP_ROOT_ALLOWING_THE_FINAL_CLASS = GateProject.buildConfigurationBlock(
        EXTENSION,
        "requireFinal under: 'example.app'",
        "allowExtendableClass type: 'example.app.FinalClass', reason: 'its siblings extend it'");

    private static final String THE_APP_ROOT_ALLOWING_A_MISSING_CLASS = GateProject.buildConfigurationBlock(
        EXTENSION,
        "requireFinal under: 'example.app'",
        "allowExtendableClass type: 'example.app.Gone', reason: 'its siblings extend it'");

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
    private static final byte CONSTANT_CLASS_TAG = 7;

    // The open class the no-source case stages: Java 17's class-file version, and a pool of its own
    // name and its superclass's, each a class entry pointing at the text after it.
    private static final short JAVA_17_MAJOR_VERSION = 61;
    private static final short STAGED_POOL_ENTRY_COUNT = 5;
    private static final short THIS_CLASS_INDEX = 1;
    private static final short SUPER_CLASS_INDEX = 3;
    private static final short PUBLIC_SUPER_FLAGS = 0x0021;
    private static final short NONE = 0;

    // No class-file version has defined this tag.
    private static final byte UNDEFINED_CONSTANT_TAG = (byte) 254;

    @Nested
    class Declarations {

        @Test
        void failsWhenARuleNamesNoRoot(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "requireFinal needs 'under'";

            // ACT
            var actualResult = stageTheOpenClass(projectDir, A_RULE_WITH_NO_ROOT)
                .runExpectingFailure();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // One outside the root exempts nothing the rule reaches, while reading as an exemption.
        @Test
        void failsWhenAnExceptionSitsOutsideItsRoot(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "requireFinal 'except' names a package strictly beneath its root example.app";

            // ACT
            var actualResult = stageTheOpenClass(projectDir, AN_EXCEPTION_OUTSIDE_THE_ROOT)
                .runExpectingFailure();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // An allowance is a line a reviewer judges, and one with no reason gives them nothing to judge.
        @Test
        void failsWhenAnAllowanceGivesNoReason(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "allowExtendableClass needs 'type'";

            // ACT
            var actualResult = stageTheOpenClass(projectDir, AN_ALLOWANCE_WITH_NO_REASON)
                .runExpectingFailure();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // A misspelt source set matches no compiled tree, so its rule would check nothing.
        @Test
        void failsWhenARuleNamesASourceSetTheBuildDoesNotHave(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "requireFinal under 'example.app' names source set(s) [mian] this build does not have";

            // ACT
            var actualResult = stageTheOpenClass(projectDir, THE_APP_ROOT_IN_A_MISSPELT_SOURCE_SET)
                .runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        @Test
        void passesWhenNothingIsDeclared(@TempDir Path projectDir) {

            // ARRANGE
            var project = stageTheOpenClass(projectDir, NOTHING_DECLARED);

            // ACT
            var actualResult = project.runExpectingGateToPass();

            // ASSERT
            assertThat(actualResult.getOutput())
                .doesNotContain("is public and neither final nor abstract");
        }

        // Were the rules not a task input, the second run would be UP-TO-DATE.
        @Test
        void failsAgainAfterAPassingRunOnceARuleIsDeclared(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "example.app.OpenClass is public and neither final nor abstract";

            stageTheOpenClass(projectDir, NOTHING_DECLARED)
                .runExpectingGateToPass();

            // ACT
            var actualResult = stageTheOpenClass(projectDir, THE_APP_ROOT)
                .runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }
    }

    @Nested
    class Classes {

        // Named by the source file it was compiled from, so the finding opens where the keyword goes.
        @Test
        void failsOnAPublicClassThatIsNeitherFinalNorAbstract(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "src/main/java/example/app/OpenClass.java: example.app.OpenClass is public and " +
                "neither final nor abstract, so code in another JAR can extend it. Mark it final, or declare it " +
                "with allowExtendableClass and the reason it has to stay open";

            // ACT
            var actualResult = stageTheOpenClass(projectDir, THE_APP_ROOT)
                .runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // The reader is usually someone who left the keyword off by accident and sees no harm, nothing in
        // their project extending the class, so the findings open on why a gate is needed at all.
        @Test
        void headsTheFindingsWithTheGapTheGateCloses(@TempDir Path projectDir) {

            // ARRANGE
            var expectedHeading = "Classes open to extension (Java leaves a class open unless it is marked final, " +
                "and nothing but this gate reports one left open)";

            // ACT
            var actualResult = stageTheOpenClass(projectDir, THE_APP_ROOT)
                .runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedHeading);
        }

        // The generic sentence fits any project; the rule's reason is this project's own stake.
        @Test
        void carriesTheReasonTheRuleGivesIntoEachFinding(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "so code in another JAR can extend it; " + PUBLISHED_ROOT_REASON +
                ". Mark it final";

            // ACT
            var actualResult = stageTheOpenClass(projectDir, THE_APP_ROOT_WITH_ITS_REASON)
                .runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // A class whose source file cannot be placed is still named, by its class file, rather than by a
        // path that would not open. Staged into a tree no compile task owns, with no source behind it.
        @Test
        void namesAClassNoSourceFileHoldsByItsClassFile(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "example/app/Staged in java/staged is public and neither final nor abstract";
            var project = GateProject
                .driving(GATE, projectDir)
                .applyingTheJavaPlugin()
                .configuringTheGate(THE_APP_ROOT)
                .holdingBytes(
                    "build/classes/java/staged/example/app/Staged.class",
                    buildOpenClassFileNamed("example/app/Staged"));

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // An enum whose constant has a body is left unmarked final, and is still closed: its
        // constructor is private to it.
        @ParameterizedTest
        @CsvSource({
            "FinalClass, extension-closed-by-final",
            "AbstractClass, extension-closed-by-abstract",
            "Contract, extension-closed-as-interface",
            "Kind, extension-closed-as-enum-with-bodies",
            "Point, extension-closed-as-record",
            "PackageClass, extension-closed-by-package-access"})
        void passesOnAClassClosedToExtension(String className, String fixtureName, @TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .applyingTheJavaPlugin()
                .configuringTheGate(THE_APP_ROOT)
                .holdingFixture(
                    GateProject.MAIN_JAVA_ROOT + APP_ROOT_PATH + className + ".java",
                    "java/" + fixtureName);

            // ACT
            var actualResult = project.runExpectingGateToPass();

            // ASSERT
            assertThat(actualResult.getOutput())
                .doesNotContain("is public and neither final nor abstract");
        }

        // A nested class carries its own flags, so a final outer class closes nothing nested in it. It is
        // named by the outer class's file, where it is written, and in the spelling an allowance takes.
        @Test
        void failsOnAPublicNestedClassThatIsNeitherFinalNorAbstract(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "src/main/java/example/app/Outer.java: example.app.Outer$Inner is public and " +
                "neither final nor abstract";
            var unexpectedName = "Outer$Hidden";

            var project = GateProject
                .driving(GATE, projectDir)
                .applyingTheJavaPlugin()
                .configuringTheGate(THE_APP_ROOT)
                .holdingFixture(
                    GateProject.MAIN_JAVA_ROOT + APP_ROOT_PATH + "Outer.java",
                    "java/extension-open-nested-in-final-outer");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage)
                .doesNotContain(unexpectedName);
        }

        // The excepted subtree is where a project keeps what it publishes for others to extend.
        @Test
        void passesOnAnOpenClassInsideTheExceptedRoot(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .applyingTheJavaPlugin()
                .configuringTheGate(THE_APP_ROOT_LESS_ITS_PUBLISHED_ONE)
                .holdingFixture(
                    GateProject.MAIN_JAVA_ROOT + "/example/app/api/Published.java",
                    "java/extension-open-in-published-root");

            // ACT
            var actualResult = project.runExpectingGateToPass();

            // ASSERT
            assertThat(actualResult.getOutput())
                .doesNotContain("is public and neither final nor abstract");
        }

        @Test
        void passesOnAnOpenClassOutsideTheRoot(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .applyingTheJavaPlugin()
                .configuringTheGate(THE_APP_ROOT)
                .holdingFixture(
                    GateProject.MAIN_JAVA_ROOT + "/example/other/Outside.java",
                    "java/extension-open-outside-root");

            // ACT
            var actualResult = project.runExpectingGateToPass();

            // ASSERT
            assertThat(actualResult.getOutput())
                .doesNotContain("is public and neither final nor abstract");
        }

        @Test
        void reportsAClassTwoRulesHoldOnce(@TempDir Path projectDir) {

            // ARRANGE
            var expectedFindingCount = 1;

            // ACT
            var actualResult = stageTheOpenClass(projectDir, THE_APP_ROOT_TWICE)
                .runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput().split("example.app.OpenClass is public", -1))
                .hasSize(expectedFindingCount + 1);
        }
    }

    @Nested
    class SourceSets {

        // Test code may extend what it likes; the rule is about what ships.
        @Test
        void passesOnAnOpenTestClassWhenTheRuleHoldsMainOnly(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .applyingTheJavaPlugin()
                .configuringTheGate(THE_APP_ROOT_IN_MAIN)
                .holdingFixture(GateProject.TEST_JAVA_ROOT + OPEN_CLASS_PATH, OPEN_CLASS_FIXTURE);

            // ACT
            var actualResult = project.runExpectingGateToPass();

            // ASSERT
            assertThat(actualResult.getOutput())
                .doesNotContain("is public and neither final nor abstract");
        }

        @Test
        void failsOnAnOpenTestClassWhenTheRuleHoldsEveryTree(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "src/test/java/example/app/OpenClass.java: example.app.OpenClass is public and " +
                "neither final nor abstract";
            var project = GateProject
                .driving(GATE, projectDir)
                .applyingTheJavaPlugin()
                .configuringTheGate(THE_APP_ROOT)
                .holdingFixture(GateProject.TEST_JAVA_ROOT + OPEN_CLASS_PATH, OPEN_CLASS_FIXTURE);

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }
    }

    @Nested
    class Allowances {

        @Test
        void passesOnAnOpenClassItsAllowanceNames(@TempDir Path projectDir) {

            // ARRANGE
            var project = stageTheOpenClass(projectDir, THE_APP_ROOT_ALLOWING_THE_OPEN_CLASS);

            // ACT
            var actualResult = project.runExpectingGateToPass();

            // ASSERT
            assertThat(actualResult.getOutput())
                .doesNotContain("is public and neither final nor abstract");
        }

        // A class made final since leaves its allowance reading as a rule while exempting nothing.
        @Test
        void failsWhenTheAllowedClassIsFinal(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "example.app.FinalClass is allowed to be extended and is no public, non-final, " +
                "concrete class any rule holds here, so the allowance exempts nothing";

            var project = GateProject
                .driving(GATE, projectDir)
                .applyingTheJavaPlugin()
                .configuringTheGate(THE_APP_ROOT_ALLOWING_THE_FINAL_CLASS)
                .holdingFixture(GateProject.MAIN_JAVA_ROOT + FINAL_CLASS_PATH, FINAL_CLASS_FIXTURE);

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        @Test
        void failsWhenTheAllowedClassIsCompiledByNothing(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "example.app.Gone is allowed to be extended";

            // ACT
            var actualResult = stageTheOpenClass(projectDir, THE_APP_ROOT_ALLOWING_A_MISSING_CLASS)
                .runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // With nothing compiled in the rule's trees, every allowance would read as stale.
        @Test
        void passesWhenThereIsNothingCompiled(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .applyingTheJavaPlugin()
                .configuringTheGate(THE_APP_ROOT_ALLOWING_A_MISSING_CLASS);

            // ACT
            var actualResult = project.runExpectingGateToPass();

            // ASSERT
            assertThat(actualResult.getOutput())
                .doesNotContain("the allowance exempts nothing");
        }
    }

    @Nested
    class ClassFiles {

        // A tag from a version the walk does not know leaves the class checked for nothing.
        //
        // Staged into a tree no compile task owns, since Gradle clears a compile task's stale outputs.
        // The gate takes the build's class output whole, so it is scanned all the same.
        @Test
        void failsWhenAClassCarriesAConstantPoolTagItCannotFollow(@TempDir Path projectDir) {

            // ARRANGE
            var expectedHeading = "Classes this gate could not read";
            var expectedClassName = "example/app/Odd";

            var project = GateProject
                .driving(GATE, projectDir)
                .applyingTheJavaPlugin()
                .configuringTheGate(THE_APP_ROOT)
                .holdingBytes(
                    "build/classes/java/staged/example/app/Odd.class",
                    buildClassFileCarryingAnUnknownTag());

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedHeading)
                .contains(expectedClassName);
        }
    }

    // A header, a pool of two entries, one text constant, and a tag no class-file version has defined.
    private static byte[] buildClassFileCarryingAnUnknownTag() {

        var classFile = ByteBuffer.allocate(CLASS_FILE_CAPACITY);

        classFile.putInt(CLASS_FILE_MAGIC);
        classFile.putShort(CLASS_FILE_MINOR_VERSION);
        classFile.putShort(CLASS_FILE_MAJOR_VERSION);
        classFile.putShort(POOL_ENTRY_COUNT);
        classFile.put(CONSTANT_UTF8_TAG);

        var text = "example/app/Odd".getBytes(StandardCharsets.UTF_8);

        classFile.putShort((short) text.length);
        classFile.put(text);
        classFile.put(UNDEFINED_CONSTANT_TAG);

        return Arrays.copyOf(
            classFile.array(),
            classFile.position());
    }

    // A public, non-final class with nothing in it and no SourceFile attribute, as a compiler asked to
    // record no debug information writes one: header, a four-entry pool, flags, this and super, and
    // empty interface, field, method and attribute tables.
    private static byte[] buildOpenClassFileNamed(String binaryName) {

        var classFile = ByteBuffer.allocate(CLASS_FILE_CAPACITY + binaryName.length());

        classFile.putInt(CLASS_FILE_MAGIC);
        classFile.putShort(CLASS_FILE_MINOR_VERSION);
        classFile.putShort(JAVA_17_MAJOR_VERSION);
        classFile.putShort(STAGED_POOL_ENTRY_COUNT);

        putClassEntry(classFile, THIS_CLASS_INDEX, binaryName);
        putClassEntry(classFile, SUPER_CLASS_INDEX, "java/lang/Object");

        classFile.putShort(PUBLIC_SUPER_FLAGS);
        classFile.putShort(THIS_CLASS_INDEX);
        classFile.putShort(SUPER_CLASS_INDEX);
        classFile.putShort(NONE);
        classFile.putShort(NONE);
        classFile.putShort(NONE);
        classFile.putShort(NONE);

        return Arrays.copyOf(
            classFile.array(),
            classFile.position());
    }

    // A class entry at the given pool index, naming the text entry written straight after it, which
    // the pool numbers one higher.
    private static void putClassEntry(ByteBuffer classFile, short classIndex, String binaryName) {

        var text = binaryName.getBytes(StandardCharsets.UTF_8);

        classFile.put(CONSTANT_CLASS_TAG);
        classFile.putShort((short) (classIndex + 1));
        classFile.put(CONSTANT_UTF8_TAG);
        classFile.putShort((short) text.length);
        classFile.put(text);
    }

    // Stages the one open class most cases are posed against, under the root every rule names.
    private static GateProject stageTheOpenClass(Path projectDir, String declaration) {

        return GateProject
            .driving(GATE, projectDir)
            .applyingTheJavaPlugin()
            .configuringTheGate(declaration)
            .holdingFixture(GateProject.MAIN_JAVA_ROOT + OPEN_CLASS_PATH, OPEN_CLASS_FIXTURE);
    }
}
