package app.snatter.server.channel;

/**
 * Settings of a channel with voice.
 *
 * @param bitrate   audio bitrate in bits per second
 * @param userLimit most members connected at once, 0 for no limit
 */
public record VoiceSettings(int bitrate, int userLimit) {

    public VoiceSettings withBitrate(int bitrate) {
        return new VoiceSettings(bitrate, userLimit);
    }

    public VoiceSettings withUserLimit(int userLimit) {
        return new VoiceSettings(bitrate, userLimit);
    }
}
