package app.snatter.server.auth;

import app.snatter.server.role.RoleService;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Turns a session token into a {@link SecurityIdentity}: looks the session up,
 * resolves the account's permissions once, and installs a permission checker
 * so {@code @PermissionsAllowed} works on resources.
 */
@ApplicationScoped
public class SessionIdentityProvider implements IdentityProvider<SessionTokenAuthenticationRequest> {

    private final AuthService auth;
    private final RoleService roles;

    public SessionIdentityProvider(AuthService auth, RoleService roles) {
        this.auth = auth;
        this.roles = roles;
    }

    @Override
    public Class<SessionTokenAuthenticationRequest> getRequestType() {
        return SessionTokenAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(SessionTokenAuthenticationRequest request, AuthenticationRequestContext context) {
        // JDBC blocks, so run off the event loop.
        return context.runBlocking(() -> {
            AuthService.Authenticated a = auth.authenticate(request.token())
                .orElseThrow(() -> new AuthenticationFailedException("Invalid or expired session token"));
            RoleService.Resolution resolution = roles.resolve(a.account().id());
            boolean owner = resolution.highestPosition() == Integer.MAX_VALUE;
            AccountPrincipal principal = new AccountPrincipal(
                a.account().id(), a.account().username(), a.session().id(),
                owner, resolution.permissions(), resolution.highestPosition());
            return QuarkusSecurityIdentity.builder()
                .setPrincipal(principal)
                .addPermissionChecker(permission -> Uni.createFrom().item(principal.hasNamed(permission.getName())))
                .build();
        });
    }
}
