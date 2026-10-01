package app.snatter.server.persistence;

import app.snatter.server.common.Value;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The column encodings the schema uses where SQLite lacks a type: instants
 * as microseconds since the epoch, UUIDs as text, and lists of ids as JSON
 * arrays. Binding instants and UUIDs is automatic (see {@link JdbiProducer});
 * reading goes through these helpers.
 */
public final class Rows {

    private static final long MICROS_PER_SECOND = 1_000_000;

    private Rows() {
    }

    /** Microseconds since the epoch, the precision instants are stored at. */
    public static long micros(Instant instant) {
        return Math.addExact(Math.multiplyExact(instant.getEpochSecond(), MICROS_PER_SECOND), instant.getNano() / 1000);
    }

    /** Reads an instant column; SQL NULL yields null. */
    public static Instant instant(ResultSet rs, String column) throws SQLException {
        long micros = rs.getLong(column);
        if (rs.wasNull()) {
            return null;
        }
        return Instant.ofEpochSecond(Math.floorDiv(micros, MICROS_PER_SECOND), Math.floorMod(micros, MICROS_PER_SECOND) * 1000);
    }

    /** Reads a nullable integer column. The driver's {@code getObject(column, Integer.class)} fails on NULL. */
    public static Integer integer(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    public static UUID uuid(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : UUID.fromString(value);
    }

    /** Reads a nullable UUID column into a typed id, or null. */
    public static <T> T id(ResultSet rs, String column, Function<UUID, T> constructor) throws SQLException {
        UUID value = uuid(rs, column);
        return value == null ? null : constructor.apply(value);
    }

    /**
     * Reads a JSON array of ids, as stored or as built by
     * {@code json_group_array}, into typed ids; SQL NULL yields an empty list.
     */
    public static <T> List<T> ids(ResultSet rs, String column, Function<UUID, T> constructor) throws SQLException {
        String json = rs.getString(column);
        if (json == null) {
            return List.of();
        }
        String body = json.strip();
        body = body.substring(1, body.length() - 1).strip();
        if (body.isEmpty()) {
            return List.of();
        }
        // Ids are hex digits and hyphens, so the array needs no real JSON parser.
        String[] items = body.split(",");
        List<T> result = new ArrayList<>(items.length);
        for (String item : items) {
            String quoted = item.strip();
            result.add(constructor.apply(UUID.fromString(quoted.substring(1, quoted.length() - 1))));
        }
        return result;
    }

    /** The JSON array to store for a list of ids. */
    public static String json(Collection<? extends Value<UUID>> ids) {
        return ids.stream().map(id -> "\"" + id.value() + "\"").collect(Collectors.joining(",", "[", "]"));
    }
}
