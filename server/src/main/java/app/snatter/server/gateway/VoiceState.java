package app.snatter.server.gateway;

import app.snatter.api.model.VoiceStateDto;
import app.snatter.server.account.AccountId;
import app.snatter.server.channel.ChannelId;

/**
 * A member in a voice channel, as others see them. Lives only in the gateway,
 * like presence.
 *
 * @param selfMuted    the member turned their microphone off; always true while deafened
 * @param selfDeafened the member turned sound off
 */
record VoiceState(AccountId accountId, ChannelId channelId, boolean selfMuted, boolean selfDeafened) {

    VoiceState {
        selfMuted = selfMuted || selfDeafened;
    }

    VoiceStateDto toDto() {
        return new VoiceStateDto()
            .accountId(accountId)
            .channelId(channelId)
            .selfMuted(selfMuted)
            .selfDeafened(selfDeafened);
    }
}
