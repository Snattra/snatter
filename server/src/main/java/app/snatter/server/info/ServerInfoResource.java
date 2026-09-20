package app.snatter.server.info;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Path("/api/v1/server-info")
public class ServerInfoResource {

    /** Bumped whenever the HTTP or WebSocket API changes incompatibly. */
    public static final int API_VERSION = 1;

    private final String version;

    public ServerInfoResource(
            @ConfigProperty(name = "quarkus.application.version") String version) {
        this.version = version;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public ServerInfo get() {
        return new ServerInfo("Snatter", version, API_VERSION);
    }
}
