package app.snatter.server.persistence;

import app.snatter.server.common.Value;
import java.lang.reflect.Type;
import java.sql.Types;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.jdbi.v3.core.argument.Argument;
import org.jdbi.v3.core.argument.ArgumentFactory;
import org.jdbi.v3.core.config.ConfigRegistry;
import org.jdbi.v3.core.generic.GenericTypes;

/**
 * Binds the types the schema stores in its own encoding (see {@link Rows}):
 * any {@link Value} as its bare value, so repositories can write
 * {@code .bind("id", accountId)}; UUIDs as text; instants as microseconds.
 *
 * <p>Preparable, because JDBI consults preparable factories first and its
 * own one would otherwise bind instants as timestamps.
 */
public final class ValueArgumentFactory implements ArgumentFactory.Preparable {

    @Override
    public Optional<Function<Object, Argument>> prepare(Type type, ConfigRegistry config) {
        Class<?> cls = GenericTypes.getErasedType(type);
        if (!Value.class.isAssignableFrom(cls) && cls != UUID.class && cls != Instant.class) {
            return Optional.empty();
        }
        return Optional.of(value -> {
            Object stored = encode(value);
            return stored == null
                ? (position, statement, ctx) -> statement.setNull(position, Types.NULL)
                : (position, statement, ctx) -> statement.setObject(position, stored);
        });
    }

    private static Object encode(Object value) {
        return switch (value) {
            case Value<?> v -> encode(v.value());
            case UUID u -> u.toString();
            case Instant i -> Rows.micros(i);
            case null -> null;
            default -> value;
        };
    }
}
