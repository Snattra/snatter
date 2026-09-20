package app.snatter.server.blob;

import app.snatter.server.common.Id;
import app.snatter.server.persistence.Ids;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(implementation = UUID.class, description = "Blob identifier")
public record BlobId(UUID value) implements Id {

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public BlobId {
        Objects.requireNonNull(value, "value");
    }

    public static BlobId newId() {
        return new BlobId(Ids.newId());
    }

    /** Used by JAX-RS for path and query parameters. */
    public static BlobId fromString(String s) {
        return new BlobId(UUID.fromString(s));
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
