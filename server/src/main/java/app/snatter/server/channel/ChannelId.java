package app.snatter.server.channel;

import app.snatter.server.common.Id;
import app.snatter.server.persistence.Ids;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;
import java.util.UUID;

public record ChannelId(UUID value) implements Id {

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public ChannelId {
        Objects.requireNonNull(value, "value");
    }

    public static ChannelId newId() {
        return new ChannelId(Ids.newId());
    }

    /** Used by JAX-RS for path and query parameters. */
    public static ChannelId fromString(String s) {
        return new ChannelId(UUID.fromString(s));
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
