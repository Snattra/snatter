package app.snatter.server.info;

import app.snatter.server.settings.ServerSettings;
import app.snatter.server.settings.ServerSettingsRepository;
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
    private final ServerSettingsRepository settings;

    public ServerInfoResource(
            @ConfigProperty(name = "quarkus.application.version") String version,
            ServerSettingsRepository settings) {
        this.version = version;
        this.settings = settings;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public ServerInfo get() {
        ServerSettings s = settings.get();
        return new ServerInfo(
            "Snatter",
            version,
            API_VERSION,
            new ServerInfo.Community(s.name(), s.description()));
    }
}
