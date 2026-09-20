package app.snatter.server.settings;

import java.time.Instant;

/**
 * Community-wide settings. Exactly one row exists in {@code server_settings}.
 */
public record ServerSettings(
        String name,
        String description,
        Instant createdAt,
        Instant updatedAt) {
}
