package app.snatter.server.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import app.snatter.client.ApiException;
import app.snatter.client.model.ApiErrorDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.function.Executable;

/** Assertions about calls made with {@link ApiClientFactory} clients. */
public final class ApiAssertions {

    private static final ObjectMapper JSON = ApiClientFactory.json();

    private ApiAssertions() {
    }

    /**
     * Makes the call, which the server must refuse with the status and the
     * error code, and returns the error for any further checks.
     */
    public static ApiErrorDto assertApiError(int status, String error, Executable call) {
        ApiErrorDto body = errorOf(assertApiStatus(status, call));
        assertEquals(error, body.getError(), () -> "error code, with message " + body.getMessage());
        return body;
    }

    /** A header of the refusal, whatever the case of its name, or null. */
    public static String header(ApiException refused, String name) {
        return refused.getResponseHeaders().entrySet().stream()
            .filter(header -> header.getKey().equalsIgnoreCase(name))
            .flatMap(header -> header.getValue().stream())
            .findFirst().orElse(null);
    }

    /** The error a refused call answered with, for when its headers matter too. */
    public static ApiErrorDto errorOf(ApiException refused) {
        try {
            return JSON.readValue(refused.getResponseBody(), ApiErrorDto.class);
        } catch (JsonProcessingException | IllegalArgumentException notAnError) {
            return fail("Not an ApiError: " + refused.getResponseBody(), notAnError);
        }
    }

    /**
     * Makes the call, which the server must refuse with the status, and
     * returns the refusal. For refusals without an error body, such as the 401
     * that Quarkus, not the API, sends for a missing or unknown token, and
     * for ones whose headers matter.
     */
    public static ApiException assertApiStatus(int status, Executable call) {
        ApiException e = assertThrows(ApiException.class, call);
        assertEquals(status, e.getCode(), () -> "status, with body " + e.getResponseBody());
        return e;
    }
}
