package app.snatter.server.persistence;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

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

    /** Reads a {@code uuid[]} column into typed ids; an SQL NULL yields an empty list. */
    public static <T> List<T> ids(ResultSet rs, String column, Function<UUID, T> constructor) throws SQLException {
        Array array = rs.getArray(column);
        if (array == null) {
            return List.of();
        }
        UUID[] values = (UUID[]) array.getArray();
        List<T> result = new ArrayList<>(values.length);
        for (UUID value : values) {
            result.add(constructor.apply(value));
        }
        return result;
    }

    /** Reads a nullable UUID column into a typed id, or null. */
    public static <T> T id(ResultSet rs, String column, Function<UUID, T> constructor) throws SQLException {
        UUID value = uuid(rs, column);
        return value == null ? null : constructor.apply(value);
    }
}
