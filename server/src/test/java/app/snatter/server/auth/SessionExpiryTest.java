package app.snatter.server.auth;

import static app.snatter.server.testing.ApiAssertions.assertApiStatus;
import static app.snatter.server.testing.ApiClientFactory.accountsApi;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.snatter.client.model.ServerSettingsUpdateDto;
import app.snatter.server.persistence.Rows;
import app.snatter.server.settings.ServerSettingsService;
import app.snatter.server.testing.TestDataService;
import app.snatter.server.testing.TestUsers;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import org.jdbi.v3.core.HandleConsumer;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SessionExpiryTest {

    private final TestDataService data = new TestDataService();

    @BeforeEach
    void setUpServer() {
        data.setUpServer();
    }

    @Inject
    Jdbi jdbi;

    @Inject
    ServerSettingsService settings;

    @Test
    void usingASessionMovesItsExpiry() {
        TestUsers.User member = TestUsers.register();
        String hash = AuthService.hashToken(member.token());
        // As if last used an hour ago and about to expire.
        write(h -> h.createUpdate("UPDATE session SET last_seen_at = :lastSeen, expires_at = :expires WHERE token_hash = :hash")
            .bind("lastSeen", Instant.now().minus(Duration.ofHours(1)))
            .bind("expires", Instant.now().plus(Duration.ofMinutes(1)))
            .bind("hash", hash).execute());

        accountsApi(member).getCurrentAccount();

        Instant expiresAt = expiresAt(hash);
        assertTrue(expiresAt.isAfter(Instant.now().plus(settings.current().sessionLifetime()).minus(Duration.ofMinutes(1))),
            "expiry moved a full lifetime ahead: " + expiresAt);
    }

    @Test
    void theLifetimeIsAServerSetting() {
        TestUsers.User member = TestUsers.register();
        data.updateSettings(new ServerSettingsUpdateDto().sessionLifetimeDays(2));
        String hash = AuthService.hashToken(member.token());
        // Last used an hour ago, so this use moves the expiry.
        write(h -> h.createUpdate("UPDATE session SET last_seen_at = :lastSeen WHERE token_hash = :hash")
            .bind("lastSeen", Instant.now().minus(Duration.ofHours(1)))
            .bind("hash", hash).execute());
        accountsApi(member).getCurrentAccount();
        Instant expiresAt = expiresAt(hash);
        Instant twoDays = Instant.now().plus(Duration.ofDays(2));
        assertTrue(expiresAt.isAfter(twoDays.minus(Duration.ofMinutes(1))) && expiresAt.isBefore(twoDays),
            "expiry moved two days ahead: " + expiresAt);
    }

    /** Writes need a transaction; reads outside one go to the read-only connections. */
    private void write(HandleConsumer<RuntimeException> update) {
        QuarkusTransaction.requiringNew().run(() -> jdbi.useHandle(update));
    }

    private Instant expiresAt(String tokenHash) {
        return jdbi.withHandle(h -> h.createQuery("SELECT expires_at FROM session WHERE token_hash = :hash")
            .bind("hash", tokenHash).map((rs, ctx) -> Rows.instant(rs, "expires_at")).one());
    }

    @Test
    void expiredSessionsAreRejected() {
        TestUsers.User member = TestUsers.register();
        write(h -> h.createUpdate("UPDATE session SET expires_at = :expires WHERE token_hash = :hash")
            .bind("expires", Instant.now().minus(Duration.ofSeconds(1)))
            .bind("hash", AuthService.hashToken(member.token())).execute());
        assertApiStatus(401, () -> accountsApi(member).getCurrentAccount());
    }
}
