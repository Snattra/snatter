package app.snatter.server.media;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

@QuarkusTest
class VoiceConnectionTest {

    @Inject
    MediaPort port;

    @Test
    void aPeerThatAnswersTheOfferConnectsAndItsVoiceArrives() throws Exception {
        try (VoiceConnection voice = new VoiceConnection(port.open(), 64000);
             StandInBrowser browser = new StandInBrowser(voice.offer())) {
            BlockingQueue<RtpPacket> received = new LinkedBlockingQueue<>();
            voice.accept(browser.answer(), received::add);
            browser.connect();
            voice.ready().toCompletableFuture().get(10, SECONDS);

            byte[] sent = DtlsPeer.rtp(0x1234_5678, "hello");
            browser.send(sent);
            RtpPacket arrived = received.poll(5, SECONDS);
            assertArrayEquals(sent, Arrays.copyOfRange(arrived.getBuffer(), arrived.getOffset(),
                arrived.getOffset() + arrived.getLength()));
        }
    }

    @Test
    void aPeerThatNeverAnswersFailsAtTheDeadlineCountedFromTheOffer() {
        try (VoiceConnection voice = new VoiceConnection(port.open(), 64000, Duration.ofMillis(200))) {
            ExecutionException failed = assertThrows(ExecutionException.class,
                () -> voice.ready().toCompletableFuture().get(5, SECONDS));
            assertInstanceOf(TimeoutException.class, failed.getCause());
        }
    }
}
