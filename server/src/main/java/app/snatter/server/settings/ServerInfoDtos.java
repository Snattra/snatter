package app.snatter.server.settings;

import app.snatter.api.model.CommunityDto;
import app.snatter.api.model.ProtocolInfoDto;
import app.snatter.api.model.RegistrationInfoDto;
import app.snatter.api.model.RegistrationModeDto;
import app.snatter.api.model.ServerInfoDto;
import app.snatter.api.model.VoiceInfoDto;
import app.snatter.server.protocol.Protocol;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** The public description of this server, as served over HTTP and the gateway. */
@ApplicationScoped
public class ServerInfoDtos {

    private final String version;
    private final ServerSettingsService settings;

    public ServerInfoDtos(@ConfigProperty(name = "quarkus.application.version") String version,
                          ServerSettingsService settings) {
        this.version = version;
        this.settings = settings;
    }

    public ServerInfoDto toDto(ServerSettings s) {
        return new ServerInfoDto()
            .name("Snatter")
            .version(version)
            .protocol(new ProtocolInfoDto()
                .version(Protocol.CURRENT.toString())
                .minClient(Protocol.MIN_CLIENT.toString()))
            .community(new CommunityDto().name(s.name()).description(s.description()))
            .ownerId(s.ownerId())
            .registration(new RegistrationInfoDto()
                .mode(RegistrationModeDto.fromValue(s.registrationMode().dbValue()))
                .challengeRequired(s.challengeRequired())
                .setupRequired(settings.setupRequired()))
            .voice(new VoiceInfoDto().defaultBitrate(s.voiceDefaultBitrate()));
    }
}
