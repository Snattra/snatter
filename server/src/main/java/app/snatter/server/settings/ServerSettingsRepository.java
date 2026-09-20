package app.snatter.server.settings;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;

@ApplicationScoped
public class ServerSettingsRepository {

    /** Primary key of the single settings row, created by the V1 migration. */
    static final short SINGLETON_ID = 1;

    // pgjdbc cannot read timestamptz as Instant directly, but OffsetDateTime works.
    private static final RowMapper<ServerSettings> MAPPER = (rs, ctx) -> new ServerSettings(
        rs.getString("name"),
        rs.getString("description"),
        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
        rs.getObject("updated_at", OffsetDateTime.class).toInstant());

    private final Jdbi jdbi;

    public ServerSettingsRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public ServerSettings get() {
        return jdbi.withHandle(h -> h
            .createQuery("""
                SELECT name, description, created_at, updated_at
                FROM server_settings
                WHERE id = :id
                """)
            .bind("id", SINGLETON_ID)
            .map(MAPPER)
            .findOne()
            .orElseThrow(() -> new IllegalStateException(
                "server_settings row is missing; database migrations did not run")));
    }

    public void update(String name, String description) {
        int rows = jdbi.withHandle(h -> h
            .createUpdate("""
                UPDATE server_settings
                SET name = :name, description = :description, updated_at = :now
                WHERE id = :id
                """)
            .bind("name", name)
            .bind("description", description)
            .bind("now", Instant.now())
            .bind("id", SINGLETON_ID)
            .execute());
        if (rows != 1) {
            throw new IllegalStateException("expected to update 1 server_settings row, updated " + rows);
        }
    }
}
