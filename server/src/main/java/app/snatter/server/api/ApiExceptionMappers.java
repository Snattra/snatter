package app.snatter.server.api;

import app.snatter.api.model.ApiErrorDto;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
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

    @ServerExceptionMapper
    public Response validationFailed(ConstraintViolationException e) {
        Map<String, String> fields = new TreeMap<>();
        for (ConstraintViolation<?> v : e.getConstraintViolations()) {
            fields.put(lastPathSegment(v), v.getMessage());
        }
        return Response.status(400)
            .type(MediaType.APPLICATION_JSON)
            .entity(new ApiErrorDto().error("validation_failed").message("Request is invalid").fields(fields))
            .build();
    }

    /** Turns {@code register.registerRequestDto.username} into {@code username}. */
    private static String lastPathSegment(ConstraintViolation<?> v) {
        String path = v.getPropertyPath().toString();
        int dot = path.lastIndexOf('.');
        return dot < 0 ? path : path.substring(dot + 1);
    }
}
