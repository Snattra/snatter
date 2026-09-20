package app.snatter.server.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Small helpers for reading PostgreSQL column types that JDBC handles awkwardly. */
public final class Rows {

    private Rows() {
    }

    /** Reads a TIMESTAMPTZ column. pgjdbc cannot convert it to Instant directly. */
    public static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    public static UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }
}
