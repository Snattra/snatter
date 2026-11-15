package app.snatter.server.api;

import app.snatter.api.model.ApiErrorDto;
import io.quarkus.security.ForbiddenException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.TreeMap;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/** Turns exceptions into the {@code ApiError} body defined in the OpenAPI contract. */
public class ApiExceptionMappers {

    @ServerExceptionMapper
    public Response apiException(ApiException e) {
        return Response.status(e.status())
            .type(MediaType.APPLICATION_JSON)
            .entity(new ApiErrorDto().error(e.code()).message(e.getMessage()))
            .build();
    }

    /** Raised by {@code @PermissionsAllowed} when the caller lacks the permission. */
    @ServerExceptionMapper
    public Response forbidden(ForbiddenException e) {
        return Response.status(403)
            .type(MediaType.APPLICATION_JSON)
            .entity(new ApiErrorDto().error("forbidden").message("You do not have permission to do this"))
            .build();
    }

    @ServerExceptionMapper
    public Response validationFailed(ConstraintViolationException e) {
        Map<String, String> fields = new TreeMap<>();
        for (ConstraintViolation<?> v : e.getConstraintViolations()) {
            fields.put(fieldOf(v), v.getMessage());
        }
        return Response.status(400)
            .type(MediaType.APPLICATION_JSON)
            .entity(new ApiErrorDto().error("validation_failed").message("Request is invalid").fields(fields))
            .build();
    }

    /**
     * The field a violation is about, by its own name: {@code username} for
     * {@code register.registerRequestDto.username}. An entry of a list is
     * named after the list, {@code clientOrigins[0]}.
     */
    private static String fieldOf(ConstraintViolation<?> v) {
        String field = v.getPropertyPath().toString();
        for (Path.Node node : v.getPropertyPath()) {
            switch (node.getKind()) {
                case PROPERTY, PARAMETER -> field = node.getName();
                case CONTAINER_ELEMENT -> field = field + "[" + node.getIndex() + "]";
                default -> {
                }
            }
        }
        return field;
    }
}
