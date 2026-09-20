package app.snatter.server.persistence;

import app.snatter.server.common.Value;
import java.lang.reflect.Type;
import java.sql.Types;
import java.util.Optional;
import org.jdbi.v3.core.argument.Argument;
import org.jdbi.v3.core.argument.ArgumentFactory;
import org.jdbi.v3.core.config.ConfigRegistry;

/** Lets repositories bind any {@link Value} directly: {@code .bind("id", accountId)}. */
public final class ValueArgumentFactory implements ArgumentFactory {

    @Override
    public Optional<Argument> build(Type type, Object value, ConfigRegistry config) {
        if (value instanceof Value<?> v) {
            return Optional.of((position, statement, ctx) -> statement.setObject(position, v.value()));
        }
        if (value == null && type instanceof Class<?> cls && Value.class.isAssignableFrom(cls)) {
            return Optional.of((position, statement, ctx) -> statement.setNull(position, Types.OTHER));
        }
        return Optional.empty();
    }
}
