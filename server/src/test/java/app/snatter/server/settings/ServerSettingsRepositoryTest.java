package app.snatter.server.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ServerSettingsRepositoryTest {

    @Inject
    ServerSettingsRepository repository;

    @AfterEach
    @Transactional
    void restoreDefaults() {
        repository.update("My Snatter server", null);
    }

    @Test
    void readsTheRowSeededByMigration() {
        ServerSettings s = repository.get();
        assertEquals("My Snatter server", s.name());
        assertNull(s.description());
        assertNotNull(s.createdAt());
        assertNotNull(s.updatedAt());
    }

    @Test
    @Transactional
    void updatesNameAndDescription() {
        ServerSettings before = repository.get();

        repository.update("Snattra HQ", "Where the ducks quack");

        ServerSettings after = repository.get();
        assertEquals("Snattra HQ", after.name());
        assertEquals("Where the ducks quack", after.description());
        assertTrue(!after.updatedAt().isBefore(before.updatedAt()));
    }
}
