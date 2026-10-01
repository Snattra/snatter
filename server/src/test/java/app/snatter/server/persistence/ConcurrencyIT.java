package app.snatter.server.persistence;

import io.quarkus.test.junit.QuarkusIntegrationTest;

/** Runs {@link ConcurrencyTest} against the packaged application, and in native builds against the executable. */
@QuarkusIntegrationTest
class ConcurrencyIT extends ConcurrencyTest {
}
