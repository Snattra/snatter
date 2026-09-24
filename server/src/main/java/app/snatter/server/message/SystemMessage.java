package app.snatter.server.message;

import app.snatter.server.account.AccountId;
import app.snatter.server.channel.ChannelId;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** A notice the server wrote about something that happened; the author is who caused it. */
public record SystemMessage(
        MessageId id,
        ChannelId channelId,
        AccountId authorId,
        SystemNotice notice,
        Instant createdAt) implements Message {

    public static SystemMessage create(ChannelId channelId, AccountId actor, SystemNotice notice) {
        return new SystemMessage(MessageId.newId(), channelId, actor, notice, Instant.now().truncatedTo(ChronoUnit.MICROS));
    }
}
