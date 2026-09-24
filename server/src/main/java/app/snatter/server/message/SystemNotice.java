package app.snatter.server.message;

import app.snatter.server.settings.RegistrationMode;

/**
 * What a system message is about. Clients render the text from the type, so
 * notices can be translated.
 */
public sealed interface SystemNotice {

    record ChannelCreated() implements SystemNotice {
    }

    record ChannelRenamed(String from, String to) implements SystemNotice {
    }

    /** {@code from} and {@code to} are null when there was or is no topic. */
    record ChannelTopicChanged(String from, String to) implements SystemNotice {
    }

    record MemberJoined() implements SystemNotice {
    }

    record ServerRenamed(String from, String to) implements SystemNotice {
    }

    record RegistrationModeChanged(RegistrationMode from, RegistrationMode to) implements SystemNotice {
    }
}
