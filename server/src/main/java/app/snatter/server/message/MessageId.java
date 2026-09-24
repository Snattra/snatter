package app.snatter.server.message;

import app.snatter.server.common.Id;
import app.snatter.server.persistence.Ids;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;
import java.util.UUID;

public record MessageId(UUID value) implements Id {

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public MessageId {
        Objects.requireNonNull(value, "value");
    }

    public static MessageId newId() {
        return new MessageId(Ids.newId());
    }

    /** Used by JAX-RS for path and query parameters. */
    public static MessageId fromString(String s) {
        return new MessageId(UUID.fromString(s));
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
