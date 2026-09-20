package app.snatter.server.info;

import app.snatter.api.ServerApi;
import app.snatter.api.model.CommunityDto;
import app.snatter.api.model.ServerInfoDto;
import app.snatter.server.settings.ServerSettings;
import app.snatter.server.settings.ServerSettingsRepository;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

public class ServerInfoResource implements ServerApi {

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

    @Override
    public Response getServerInfo() {
        ServerSettings s = settings.get();
        ServerInfoDto info = new ServerInfoDto()
            .name("Snatter")
            .version(version)
            .apiVersion(API_VERSION)
            .community(new CommunityDto().name(s.name()).description(s.description()));
        return Response.ok(info).build();
    }
}
