package app.snatter.server.auth;

import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Runs {@link AuthResourceTest} against the packaged application, which under
 * {@code -Dnative} is the native executable. Catches native-only problems such
 * as missing reflection registration.
 */
@QuarkusIntegrationTest
class AuthResourceIT extends AuthResourceTest {
}
