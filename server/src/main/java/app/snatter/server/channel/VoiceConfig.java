package app.snatter.server.channel;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/** Operator limits for voice channels. */
@ConfigMapping(prefix = "snatter.voice")
public interface VoiceConfig {

    /** Bitrate of new voice channels, in bits per second. */
    @WithDefault("64000")
    int defaultBitrate();

    /** Highest bitrate a channel may be set to, in bits per second. */
    @WithDefault("256000")
    int maxBitrate();

    /** Bitrate new voice channels get: the default, capped at the maximum. */
    default int newChannelBitrate() {
        return Math.min(defaultBitrate(), maxBitrate());
    }
}
