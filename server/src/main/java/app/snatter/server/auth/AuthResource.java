package app.snatter.server.auth;

import app.snatter.api.AuthApi;
import app.snatter.api.model.AuthResponseDto;
import app.snatter.api.model.ChallengeDto;
import app.snatter.api.model.LoginRequestDto;
import app.snatter.api.model.RegisterRequestDto;
import app.snatter.server.account.AccountDtos;
import app.snatter.server.ratelimit.RateLimited;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.vertx.core.http.HttpServerRequest;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestResponse;

public class AuthResource implements AuthApi {

    private final AuthService auth;
    private final AltchaService challenges;
    private final SecurityIdentity identity;
    private final HttpServerRequest request;
    private final HttpHeaders headers;

    public AuthResource(AuthService auth, AltchaService challenges, SecurityIdentity identity,
                        @Context HttpServerRequest request, @Context HttpHeaders headers) {
        this.auth = auth;
        this.challenges = challenges;
        this.identity = identity;
        this.request = request;
        this.headers = headers;
    }

    @Override
    @RateLimited("challenge")
    public RestResponse<ChallengeDto> getChallenge() {
        AltchaService.Challenge c = challenges.create();
        return RestResponse.ok(new ChallengeDto()
            .algorithm(ChallengeDto.AlgorithmEnum.fromValue(c.algorithm()))
            .challenge(c.challenge())
            .salt(c.salt())
            .signature(c.signature())
            .maxnumber(c.maxnumber()));
    }

    @Override
    @RateLimited("register")
    public RestResponse<AuthResponseDto> register(RegisterRequestDto body) {
        AuthService.Login login = auth.register(
            new AuthService.Registration(body.getUsername(), body.getPassword(), body.getDisplayName(), body.getAltcha(), body.getInviteCode()),
            clientIp(), userAgent());
        return RestResponse.status(Response.Status.CREATED, toDto(login));
    }

    @Override
    @RateLimited("login")
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
