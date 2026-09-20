package app.snatter.server.auth;

import app.snatter.server.common.Id;
import app.snatter.server.persistence.Ids;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(implementation = UUID.class, description = "Session identifier")
public record SessionId(UUID value) implements Id {

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public SessionId {
        Objects.requireNonNull(value, "value");
    }

    public static SessionId newId() {
        return new SessionId(Ids.newId());
    }

    /** Used by JAX-RS for path and query parameters. */
    public static SessionId fromString(String s) {
        return new SessionId(UUID.fromString(s));
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
