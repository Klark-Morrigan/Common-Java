import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Drives the shared enforce-package-layering gate.
class EnforcePackageLayeringGateIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest("enforcePackageLayering", "lint/structure/enforce-package-layering");

    private static final String EXTENSION = "enforcePackageLayering";

    // The framework root is closed to the feature root: what the fixtures are written against.
    private static final String CLOSED_PACKAGE_ROOT = "example.framework";
    private static final String FORBIDDEN_PACKAGE_ROOT = "example.feature";
    private static final String SECOND_FORBIDDEN_PACKAGE_ROOT = "example.otherfeature";
    private static final String KINDS_PACKAGE_ROOT = CLOSED_PACKAGE_ROOT + ".kinds";
    private static final String OUTSIDE_PACKAGE = FORBIDDEN_PACKAGE_ROOT + ".render";

    private static final String NO_EDGE_DECLARED = "";

    private static final String CLOSED_TO_THE_FEATURE = GateProject.buildConfigurationBlock(
        EXTENSION,
        "forbidImport under: '" + CLOSED_PACKAGE_ROOT + "', of: '" + FORBIDDEN_PACKAGE_ROOT + "'");

    private static final String CLOSED_TO_BOTH_FEATURES = GateProject.buildConfigurationBlock(
        EXTENSION,
        "forbidImport under: '" + CLOSED_PACKAGE_ROOT + "', of: '" + FORBIDDEN_PACKAGE_ROOT + "'",
        "forbidImport under: '" + CLOSED_PACKAGE_ROOT + "', of: '" + SECOND_FORBIDDEN_PACKAGE_ROOT + "'");

    // The closed package alone, its own subpackages left free to import what it may not.
    private static final String CLOSED_EXACTLY_TO_THE_FEATURE = GateProject.buildConfigurationBlock(
        EXTENSION,
        "forbidImport exactly: '" + CLOSED_PACKAGE_ROOT + "', of: '" + FORBIDDEN_PACKAGE_ROOT + "'");

    // The importing fixtures sit in the render subtree; one rule leaves it out, the other a sibling.
    private static final String CLOSED_EXCEPT_THE_RENDER_SUBTREE = GateProject.buildConfigurationBlock(
        EXTENSION,
        "forbidImport under: '" + CLOSED_PACKAGE_ROOT + "', except: '" + CLOSED_PACKAGE_ROOT +
            ".render', of: '" + FORBIDDEN_PACKAGE_ROOT + "'");

    private static final String CLOSED_EXCEPT_A_SIBLING_SUBTREE = GateProject.buildConfigurationBlock(
        EXTENSION,
        "forbidImport under: '" + CLOSED_PACKAGE_ROOT + "', except: ['" + CLOSED_PACKAGE_ROOT +
            ".paint'], of: '" + FORBIDDEN_PACKAGE_ROOT + "'");

    private static final String CLOSED_EXCEPT_A_PACKAGE_OUTSIDE_THE_ROOT = GateProject.buildConfigurationBlock(
        EXTENSION,
        "forbidImport under: '" + CLOSED_PACKAGE_ROOT + "', except: '" + OUTSIDE_PACKAGE +
            "', of: '" + FORBIDDEN_PACKAGE_ROOT + "'");

    private static final String CLOSED_EXCEPT_THE_ROOT_ITSELF = GateProject.buildConfigurationBlock(
        EXTENSION,
        "forbidImport under: '" + CLOSED_PACKAGE_ROOT + "', except: '" + CLOSED_PACKAGE_ROOT +
            "', of: '" + FORBIDDEN_PACKAGE_ROOT + "'");

    private static final String CLOSED_EXACTLY_EXCEPT_A_SUBTREE = GateProject.buildConfigurationBlock(
        EXTENSION,
        "forbidImport exactly: '" + CLOSED_PACKAGE_ROOT + "', except: '" + CLOSED_PACKAGE_ROOT +
            ".render', of: '" + FORBIDDEN_PACKAGE_ROOT + "'");

    private static final String CLOSED_AT_BOTH_WIDTHS = GateProject.buildConfigurationBlock(
        EXTENSION,
        "forbidImport under: '" + CLOSED_PACKAGE_ROOT + "', exactly: '" + CLOSED_PACKAGE_ROOT +
            "', of: '" + FORBIDDEN_PACKAGE_ROOT + "'");

    // The shape a misspelt or forgotten 'of:' takes.
    private static final String HALF_DECLARED_EDGE = GateProject.buildConfigurationBlock(
        EXTENSION,
        "forbidImport under: '" + CLOSED_PACKAGE_ROOT + "'");

    // The imported side as a layer: a subtree closed to the rest of the root it sits in.
    private static final String REST_OF_THE_FRAMEWORK =
        "[under: '" + CLOSED_PACKAGE_ROOT + "', except: '" + KINDS_PACKAGE_ROOT + "']";

    private static final String KINDS_CLOSED_TO_THE_REST_OF_THE_FRAMEWORK = GateProject.buildConfigurationBlock(
        EXTENSION,
        "forbidImport under: '" + KINDS_PACKAGE_ROOT + "', of: " + REST_OF_THE_FRAMEWORK);

    private static final String KINDS_CLOSED_TO_THE_FEATURE_AND_THE_REST_OF_THE_FRAMEWORK =
        GateProject.buildConfigurationBlock(
            EXTENSION,
            "forbidImport under: '" + KINDS_PACKAGE_ROOT + "', of: ['" + FORBIDDEN_PACKAGE_ROOT + "', " +
                REST_OF_THE_FRAMEWORK + "]");

    private static final String IMPORTED_LAYER_EXCEPT_A_PACKAGE_OUTSIDE_ITS_ROOT = GateProject.buildConfigurationBlock(
        EXTENSION,
        "forbidImport under: '" + KINDS_PACKAGE_ROOT + "', of: [under: '" + CLOSED_PACKAGE_ROOT +
            "', except: '" + OUTSIDE_PACKAGE + "']");

    private static final String IMPORTED_LAYER_WITHOUT_ITS_ROOT = GateProject.buildConfigurationBlock(
        EXTENSION,
        "forbidImport under: '" + KINDS_PACKAGE_ROOT + "', of: [except: '" + KINDS_PACKAGE_ROOT + "']");

    private static final String IMPORTED_LAYER_CLOSED_EXACTLY = GateProject.buildConfigurationBlock(
        EXTENSION,
        "forbidImport under: '" + KINDS_PACKAGE_ROOT + "', of: [under: '" + CLOSED_PACKAGE_ROOT +
            "', exactly: '" + CLOSED_PACKAGE_ROOT + "']");

    private static final String FEATURE_VIEW_REPORT =
        "Sample.java:3: example.framework may not import example.feature: example.feature.view.FeatureView";

    @Nested
    class Declarations {

        @Test
        void failsWhenAnEdgeIsDeclaredWithoutTheRootItCloses(@TempDir Path projectDir) {

            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(HALF_DECLARED_EDGE)
                .runExpectingFailure();

            assertThat(result.getOutput())
                .contains("forbidImport needs the package root that is closed");
        }

        @Test
        void failsWhenAnEdgeClosesItsRootAtBothWidthsAtOnce(@TempDir Path projectDir) {

            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_AT_BOTH_WIDTHS)
                .runExpectingFailure();

            assertThat(result.getOutput())
                .contains("forbidImport closes a package root one way or the other");
        }

        @Test
        void failsWhenTheLeftOutSubtreeSitsOutsideTheClosedRoot(@TempDir Path projectDir) {

            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_EXCEPT_A_PACKAGE_OUTSIDE_THE_ROOT)
                .runExpectingFailure();

            assertThat(result.getOutput())
                .contains("forbidImport 'except' names a package strictly beneath its root " +
                    "example.framework; got: example.feature.render");
        }

        @Test
        void failsWhenTheLeftOutSubtreeIsTheClosedRootItself(@TempDir Path projectDir) {

            // Leaving the root out of itself exempts everything the rule reaches.
            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_EXCEPT_THE_ROOT_ITSELF)
                .runExpectingFailure();

            assertThat(result.getOutput())
                .contains("forbidImport 'except' names a package strictly beneath its root " +
                    "example.framework; got: example.framework");
        }

        @Test
        void failsWhenAPackageClosedExactlyLeavesASubtreeOut(@TempDir Path projectDir) {

            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_EXACTLY_EXCEPT_A_SUBTREE)
                .runExpectingFailure();

            assertThat(result.getOutput())
                .contains("forbidImport leaves subtrees out of an 'under' root only");
        }

        @Test
        void failsWhenTheImportedLayerLeavesOutAPackageOutsideItsRoot(@TempDir Path projectDir) {

            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(IMPORTED_LAYER_EXCEPT_A_PACKAGE_OUTSIDE_ITS_ROOT)
                .runExpectingFailure();

            assertThat(result.getOutput())
                .contains("forbidImport 'except' names a package strictly beneath its root " +
                    "example.framework; got: example.feature.render");
        }

        @Test
        void failsWhenTheImportedLayerNamesNoRoot(@TempDir Path projectDir) {

            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(IMPORTED_LAYER_WITHOUT_ITS_ROOT)
                .runExpectingFailure();

            assertThat(result.getOutput())
                .contains("forbidImport 'of' names a package root, or a layer as " +
                    "[under: root, except: subtrees]");
        }

        @Test
        void failsWhenTheImportedLayerIsClosedExactly(@TempDir Path projectDir) {

            // The imported side is always a whole layer, so it has no narrow width.
            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(IMPORTED_LAYER_CLOSED_EXACTLY)
                .runExpectingFailure();

            assertThat(result.getOutput())
                .contains("forbidImport 'of' names a package root, or a layer as " +
                    "[under: root, except: subtrees]");
        }

        @Test
        void passesWhenTheBuildDeclaresNoEdge(@TempDir Path projectDir) {

            GateProject.driving(GATE, projectDir)
                .configuringTheGate(NO_EDGE_DECLARED)
                .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "framework-importing-the-feature")
                .runExpectingGateToPass();
        }

        @Test
        void failsAgainAfterAPassingRunOnceAnEdgeIsDeclared(@TempDir Path projectDir) {

            // Were the edges not a task input, the second run would be UP-TO-DATE.
            GateProject.driving(GATE, projectDir)
                .configuringTheGate(NO_EDGE_DECLARED)
                .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "framework-importing-the-feature")
                .runExpectingGateToPass();

            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURE)
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains(FEATURE_VIEW_REPORT);
        }
    }

    @Nested
    class Scope {

        @Test
        void failsWhenTestCodeImportsTheForbiddenRoot(@TempDir Path projectDir) {

            // A suite reaching for a real type from the far side inverts the same arrow.
            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURE)
                .holdingJavaSample(GateProject.TEST_JAVA_ROOT, "framework-importing-the-feature")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains(FEATURE_VIEW_REPORT);
        }

        @Test
        void failsWhenASourceSetBeyondMainAndTestImportsTheForbiddenRoot(@TempDir Path projectDir) {

            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURE)
                .holdingJavaSample("src/utils/java", "framework-importing-the-feature")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains(FEATURE_VIEW_REPORT);
        }

        @Test
        void failsWhenKotlinCodeImportsTheForbiddenRoot(@TempDir Path projectDir) {

            // Kotlin leaves the semicolon off its package declaration and may alias an import.
            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURE)
                .holdingKotlinSample(GateProject.MAIN_KOTLIN_ROOT, "framework-importing-the-feature")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.kt:3: example.framework may not import example.feature: " +
                    "example.feature.view.FeatureView");
        }

        @Test
        void failsWhenThePackageDeclarationCarriesATrailingComment(@TempDir Path projectDir) {

            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURE)
                .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "framework-declaring-its-package-beside-a-comment")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains(FEATURE_VIEW_REPORT);
        }

        @Test
        void passesWhenTheImportingPackageOnlySharesAPrefix(@TempDir Path projectDir) {

            // 'example.frameworkish' is not under 'example.framework'.
            GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURE)
                .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "package-sharing-a-prefix")
                .runExpectingGateToPass();
        }

        @Test
        void passesWhenTheImportedNameOnlySharesAPrefix(@TempDir Path projectDir) {

            // 'example.featureless' is not under 'example.feature'.
            GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURE)
                .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "import-sharing-a-prefix")
                .runExpectingGateToPass();
        }

        @Test
        void passesWhenTheImportingFileIsInTheDefaultPackage(@TempDir Path projectDir) {

            // The default package sits under no root, so no edge names it.
            GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURE)
                .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "default-package-file")
                .runExpectingGateToPass();
        }

        @Test
        void passesWhenThereIsNoSourceTree(@TempDir Path projectDir) {

            GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURE)
                .runExpectingGateToPass();
        }
    }

    @Nested
    class Imports {

        @Test
        void failsWhenProductionCodeImportsTheForbiddenRoot(@TempDir Path projectDir) {

            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURE)
                .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "framework-importing-the-feature")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains(FEATURE_VIEW_REPORT);
        }

        @Test
        void failsWhenTheForbiddenRootArrivesByWildcardOrStaticImport(@TempDir Path projectDir) {

            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURE)
                .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "wildcard-and-static-imports")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:3: example.framework may not import example.feature: " +
                    "example.feature.view.FeatureView.DEFAULT_VIEW")
                .contains("Sample.java:5: example.framework may not import example.feature: " +
                    "example.feature.view.*");
        }

        @Test
        void failsOnAnEdgeBeyondTheFirstDeclared(@TempDir Path projectDir) {

            // A gate that honoured only the first declaration would pass every single-edge case.
            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_BOTH_FEATURES)
                .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "framework-importing-the-second-feature")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:3: example.framework may not import example.otherfeature: " +
                    "example.otherfeature.view.OtherView");
        }

        @Test
        void passesWhenNothingImportsTheForbiddenRoot(@TempDir Path projectDir) {

            GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURE)
                .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "framework-importing-nothing-forbidden")
                .runExpectingGateToPass();
        }

        @Test
        void passesWhenAnImportIsOnlyInABlockComment(@TempDir Path projectDir) {

            GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURE)
                .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "framework-commenting-out-a-feature-import")
                .runExpectingGateToPass();
        }

        @Test
        void passesWhenAnImportIsOnlySpeltInsideARawString(@TempDir Path projectDir) {

            GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURE)
                .holdingKotlinSample(
                    GateProject.MAIN_KOTLIN_ROOT,
                    "framework-spelling-a-feature-import-in-a-raw-string")
                .runExpectingGateToPass();
        }
    }

    @Nested
    class Widths {

        @Test
        void failsWhenTheClosedPackageItselfImportsTheForbiddenRootAtTheNarrowWidth(@TempDir Path projectDir) {

            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_EXACTLY_TO_THE_FEATURE)
                .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "framework-root-importing-the-feature")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:3: example.framework itself may not import example.feature: " +
                    "example.feature.view.FeatureView");
        }

        @Test
        void failsWhenTheClosedPackageItselfImportsTheForbiddenRootAtTheWideWidth(@TempDir Path projectDir) {

            // The root of a closed subtree is inside it; the widths differ only below it.
            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_TO_THE_FEATURE)
                .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "framework-root-importing-the-feature")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains(FEATURE_VIEW_REPORT);
        }

        @Test
        void passesWhenOnlyASubpackageOfTheNarrowlyClosedPackageImportsTheForbiddenRoot(
                @TempDir Path projectDir) {

            // The reason the narrow width exists: a package closed to one of its own subpackages
            // cannot be closed as a subtree without shutting that subpackage off from its siblings.
            GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_EXACTLY_TO_THE_FEATURE)
                .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "framework-importing-the-feature")
                .runExpectingGateToPass();
        }

        @Test
        void failsWhenASiblingOfTheLeftOutSubtreeImportsTheForbiddenRoot(@TempDir Path projectDir) {

            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_EXCEPT_A_SIBLING_SUBTREE)
                .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "framework-importing-the-feature")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:3: example.framework except example.framework.paint may not import " +
                    "example.feature: example.feature.view.FeatureView");
        }

        @Test
        void passesWhenOnlyTheLeftOutSubtreeImportsTheForbiddenRoot(@TempDir Path projectDir) {

            GateProject.driving(GATE, projectDir)
                .configuringTheGate(CLOSED_EXCEPT_THE_RENDER_SUBTREE)
                .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "framework-importing-the-feature")
                .runExpectingGateToPass();
        }
    }

    @Nested
    class ImportedLayers {

        @Test
        void failsWhenASubtreeImportsTheRestOfTheLayerItIsClosedTo(@TempDir Path projectDir) {

            // The parent's own top-level classes are what a list of sibling subtrees cannot name.
            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(KINDS_CLOSED_TO_THE_REST_OF_THE_FRAMEWORK)
                .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "kinds-importing-the-feature-and-the-framework")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:4: example.framework.kinds may not import example.framework except " +
                    "example.framework.kinds: example.framework.FrameworkRoot")
                .doesNotContain("Sample.java:3:");
        }

        @Test
        void failsOnEachEntryOfAListMixingARootAndALayer(@TempDir Path projectDir) {

            var result = GateProject.driving(GATE, projectDir)
                .configuringTheGate(KINDS_CLOSED_TO_THE_FEATURE_AND_THE_REST_OF_THE_FRAMEWORK)
                .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "kinds-importing-the-feature-and-the-framework")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:3: example.framework.kinds may not import example.feature: " +
                    "example.feature.view.FeatureView")
                .contains("Sample.java:4: example.framework.kinds may not import example.framework except " +
                    "example.framework.kinds: example.framework.FrameworkRoot");
        }

        @Test
        void passesWhenASubtreeImportsOnlyItselfFromTheLayerItIsClosedTo(@TempDir Path projectDir) {

            // Closed to its whole parent, the subtree would be closed to its own static imports.
            GateProject.driving(GATE, projectDir)
                .configuringTheGate(KINDS_CLOSED_TO_THE_REST_OF_THE_FRAMEWORK)
                .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "kinds-importing-its-own-kind")
                .runExpectingGateToPass();
        }
    }
}
