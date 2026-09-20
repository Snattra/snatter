package app.snatter.server.account;

import app.snatter.server.common.Id;
import app.snatter.server.persistence.Ids;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(implementation = UUID.class, description = "Account identifier")
public record AccountId(UUID value) implements Id {

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public AccountId {
        Objects.requireNonNull(value, "value");
    }

    public static AccountId newId() {
        return new AccountId(Ids.newId());
    }

    /** Used by JAX-RS for path and query parameters. */
    public static AccountId fromString(String s) {
        return new AccountId(UUID.fromString(s));
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
