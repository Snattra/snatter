package app.snatter.server.testing;

import io.quarkus.arc.Arc;
import io.quarkus.test.junit.callback.QuarkusTestBeforeEachCallback;
import io.quarkus.test.junit.callback.QuarkusTestMethodContext;

/**
 * Starts every {@code @QuarkusTest} on a fresh server, so no test sees what
 * another left behind. It runs before the test class's own
 * {@code @BeforeEach} methods, which set up what its tests share with
 * {@link TestDataService}.
 *
 * <p>Registered in {@code META-INF/services}, so it applies to every test
 * class without being named.
 */
public class ResetDatabaseBeforeEach implements QuarkusTestBeforeEachCallback {

    @Override
    public void beforeEach(QuarkusTestMethodContext context) {
        Arc.requireContainer().instance(DatabaseReset.class).get().reset();
    }
}
