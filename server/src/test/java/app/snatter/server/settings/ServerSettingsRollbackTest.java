package app.snatter.server.settings;

import static app.snatter.server.testing.ApiAssertions.assertApiError;
import static app.snatter.server.testing.ApiAssertions.assertApiStatus;
import static app.snatter.server.testing.ApiClientFactory.serverApi;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.snatter.client.model.RegistrationModeDto;
import app.snatter.client.model.ServerInfoDto;
import app.snatter.client.model.ServerSettingsDto;
import app.snatter.client.model.ServerSettingsUpdateDto;
import app.snatter.server.account.AccountEvent;
import app.snatter.server.testing.TestDataService;
import app.snatter.server.testing.TestUsers;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ServerSettingsRollbackTest {

    @Singleton
    public static class FailingObserver {
        final AtomicBoolean registration = new AtomicBoolean();
        final AtomicBoolean settings = new AtomicBoolean();

        void onAccount(@Observes AccountEvent event) {
            if (event instanceof AccountEvent.Registered && registration.get()) {
                throw new IllegalStateException("registration failure");
            }
        }

        void onSettings(@Observes ServerSettingsService.Changed event) {
            if (settings.get()) {
                throw new IllegalStateException("settings failure");
            }
        }
    }

    @Inject
    FailingObserver observer;

    @Inject
    ServerSettingsService settings;

    @Inject
    ServerSettingsRepository repository;

    @Test
    void cacheIsNotPublishedUntilTheOuterTransactionCommits() {
        ServerSettings before = settings.current();
        ServerSettings updated = before.withChallengeMaxNumber(20000);
        assertThrows(IllegalStateException.class, () -> QuarkusTransaction.requiringNew().run(() -> {
            settings.update(null, updated);
            assertEquals(20000, repository.get().challengeMaxNumber());
            assertEquals(before, settings.current());
            throw new IllegalStateException("rollback after update");
        }));
        assertEquals(before, settings.current());
        assertEquals(before, repository.get());

        QuarkusTransaction.requiringNew().run(() -> {
            settings.update(null, updated);
            assertEquals(before, settings.current());
        });
        assertEquals(20000, settings.current().challengeMaxNumber());
        assertEquals(repository.get(), settings.current());
    }

    @Test
    void failedFirstRegistrationLeavesServerReadyForSetup() {
        observer.registration.set(true);
        try {
            assertApiStatus(500, () -> TestUsers.register("first"));
        } finally {
            observer.registration.set(false);
        }

        ServerInfoDto info = serverApi().getServerInfo();
        assertTrue(info.getRegistration().getSetupRequired());
        assertNull(info.getOwnerId());

        TestUsers.User owner = TestUsers.register("first");
        info = serverApi().getServerInfo();
        assertFalse(info.getRegistration().getSetupRequired());
        assertEquals(owner.id(), info.getOwnerId());
    }

    @Test
    void failedSettingsUpdateDoesNotPublishOrResetRateLimits() {
        TestDataService data = new TestDataService();
        TestUsers.User owner = data.setUpServer();
        data.updateSettings(new ServerSettingsUpdateDto()
            .rateLimits(TestDataService.rateLimits(true, 1, 3600, 5, 3600, 30, 60)));
        ServerSettingsDto before = serverApi(owner).getServerSettings();
        ServerInfoDto info = serverApi().getServerInfo();
        assertApiError(401, "invalid_credentials", () -> TestUsers.login(owner.username(), "wrong password"));

        observer.settings.set(true);
        try {
            assertApiStatus(500, () -> data.updateSettings(new ServerSettingsUpdateDto()
                .name("Never committed")
                .registrationMode(RegistrationModeDto.INVITE_ONLY)
                .rateLimits(TestDataService.rateLimits(false, 10, 60, 5, 3600, 30, 60))));
        } finally {
            observer.settings.set(false);
        }

        assertEquals(before, serverApi(owner).getServerSettings());
        ServerInfoDto after = serverApi().getServerInfo();
        assertEquals(info.getCommunity(), after.getCommunity());
        assertEquals(info.getRegistration(), after.getRegistration());
        assertApiStatus(429, () -> TestUsers.login(owner.username(), "wrong password"));

        data.updateSettings(new ServerSettingsUpdateDto().name("Committed"));
        assertEquals("Committed", serverApi().getServerInfo().getCommunity().getName());
        assertApiError(401, "invalid_credentials", () -> TestUsers.login(owner.username(), "wrong password"));
    }
}
