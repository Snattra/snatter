package app.snatter.server.auth;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.snatter.server.persistence.Rows;
import app.snatter.server.testing.TestUsers;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SessionExpiryTest {

    @Inject
    Jdbi jdbi;

    @Inject
    AuthConfig config;

    @Test
    void usingASessionMovesItsExpiry() {
        TestUsers.User member = TestUsers.register();
        String hash = AuthService.hashToken(member.token());
        // As if last used an hour ago and about to expire.
        jdbi.useHandle(h -> h.createUpdate("""
                UPDATE session SET last_seen_at = now() - interval '1 hour', expires_at = now() + interval '1 minute'
                WHERE token_hash = :hash
                """).bind("hash", hash).execute());

        given().header("Authorization", "Bearer " + member.token()).get("/api/v1/accounts/me").then().statusCode(200);

        Instant expiresAt = jdbi.withHandle(h -> h.createQuery("SELECT expires_at FROM session WHERE token_hash = :hash")
            .bind("hash", hash).map((rs, ctx) -> Rows.instant(rs, "expires_at")).one());
        assertTrue(expiresAt.isAfter(Instant.now().plus(config.sessionLifetime()).minus(Duration.ofMinutes(1))),
            "expiry moved a full lifetime ahead: " + expiresAt);
    }

    @Test
    void expiredSessionsAreRejected() {
        TestUsers.User member = TestUsers.register();
        jdbi.useHandle(h -> h.createUpdate("UPDATE session SET expires_at = now() - interval '1 second' WHERE token_hash = :hash")
            .bind("hash", AuthService.hashToken(member.token())).execute());
        given().header("Authorization", "Bearer " + member.token()).get("/api/v1/accounts/me").then().statusCode(401);
    }
}
