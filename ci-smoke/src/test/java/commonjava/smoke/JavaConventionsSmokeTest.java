package commonjava.smoke;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Smoke test that exists only to exercise java-conventions.gradle end to
 * end in CI. It imports from the JUnit 5 and AssertJ dependencies the
 * shared conventions are supposed to put on the test classpath, so if those
 * conventions stop wiring up the test stack (or change the Java target),
 * this fails to compile or run - turning a convention regression into a red
 * build in Common-Java's own CI instead of surfacing first downstream.
 */
class JavaConventionsSmokeTest {

    @Test
    void appliesTheSharedTestStack() {
        assertThat(1 + 1).isEqualTo(2);
    }
}
