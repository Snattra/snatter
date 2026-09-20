package app.snatter.server.auth;

import app.snatter.api.AuthApi;
import app.snatter.api.model.AuthResponseDto;
import app.snatter.api.model.LoginRequestDto;
import app.snatter.api.model.RegisterRequestDto;
import app.snatter.server.account.AccountDtos;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.core.http.HttpServerRequest;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestResponse;

public class AuthResource implements AuthApi {

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

    @Override
    public RestResponse<AuthResponseDto> register(RegisterRequestDto body) {
        AuthService.Login login = auth.register(
            body.getUsername(), body.getPassword(), body.getDisplayName(), clientIp(), userAgent());
        return RestResponse.status(Response.Status.CREATED, toDto(login));
    }

    @Override
    public RestResponse<AuthResponseDto> login(LoginRequestDto body) {
        return RestResponse.ok(toDto(auth.login(body.getUsername(), body.getPassword(), clientIp(), userAgent())));
    }

    /** Revokes the session used to make this call. */
    @Override
    @Authenticated
    public RestResponse<Void> logout() {
        AccountPrincipal principal = (AccountPrincipal) identity.getPrincipal();
        auth.logout(principal.sessionId());
        return RestResponse.noContent();
    }

    private static AuthResponseDto toDto(AuthService.Login login) {
        return new AuthResponseDto()
            .token(login.token())
            .expiresAt(login.expiresAt())
            .account(AccountDtos.toDto(login.account()));
    }

    private String clientIp() {
        // Proxy headers are honoured only once quarkus.http.proxy.* is configured.
        return request.remoteAddress() == null ? null : request.remoteAddress().hostAddress();
    }

    private String userAgent() {
        return headers.getHeaderString(HttpHeaders.USER_AGENT);
    }
}
