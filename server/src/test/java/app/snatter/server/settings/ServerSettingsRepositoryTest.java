package app.snatter.server.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Duration;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ServerSettingsRepositoryTest {

    @Inject
    ServerSettingsRepository repository;

    @Test
    void readsTheRowSeededByMigration() {
        ServerSettings s = repository.get();
        assertNotNull(s.name());
        assertNotNull(s.registrationMode());
        assertNotNull(s.rateLimits().login());
        assertNotNull(s.createdAt());
        assertNotNull(s.updatedAt());
    }

    @Test
    @Transactional
    void updatesEveryEditableField() {
        ServerSettings before = repository.get();
        try {
            ServerSettings changed = before
                .withName("Snattra HQ")
                .withDescription("Where the ducks quack")
                .withRegistrationMode(RegistrationMode.INVITE_ONLY)
                .withChallengeRequired(false)
                .withPublicUrl("https://example.test")
                .withRateLimits(new RateLimits(false,
                    new RateLimitPolicy(1, Duration.ofSeconds(2)),
                    new RateLimitPolicy(3, Duration.ofSeconds(4)),
                    new RateLimitPolicy(5, Duration.ofSeconds(6)),
                    new RateLimitPolicy(7, Duration.ofSeconds(8)),
                    new RateLimitPolicy(9, Duration.ofSeconds(10))));
            repository.update(changed);

            ServerSettings after = repository.get();
            assertEquals("Snattra HQ", after.name());
            assertEquals("Where the ducks quack", after.description());
            assertEquals(RegistrationMode.INVITE_ONLY, after.registrationMode());
            assertEquals(false, after.challengeRequired());
            assertEquals("https://example.test", after.publicUrl());
            assertEquals(changed.rateLimits(), after.rateLimits());
            assertEquals(before.ownerId(), after.ownerId(), "update must not touch the owner");
            assertTrue(!after.updatedAt().isBefore(before.updatedAt()));
        } finally {
            repository.update(before);
        }
    }
}
