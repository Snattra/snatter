package app.snatter.server.auth;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/** Turns a session token into a {@link SecurityIdentity} by looking it up in the database. */
@ApplicationScoped
public class SessionIdentityProvider implements IdentityProvider<SessionTokenAuthenticationRequest> {

    private final AuthService auth;

    public SessionIdentityProvider(AuthService auth) {
        this.auth = auth;
    }

    @Override
    public Class<SessionTokenAuthenticationRequest> getRequestType() {
        return SessionTokenAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(SessionTokenAuthenticationRequest request, AuthenticationRequestContext context) {
        // JDBC blocks, so run off the event loop.
        return context.runBlocking(() -> auth.authenticate(request.token())
            .map(a -> (SecurityIdentity) QuarkusSecurityIdentity.builder()
                .setPrincipal(new AccountPrincipal(a.account().id(), a.account().username(), a.session().id()))
                .build())
            .orElseThrow(() -> new AuthenticationFailedException("Invalid or expired session token")));
    }
}
