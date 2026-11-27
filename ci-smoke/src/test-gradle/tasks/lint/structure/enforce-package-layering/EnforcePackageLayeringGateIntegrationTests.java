import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Drives the shared enforce-package-layering gate.
class EnforcePackageLayeringGateIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest(
            "enforcePackageLayering",
            "lint/structure/enforce-package-layering");

    private static final String EXTENSION = "enforcePackageLayering";

    // The framework root is closed to the feature root: what the fixtures are written against.
    private static final String CLOSED_PACKAGE_ROOT = "example.framework";
    private static final String FORBIDDEN_PACKAGE_ROOT = "example.feature";
    private static final String SECOND_FORBIDDEN_PACKAGE_ROOT = "example.otherfeature";
    private static final String KINDS_PACKAGE_ROOT = CLOSED_PACKAGE_ROOT + ".kinds";
    private static final String OUTSIDE_PACKAGE = FORBIDDEN_PACKAGE_ROOT + ".render";

    private static final String NO_EDGE_DECLARED = "";

    // An edge the framework sample does not cross, for a passing run that still reads every file.
    private static final String CLOSED_TO_THE_OTHER_FEATURE_ALONE =
        GateProject.buildConfigurationBlock(
            "enforcePackageLayering",
            "forbidImport under: '" +
                CLOSED_PACKAGE_ROOT +
                "', of: '" +
                SECOND_FORBIDDEN_PACKAGE_ROOT +
                "'");

    private static final String AN_EDGE_WITH_A_MISSPELT_KEY =
        GateProject.buildConfigurationBlock(
            "enforcePackageLayering",
            "forbidImport under: '" +
                CLOSED_PACKAGE_ROOT +
                "', excep: '" +
                KINDS_PACKAGE_ROOT +
                "', of: '" +
                FORBIDDEN_PACKAGE_ROOT +
                "'");

    private static final String A_LAYER_WITH_A_MISSPELT_KEY =
        GateProject.buildConfigurationBlock(
            "enforcePackageLayering",
            "forbidImport under: '" +
                KINDS_PACKAGE_ROOT +
                "', of: [[under: '" +
                CLOSED_PACKAGE_ROOT +
                "', exept: '" +
                KINDS_PACKAGE_ROOT +
                "']]");

    private static final String CLOSED_TO_THE_FEATURE =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "forbidImport under: '" +
                CLOSED_PACKAGE_ROOT +
                "', of: '" +
                FORBIDDEN_PACKAGE_ROOT +
                "'");

    private static final String CLOSED_TO_BOTH_FEATURES =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "forbidImport under: '" +
                CLOSED_PACKAGE_ROOT +
                "', of: '" +
                FORBIDDEN_PACKAGE_ROOT +
                "'",
            "forbidImport under: '" +
                CLOSED_PACKAGE_ROOT +
                "', of: '" +
                SECOND_FORBIDDEN_PACKAGE_ROOT +
                "'");

    // The closed package alone, its own subpackages left free to import what it may not.
    private static final String CLOSED_EXACTLY_TO_THE_FEATURE =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "forbidImport exactly: '" +
                CLOSED_PACKAGE_ROOT +
                "', of: '" +
                FORBIDDEN_PACKAGE_ROOT +
                "'");

    // The importing fixtures sit in the render subtree; one rule leaves it out, the other a sibling.
    private static final String CLOSED_EXCEPT_THE_RENDER_SUBTREE =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "forbidImport under: '" +
                CLOSED_PACKAGE_ROOT +
                "', except: '" +
                CLOSED_PACKAGE_ROOT +
                ".render', of: '" +
                FORBIDDEN_PACKAGE_ROOT +
                "'");

    private static final String CLOSED_EXCEPT_A_SIBLING_SUBTREE =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "forbidImport under: '" +
                CLOSED_PACKAGE_ROOT +
                "', except: ['" +
                CLOSED_PACKAGE_ROOT +
                ".paint'], of: '" +
                FORBIDDEN_PACKAGE_ROOT +
                "'");

    private static final String CLOSED_EXCEPT_A_PACKAGE_OUTSIDE_THE_ROOT =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "forbidImport under: '" +
                CLOSED_PACKAGE_ROOT +
                "', except: '" +
                OUTSIDE_PACKAGE +
                "', of: '" +
                FORBIDDEN_PACKAGE_ROOT +
                "'");

    private static final String CLOSED_EXCEPT_THE_ROOT_ITSELF =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "forbidImport under: '" +
                CLOSED_PACKAGE_ROOT +
                "', except: '" +
                CLOSED_PACKAGE_ROOT +
                "', of: '" +
                FORBIDDEN_PACKAGE_ROOT +
                "'");

    private static final String CLOSED_EXACTLY_EXCEPT_A_SUBTREE =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "forbidImport exactly: '" +
                CLOSED_PACKAGE_ROOT +
                "', except: '" +
                CLOSED_PACKAGE_ROOT +
                ".render', of: '" +
                FORBIDDEN_PACKAGE_ROOT +
                "'");

    private static final String CLOSED_AT_BOTH_WIDTHS =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "forbidImport under: '" +
                CLOSED_PACKAGE_ROOT +
                "', exactly: '" +
                CLOSED_PACKAGE_ROOT +
                "', of: '" +
                FORBIDDEN_PACKAGE_ROOT +
                "'");

    // The shape a misspelt or forgotten 'of:' takes.
    private static final String HALF_DECLARED_EDGE =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "forbidImport under: '" +
                CLOSED_PACKAGE_ROOT +
                "'");

    // The imported side as a layer: a subtree closed to the rest of the root it sits in.
    private static final String REST_OF_THE_FRAMEWORK =
        "[under: '" + CLOSED_PACKAGE_ROOT + "', except: '" + KINDS_PACKAGE_ROOT + "']";

    private static final String KINDS_CLOSED_TO_THE_REST_OF_THE_FRAMEWORK =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "forbidImport under: '" +
                KINDS_PACKAGE_ROOT +
                "', of: " +
                REST_OF_THE_FRAMEWORK);

    private static final String KINDS_CLOSED_TO_THE_FEATURE_AND_THE_REST_OF_THE_FRAMEWORK =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "forbidImport under: '" +
                KINDS_PACKAGE_ROOT +
                "', of: ['" +
                FORBIDDEN_PACKAGE_ROOT +
                "', " +
                REST_OF_THE_FRAMEWORK +
                "]");

    private static final String IMPORTED_LAYER_EXCEPT_A_PACKAGE_OUTSIDE_ITS_ROOT =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "forbidImport under: '" +
                KINDS_PACKAGE_ROOT +
                "', of: [under: '" +
                CLOSED_PACKAGE_ROOT +
                "', except: '" +
                OUTSIDE_PACKAGE +
                "']");

    private static final String IMPORTED_LAYER_WITHOUT_ITS_ROOT =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "forbidImport under: '" +
                KINDS_PACKAGE_ROOT +
                "', of: [except: '" +
                KINDS_PACKAGE_ROOT +
                "']");

    private static final String IMPORTED_LAYER_CLOSED_EXACTLY =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "forbidImport under: '" +
                KINDS_PACKAGE_ROOT +
                "', of: [under: '" +
                CLOSED_PACKAGE_ROOT +
                "', exactly: '" +
                CLOSED_PACKAGE_ROOT +
                "']");

    private static final String FEATURE_VIEW_REPORT =
        "Sample.java:3: example.framework may not import example.feature: example.feature.view.FeatureView";

    @Nested
    class Declarations {

        @Test
        void failsWhenAnEdgeIsDeclaredWithoutTheRootItCloses(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "forbidImport needs the package root that is closed";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(HALF_DECLARED_EDGE);

            // ACT
            var actualResult = project.runExpectingFailure();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        @Test
        void failsWhenAnEdgeClosesItsRootAtBothWidthsAtOnce(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "forbidImport closes a package root one way or the other";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_AT_BOTH_WIDTHS);

            // ACT
            var actualResult = project.runExpectingFailure();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        @Test
        void failsWhenTheLeftOutSubtreeSitsOutsideTheClosedRoot(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage =
                "forbidImport 'except' names a package strictly beneath its " +
                    "root example.framework; got: example.feature.render";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_EXCEPT_A_PACKAGE_OUTSIDE_THE_ROOT);

            // ACT
            var actualResult = project.runExpectingFailure();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // Leaving the root out of itself exempts everything the rule reaches.
        @Test
        void failsWhenTheLeftOutSubtreeIsTheClosedRootItself(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage =
                "forbidImport 'except' names a package strictly beneath " +
                    "its root example.framework; got: example.framework";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_EXCEPT_THE_ROOT_ITSELF);

            // ACT
            var actualResult = project.runExpectingFailure();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        @Test
        void failsWhenAPackageClosedExactlyLeavesASubtreeOut(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "forbidImport leaves subtrees out of an 'under' root only";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_EXACTLY_EXCEPT_A_SUBTREE);

            // ACT
            var actualResult = project.runExpectingFailure();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        @Test
        void failsWhenTheImportedLayerLeavesOutAPackageOutsideItsRoot(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage =
                "forbidImport 'except' names a package strictly beneath its " +
                    "root example.framework; got: example.feature.render";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(IMPORTED_LAYER_EXCEPT_A_PACKAGE_OUTSIDE_ITS_ROOT);

            // ACT
            var actualResult = project.runExpectingFailure();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        @Test
        void failsWhenTheImportedLayerNamesNoRoot(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage =
                "forbidImport 'of' names a package root, or a layer as [under: root, except: subtrees]";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(IMPORTED_LAYER_WITHOUT_ITS_ROOT);

            // ACT
            var actualResult = project.runExpectingFailure();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // The imported side is always a whole layer, so it has no narrow width.
        @Test
        void failsWhenTheImportedLayerIsClosedExactly(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage =
                "forbidImport 'of' names a package root, or a layer as [under: root, except: subtrees]";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(IMPORTED_LAYER_CLOSED_EXACTLY);

            // ACT
            var actualResult = project.runExpectingFailure();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // A misspelt key would otherwise be dropped in silence: 'excep' would close the subtree it meant to
        // leave out.
        @Test
        void failsWhenAnEdgeNamesAKeyItDoesNotTake(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage =
                "forbidImport does not take 'excep'; it takes 'under', 'exactly', 'except', 'of'";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(AN_EDGE_WITH_A_MISSPELT_KEY)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT,
                    "framework-importing-the-feature");

            // ACT
            var actualResult = project.runExpectingFailure();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        @Test
        void failsWhenAnImportedLayerNamesAKeyItDoesNotTake(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage = "forbidImport 'of' layer does not take 'exept'; it takes 'under', 'except'";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(A_LAYER_WITH_A_MISSPELT_KEY)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT,
                    "framework-importing-the-feature");

            // ACT
            var actualResult = project.runExpectingFailure();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // Every consumer inherits the gate, so one declaring nothing has it sit out.
        @Test
        void sitsOutWhenTheBuildDeclaresNoEdge(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(NO_EDGE_DECLARED)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT,
                    "framework-importing-the-feature");

            // ACT
            project.runExpectingGateToBeSkipped();
        }

        // Were the edges not a task input, the second run would be UP-TO-DATE.
        @Test
        void failsAgainAfterAPassingRunOnceAnEdgeTightens(@TempDir Path projectDir) {

            // ARRANGE
            GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_OTHER_FEATURE_ALONE)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT,
                    "framework-importing-the-feature")
                .runExpectingGateToPass();

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURE);

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(FEATURE_VIEW_REPORT);
        }
    }

    @Nested
    class Scope {

        // A suite reaching for a real type from the far side inverts the same arrow.
        @Test
        void failsWhenTestCodeImportsTheForbiddenRoot(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURE)
                .holdingJavaSample(
                    GateProject.TEST_JAVA_ROOT,
                    "framework-importing-the-feature");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(FEATURE_VIEW_REPORT);
        }

        @Test
        void failsWhenASourceSetBeyondMainAndTestImportsTheForbiddenRoot(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURE)
                .holdingJavaSample(
                    "src/utils/java",
                    "framework-importing-the-feature");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(FEATURE_VIEW_REPORT);
        }

        // Kotlin leaves the semicolon off its package declaration and may alias an import.
        @Test
        void failsWhenKotlinCodeImportsTheForbiddenRoot(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage =
                "Sample.kt:3: example.framework may not import example.feature: example.feature.view.FeatureView";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURE)
                .holdingKotlinSample(
                    GateProject.MAIN_KOTLIN_ROOT,
                    "framework-importing-the-feature");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        @Test
        void failsWhenThePackageDeclarationCarriesATrailingComment(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURE)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT,
                    "framework-declaring-its-package-beside-a-comment");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(FEATURE_VIEW_REPORT);
        }

        // 'example.frameworkish' is not under 'example.framework'.
        @Test
        void passesWhenTheImportingPackageOnlySharesAPrefix(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURE)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT,
                    "package-sharing-a-prefix");

            // ACT
            project.runExpectingGateToPass();
        }

        // 'example.featureless' is not under 'example.feature'.
        @Test
        void passesWhenTheImportedNameOnlySharesAPrefix(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURE)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT,
                    "import-sharing-a-prefix");

            // ACT
            project.runExpectingGateToPass();
        }

        // The default package sits under no root, so no edge names it.
        @Test
        void passesWhenTheImportingFileIsInTheDefaultPackage(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURE)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT,
                    "default-package-file");

            // ACT
            project.runExpectingGateToPass();
        }

        @Test
        void passesWhenThereIsNoSourceTree(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURE);

            // ACT
            project.runExpectingGateToPass();
        }
    }

    @Nested
    class Imports {

        @Test
        void failsWhenProductionCodeImportsTheForbiddenRoot(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURE)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT,
                    "framework-importing-the-feature");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(FEATURE_VIEW_REPORT);
        }

        @Test
        void failsWhenTheForbiddenRootArrivesByWildcardOrStaticImport(@TempDir Path projectDir) {

            // ARRANGE
            var expectedStaticImportMessage =
                "Sample.java:3: example.framework may not import example.feature: " +
                    "example.feature.view.FeatureView.DEFAULT_VIEW";

            var expectedWildcardImportMessage =
                "Sample.java:5: example.framework may not import example.feature: example.feature.view.*";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURE)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT,
                    "wildcard-and-static-imports");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedStaticImportMessage)
                .contains(expectedWildcardImportMessage);
        }

        // A gate that honoured only the first declaration would pass every single-edge case.
        @Test
        void failsOnAnEdgeBeyondTheFirstDeclared(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage =
                "Sample.java:3: example.framework may not import " +
                    "example.otherfeature: example.otherfeature.view.OtherView";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_BOTH_FEATURES)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT,
                    "framework-importing-the-second-feature");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        @Test
        void passesWhenNothingImportsTheForbiddenRoot(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURE)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT,
                    "framework-importing-nothing-forbidden");

            // ACT
            project.runExpectingGateToPass();
        }

        @Test
        void passesWhenAnImportIsOnlyInABlockComment(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURE)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT,
                    "framework-commenting-out-a-feature-import");

            // ACT
            project.runExpectingGateToPass();
        }

        @Test
        void passesWhenAnImportIsOnlySpeltInsideARawString(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURE)
                .holdingKotlinSample(
                    GateProject.MAIN_KOTLIN_ROOT,
                    "framework-spelling-a-feature-import-in-a-raw-string");

            // ACT
            project.runExpectingGateToPass();
        }
    }

    @Nested
    class Widths {

        @Test
        void failsWhenTheClosedPackageItselfImportsTheForbiddenRootAtTheNarrowWidth(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage =
                "Sample.java:3: example.framework itself may not import " +
                    "example.feature: example.feature.view.FeatureView";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_EXACTLY_TO_THE_FEATURE)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT,
                    "framework-root-importing-the-feature");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        // The root of a closed subtree is inside it; the widths differ only below it.
        @Test
        void failsWhenTheClosedPackageItselfImportsTheForbiddenRootAtTheWideWidth(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURE)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT,
                    "framework-root-importing-the-feature");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(FEATURE_VIEW_REPORT);
        }

        // The reason the narrow width exists: a package closed to one of its own subpackages
        // cannot be closed as a subtree without shutting that subpackage off from its siblings.
        @Test
        void passesWhenOnlyASubpackageOfTheNarrowlyClosedPackageImportsTheForbiddenRoot(
                @TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_EXACTLY_TO_THE_FEATURE)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT,
                    "framework-importing-the-feature");

            // ACT
            project.runExpectingGateToPass();
        }

        @Test
        void failsWhenASiblingOfTheLeftOutSubtreeImportsTheForbiddenRoot(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage =
                "Sample.java:3: example.framework except example.framework.paint may " +
                    "not import example.feature: example.feature.view.FeatureView";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_EXCEPT_A_SIBLING_SUBTREE)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT,
                    "framework-importing-the-feature");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage);
        }

        @Test
        void passesWhenOnlyTheLeftOutSubtreeImportsTheForbiddenRoot(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(CLOSED_EXCEPT_THE_RENDER_SUBTREE)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT,
                    "framework-importing-the-feature");

            // ACT
            project.runExpectingGateToPass();
        }
    }

    @Nested
    class ImportedLayers {

        // The parent's own top-level classes are what a list of sibling subtrees cannot name.
        @Test
        void failsWhenASubtreeImportsTheRestOfTheLayerItIsClosedTo(@TempDir Path projectDir) {

            // ARRANGE
            var expectedMessage =
                "Sample.java:4: example.framework.kinds may not import example.framework " +
                    "except example.framework.kinds: example.framework.FrameworkRoot";

            var unexpectedLine = "Sample.java:3:";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(KINDS_CLOSED_TO_THE_REST_OF_THE_FRAMEWORK)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT,
                    "kinds-importing-the-feature-and-the-framework");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedMessage)
                .doesNotContain(unexpectedLine);
        }

        @Test
        void failsOnEachEntryOfAListMixingARootAndALayer(@TempDir Path projectDir) {

            // ARRANGE
            var expectedRootMessage =
                "Sample.java:3: example.framework.kinds may not import " +
                    "example.feature: example.feature.view.FeatureView";

            var expectedLayerMessage =
                "Sample.java:4: example.framework.kinds may not import example.framework " +
                    "except example.framework.kinds: example.framework.FrameworkRoot";

            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(KINDS_CLOSED_TO_THE_FEATURE_AND_THE_REST_OF_THE_FRAMEWORK)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT,
                    "kinds-importing-the-feature-and-the-framework");

            // ACT
            var actualResult = project.runExpectingGateToFail();

            // ASSERT
            assertThat(actualResult.getOutput())
                .contains(expectedRootMessage)
                .contains(expectedLayerMessage);
        }

        // Closed to its whole parent, the subtree would be closed to its own static imports.
        @Test
        void passesWhenASubtreeImportsOnlyItselfFromTheLayerItIsClosedTo(@TempDir Path projectDir) {

            // ARRANGE
            var project = GateProject
                .driving(GATE, projectDir)
                .configuringTheGate(KINDS_CLOSED_TO_THE_REST_OF_THE_FRAMEWORK)
                .holdingJavaSample(
                    GateProject.MAIN_JAVA_ROOT,
                    "kinds-importing-its-own-kind");

            // ACT
            project.runExpectingGateToPass();
        }
    }
}
