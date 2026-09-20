package app.snatter.server.role;

import app.snatter.server.common.Id;
import app.snatter.server.persistence.Ids;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;
import java.util.UUID;

public record RoleId(UUID value) implements Id {

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public RoleId {
        Objects.requireNonNull(value, "value");
    }

    public static RoleId newId() {
        return new RoleId(Ids.newId());
    }

    /** Used by JAX-RS for path and query parameters. */
    public static RoleId fromString(String s) {
        return new RoleId(UUID.fromString(s));
    }

    @Override
    @JsonValue
    public UUID value() {
        return value;
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
