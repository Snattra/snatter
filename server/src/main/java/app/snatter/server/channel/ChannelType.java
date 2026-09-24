package app.snatter.server.channel;

/** What a channel carries. */
public enum ChannelType {
    TEXT("text"),
    VOICE("voice"),
    VOICE_TEXT("voice_text");

    private final String dbValue;

    ChannelType(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    /** Whether members can connect for audio; such channels have a bitrate and user limit. */
    public boolean hasVoice() {
        return this != TEXT;
    }

    /** Whether the channel keeps a message history, including system notices. */
    public boolean hasMessages() {
        return this != VOICE;
    }

    public static ChannelType fromDbValue(String value) {
        for (ChannelType type : values()) {
            if (type.dbValue.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown channel type: " + value);
    }
}
