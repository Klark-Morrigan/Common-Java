import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// Drives the shared enforce-methods-ordered-by-visibility gate, which scans src/main rather than
// src/test, so its samples are written there.
//
// The ladder rule and its language-specific default-visibility split (a bare method is
// package-private in Java, public in Kotlin, and public inside a Java interface) are covered in both
// languages, along with the constructor, enum constant, local-function, and property-initializer
// exclusions.
class EnforceMethodsOrderedByVisibilityGateIntegrationTests {

    private static final GateUnderTest GATE =
        new GateUnderTest("enforceMethodsOrderedByVisibility", "lint/structure/enforce-methods-ordered-by-visibility");

    @Nested
    class JavaLadder {

        @Test
        void passesWhenMethodsDescendTheLadderInJava(@TempDir Path projectDir) {

            GateProject
                .driving(GATE, projectDir)
                .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "ordered-ladder-passes")
                .runExpectingGateToPass();
        }

        @Test
        void failsWhenPublicSitsBelowPrivateInJava(@TempDir Path projectDir) {

            var result = GateProject
                .driving(GATE, projectDir)
                .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "public-below-private-fails")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:8: public method declared below a private method (line 5)");
        }

        // Protected and package-private are the two middle rungs, ordered only against each other.
        @Test
        void failsWhenProtectedSitsBelowPackagePrivateInJava(@TempDir Path projectDir) {

            var result = GateProject
                .driving(GATE, projectDir)
                .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "protected-below-package-private-fails")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:5: protected method declared below a package-private (implicit) method");
        }

        // An annotation with an argument list ahead of the modifiers would otherwise put its own '('
        // first and hide the 'private' behind it, ranking the method package-private.
        @Test
        void failsWhenAnnotatedPublicSitsBelowAnnotatedPrivateInJava(@TempDir Path projectDir) {

            var result = GateProject
                .driving(GATE, projectDir)
                .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "annotated-public-below-annotated-private-fails")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:8: public method declared below a private method (line 5)");
        }

        // A no-modifier method is package-private in a class but public in an interface. This
        // fixture only fails if the gate ranks the bare interface method as public.
        @Test
        void failsWhenInterfaceImplicitPublicSitsBelowPrivateInJava(@TempDir Path projectDir) {

            var result = GateProject
                .driving(GATE, projectDir)
                .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "interface-implicit-public-below-private-fails")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.java:5: public (implicit) method declared below a private method");
        }

        // A public constructor below a private method, and an enum constant with an argument list,
        // both look like methods out of order.
        @Test
        void passesWhenConstructorsAndEnumConstantsSitBelowPrivateInJava(@TempDir Path projectDir) {

            GateProject
                .driving(GATE, projectDir)
                .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "constructors-and-enum-constants-passes")
                .runExpectingGateToPass();
        }

        // A field whose value wraps onto the next line puts a call shaped like a bare
        // package-private method on that line, above the public methods.
        @Test
        void passesWhenFieldInitializersWrapInJava(@TempDir Path projectDir) {

            GateProject
                .driving(GATE, projectDir)
                .holdingJavaSample(GateProject.MAIN_JAVA_ROOT, "wrapped-field-initializers-passes")
                .runExpectingGateToPass();
        }
    }

    @Nested
    class KotlinLadder {

        @Test
        void passesWhenMethodsDescendTheLadderInKotlin(@TempDir Path projectDir) {

            GateProject
                .driving(GATE, projectDir)
                .holdingKotlinSample(GateProject.MAIN_KOTLIN_ROOT, "ordered-ladder-passes")
                .runExpectingGateToPass();
        }

        // A bare Kotlin 'fun' is public, so it must not appear below a private one.
        @Test
        void failsWhenPublicSitsBelowPrivateInKotlin(@TempDir Path projectDir) {

            var result = GateProject
                .driving(GATE, projectDir)
                .holdingKotlinSample(GateProject.MAIN_KOTLIN_ROOT, "public-below-private-fails")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.kt:8: public (implicit) method declared below a private method (line 5)");
        }

        // Kotlin ranks internal above protected, the reverse of where Java's middle rungs sit.
        @Test
        void failsWhenInternalSitsBelowProtectedInKotlin(@TempDir Path projectDir) {

            var result = GateProject
                .driving(GATE, projectDir)
                .holdingKotlinSample(GateProject.MAIN_KOTLIN_ROOT, "internal-below-protected-fails")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.kt:8: internal method declared below a protected method (line 5)");
        }

        @Test
        void failsWhenPublicSitsBelowPrivateInAnEnumClassInKotlin(@TempDir Path projectDir) {

            var result = GateProject
                .driving(GATE, projectDir)
                .holdingKotlinSample(GateProject.MAIN_KOTLIN_ROOT, "enum-class-public-below-private-fails")
                .runExpectingGateToFail();

            assertThat(result.getOutput())
                .contains("Sample.kt:8: public (implicit) method declared below a private method (line 5)");
        }

        // A 'fun' nested in a method body sits deeper than the class body, and a 'fun' after '=' is
        // a function-expression property value: neither is a member.
        @Test
        void passesWhenLocalFunctionsAndPropertyInitializersFollowPrivateInKotlin(@TempDir Path projectDir) {

            GateProject
                .driving(GATE, projectDir)
                .holdingKotlinSample(GateProject.MAIN_KOTLIN_ROOT, "local-functions-and-property-initializers-passes")
                .runExpectingGateToPass();
        }

        @Test
        void passesWhenBodilessDeclarationsPrecedeATopLevelFunctionInKotlin(@TempDir Path projectDir) {

            GateProject
                .driving(GATE, projectDir)
                .holdingKotlinSample(
                    GateProject.MAIN_KOTLIN_ROOT, "bodiless-declarations-before-top-level-function-passes")
                .runExpectingGateToPass();
        }
    }

    @Nested
    class ScannedTrees {

        // Test classes order setup methods and helpers by role, so the ladder is not held there.
        @Test
        void passesWhenTheTestTreeIsOutOfOrder(@TempDir Path projectDir) {

            GateProject
                .driving(GATE, projectDir)
                .holdingJavaSample(GateProject.TEST_JAVA_ROOT, "public-below-private-fails")
                .runExpectingGateToPass();
        }

        @Test
        void passesWhenThereIsNoMainTree(@TempDir Path projectDir) {

            GateProject
                .driving(GATE, projectDir)
                .runExpectingGateToPass();
        }
    }
}
