package app.snatter.server.auth;

import io.quarkus.security.identity.request.BaseAuthenticationRequest;

/** Carries a bearer token from the HTTP layer to {@link SessionIdentityProvider}. */
public class SessionTokenAuthenticationRequest extends BaseAuthenticationRequest {

    private final String token;

    public SessionTokenAuthenticationRequest(String token) {
        this.token = token;
    }

    public String token() {
        return token;
    }
}
