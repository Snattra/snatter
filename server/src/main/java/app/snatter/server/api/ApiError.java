package app.snatter.server.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.Map;

/**
 * Error body returned by every failing API call.
 *
 * @param error   stable, machine-readable code such as {@code username_taken}
 * @param message human-readable explanation
 * @param fields  per-field messages for validation failures, otherwise absent
 */
// Only ever returned through Response.entity(), so Quarkus cannot detect it for native reflection.
@RegisterForReflection
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String error, String message, Map<String, String> fields) {

    public static ApiError of(String error, String message) {
        return new ApiError(error, message, null);
    }
}
