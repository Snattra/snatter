package app.snatter.server.api;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.TreeMap;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

public class ApiExceptionMappers {

    @ServerExceptionMapper
    public Response apiException(ApiException e) {
        return Response.status(e.status())
            .type(MediaType.APPLICATION_JSON)
            .entity(ApiError.of(e.code(), e.getMessage()))
            .build();
    }

    @ServerExceptionMapper
    public Response validationFailed(ConstraintViolationException e) {
        Map<String, String> fields = new TreeMap<>();
        for (ConstraintViolation<?> v : e.getConstraintViolations()) {
            fields.put(lastPathSegment(v), v.getMessage());
        }
        return Response.status(400)
            .type(MediaType.APPLICATION_JSON)
            .entity(new ApiError("validation_failed", "Request is invalid", fields))
            .build();
    }

    /** Turns {@code register.request.username} into {@code username}. */
    private static String lastPathSegment(ConstraintViolation<?> v) {
        String path = v.getPropertyPath().toString();
        int dot = path.lastIndexOf('.');
        return dot < 0 ? path : path.substring(dot + 1);
    }
}
