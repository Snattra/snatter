package app.snatter.server.auth;

import app.snatter.server.account.Account;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.core.http.HttpServerRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.Instant;

@Path("/api/v1/auth")
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {

    public record RegisterRequest(
        @NotBlank
        @Size(min = 3, max = 32)
        @Pattern(regexp = "[A-Za-z0-9_.]+", message = "may only contain letters, digits, underscore and dot")
        String username,

        @NotNull
        @Size(min = 8, max = 128)
        String password,

        @Size(max = 64)
        String displayName) {
    }

    public record LoginRequest(
        @NotBlank String username,
        @NotNull String password) {
    }

    public record AuthResponse(String token, Instant expiresAt, Account account) {
        static AuthResponse of(AuthService.Login login) {
            return new AuthResponse(login.token(), login.expiresAt(), login.account());
        }
    }

    private final AuthService auth;
    private final SecurityIdentity identity;
    private final HttpServerRequest request;
    private final HttpHeaders headers;

    public AuthResource(AuthService auth, SecurityIdentity identity,
                        @Context HttpServerRequest request, @Context HttpHeaders headers) {
        this.auth = auth;
        this.identity = identity;
        this.request = request;
        this.headers = headers;
    }

    @POST
    @Path("/register")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response register(@Valid RegisterRequest body) {
        AuthService.Login login = auth.register(
            body.username(), body.password(), body.displayName(), clientIp(), userAgent());
        return Response.status(Response.Status.CREATED).entity(AuthResponse.of(login)).build();
    }

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    public AuthResponse login(@Valid LoginRequest body) {
        return AuthResponse.of(auth.login(body.username(), body.password(), clientIp(), userAgent()));
    }

    /** Revokes the session used to make this call. */
    @POST
    @Path("/logout")
    @Authenticated
    public Response logout() {
        AccountPrincipal principal = (AccountPrincipal) identity.getPrincipal();
        auth.logout(principal.sessionId());
        return Response.noContent().build();
    }

    private String clientIp() {
        // Proxy headers are honoured only once quarkus.http.proxy.* is configured; see server README.
        return request.remoteAddress() == null ? null : request.remoteAddress().hostAddress();
    }

    private String userAgent() {
        return headers.getHeaderString(HttpHeaders.USER_AGENT);
    }
}
