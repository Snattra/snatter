package app.snatter.server;

import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.info.License;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;

/**
 * Describes the HTTP API. The generated OpenAPI document is written to the
 * {@code protocol/} module on every build and is the contract for clients.
 */
@OpenAPIDefinition(
    info = @Info(
        title = "Snatter API",
        version = "1",
        description = "HTTP API of a Snatter server. Authenticate with POST /api/v1/auth/login "
            + "or /register and send the returned token as 'Authorization: Bearer <token>'.",
        license = @License(name = "Apache-2.0", url = "https://www.apache.org/licenses/LICENSE-2.0")))
@SecurityScheme(
    securitySchemeName = "session",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    description = "Opaque session token issued by /api/v1/auth/login or /register")
public class SnatterApplication extends Application {
}
