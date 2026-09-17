package commonjava.smoke;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;

import static org.assertj.core.api.Assertions.assertThat;

// TEMPORARY - delete after checking the end-of-run failure report.
class ScratchFailureShapeTest {

    @Nested
    class FailureShapes {

        @Test
        void failsWithACauseChain() {

            throw new InternalError(
                new InvocationTargetException(
                    new Error("Probable fatal error: No fonts found.")));
        }

        @Test
        void failsWithAMultiLineAssertion() {

            assertThat(1 + 1)
                .isEqualTo(3);
        }
    }
}
