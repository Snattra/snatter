package app.snatter.server.settings;

import app.snatter.api.model.CommunityDto;
import app.snatter.api.model.RegistrationInfoDto;
import app.snatter.api.model.RegistrationModeDto;
import app.snatter.api.model.ServerInfoDto;
import app.snatter.api.model.VoiceInfoDto;
import app.snatter.server.channel.VoiceConfig;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** The public description of this server, as served over HTTP and the gateway. */
@ApplicationScoped
public class ServerInfoDtos {

    /** Bumped whenever the HTTP or WebSocket API changes incompatibly. */
    public static final int API_VERSION = 1;

    private final String version;
    private final VoiceConfig voice;
    private final ServerSettingsService settings;

    public ServerInfoDtos(@ConfigProperty(name = "quarkus.application.version") String version, VoiceConfig voice,
                          ServerSettingsService settings) {
        this.version = version;
        this.voice = voice;
        this.settings = settings;
    }

    public ServerInfoDto toDto(ServerSettings s) {
        return new ServerInfoDto()
            .name("Snatter")
            .version(version)
            .apiVersion(API_VERSION)
            .community(new CommunityDto().name(s.name()).description(s.description()))
            .registration(new RegistrationInfoDto()
                .mode(RegistrationModeDto.fromValue(s.registrationMode().dbValue()))
                .challengeRequired(s.challengeRequired())
                .setupRequired(settings.setupRequired()))
            .voice(new VoiceInfoDto()
                .defaultBitrate(voice.newChannelBitrate())
                .maxBitrate(voice.maxBitrate()));
    }
}
