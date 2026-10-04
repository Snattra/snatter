package app.snatter.server.channel;

import jakarta.enterprise.event.Observes;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Collects the channel events fired during tests. */
@Singleton
public class RecordedChannelEvents {

    private final List<ChannelEvent> events = new CopyOnWriteArrayList<>();

    void record(@Observes ChannelEvent event) {
        events.add(event);
    }

    public List<ChannelEvent> about(ChannelId channel) {
        return events.stream().filter(e -> e.channelId().equals(channel)).toList();
    }
}
