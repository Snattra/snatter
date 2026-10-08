package app.snatter.server.media;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

@QuarkusTest
class VoiceConnectionTest {

    @Inject
    MediaPort port;

    @Test
    void membersInAChannelHearEachOther() throws Exception {
        try (VoiceConnection alice = new VoiceConnection(port.open(), "alice");
             VoiceConnection bob = new VoiceConnection(port.open(), "bob")) {
            String aliceOffer = alice.hear(List.of(), 64000).orElseThrow();
            try (StandInBrowser aliceBrowser = new StandInBrowser(aliceOffer)) {
                assertEquals(Optional.empty(), alice.accept(aliceBrowser.answer()));
                aliceBrowser.connect();

                // Bob comes: alice is offered his voice on a line of its own, and he hers.
                String aliceAgain = alice.hear(List.of(bob), 64000).orElseThrow();
                String bobOffer = bob.hear(List.of(alice), 64000).orElseThrow();
                try (StandInBrowser bobBrowser = new StandInBrowser(bobOffer)) {
                    alice.accept(aliceBrowser.answer(aliceAgain));
                    bob.accept(bobBrowser.answer());
                    bobBrowser.connect();
                    alice.ready().toCompletableFuture().get(10, SECONDS);
                    bob.ready().toCompletableFuture().get(10, SECONDS);

                    byte[] sent = StandInBrowser.rtp(0x1234_5678, 1, "from bob");
                    bobBrowser.send(sent);
                    byte[] heard = aliceBrowser.receive(5000);
                    // The same packet, as the source alice's offer named for bob.
                    RtpPacket asHeard = new RtpPacket(heard, 0, heard.length);
                    assertEquals(StandInBrowser.ssrcOf(aliceAgain, "bob"), asHeard.ssrc());
                    asHeard.setSsrc(0x1234_5678);
                    assertArrayEquals(sent, heard);
                }
            }
        }
    }

    @Test
    void aLineWhoseMemberLeftCarriesTheNextToCome() throws Exception {
        try (VoiceConnection alice = new VoiceConnection(port.open(), "alice");
             VoiceConnection bob = new VoiceConnection(port.open(), "bob");
             VoiceConnection carol = new VoiceConnection(port.open(), "carol")) {
            String first = alice.hear(List.of(bob), 64000).orElseThrow();
            try (StandInBrowser browser = new StandInBrowser(first)) {
                alice.accept(browser.answer());
                Integer bobsSource = StandInBrowser.ssrcOf(first, "bob");

                String bobLeft = alice.hear(List.of(), 64000).orElseThrow();
                assertTrue(bobLeft.contains("a=mid:1\r\na=inactive"), bobLeft);
                assertNull(StandInBrowser.ssrcOf(bobLeft, "bob"));
                alice.accept(browser.answer(bobLeft));

                String carolCame = alice.hear(List.of(carol), 64000).orElseThrow();
                assertTrue(carolCame.contains("a=mid:1\r\na=sendonly"), carolCame);
                assertTrue(!carolCame.contains("a=mid:2"), carolCame);
                assertNotEquals(bobsSource, StandInBrowser.ssrcOf(carolCame, "carol"));
            }
        }
    }

    @Test
    void aChangeWhileAnOfferWaitsIsOfferedOnceItIsAnswered() throws Exception {
        try (VoiceConnection alice = new VoiceConnection(port.open(), "alice");
             VoiceConnection bob = new VoiceConnection(port.open(), "bob")) {
            String first = alice.hear(List.of(), 64000).orElseThrow();
            try (StandInBrowser browser = new StandInBrowser(first)) {
                // Nothing changed, so nothing to offer; then a change, which waits for the answer.
                assertEquals(Optional.empty(), alice.hear(List.of(), 64000));
                assertEquals(Optional.empty(), alice.hear(List.of(bob), 64000));

                String next = alice.accept(browser.answer()).orElseThrow();
                assertTrue(next.contains("o=- " + session(first) + " 2 "), next);
                assertTrue(next.contains("a=msid:bob voice-1"), next);
                // Answering again with nothing waiting is ignored.
                alice.accept(browser.answer(next));
                assertEquals(Optional.empty(), alice.accept(browser.answer(next)));

                assertTrue(alice.hear(List.of(bob), 96000).orElseThrow().contains("maxaveragebitrate=96000"));
            }
        }
    }

    @Test
    void aPeerThatNeverAnswersFailsAtTheDeadlineCountedFromTheOffer() {
        try (VoiceConnection voice = new VoiceConnection(port.open(), "alice", Duration.ofMillis(200))) {
            voice.hear(List.of(), 64000);
            ExecutionException failed = assertThrows(ExecutionException.class,
                () -> voice.ready().toCompletableFuture().get(5, SECONDS));
            assertInstanceOf(TimeoutException.class, failed.getCause());
        }
    }

    private static String session(String offer) {
        return Arrays.stream(offer.split("\r\n")).filter(line -> line.startsWith("o=")).findFirst().orElseThrow()
            .split(" ")[1];
    }
}
