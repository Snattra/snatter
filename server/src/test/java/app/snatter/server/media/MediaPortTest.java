package app.snatter.server.media;

import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import app.snatter.server.media.IceConnection.Candidate;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.ice4j.Transport;
import org.ice4j.TransportAddress;
import org.ice4j.ice.Agent;
import org.ice4j.ice.CandidatePair;
import org.ice4j.ice.CandidateType;
import org.ice4j.ice.Component;
import org.ice4j.ice.IceMediaStream;
import org.ice4j.ice.IceProcessingState;
import org.ice4j.ice.KeepAliveStrategy;
import org.ice4j.ice.RemoteCandidate;
import org.junit.jupiter.api.Test;

@QuarkusTest
class MediaPortTest {

    @Inject
    MediaPort port;

    @Test
    void aPeerFindsThePortAndWhatItSendsArrives() throws Exception {
        try (IceConnection server = port.open(); Peer peer = new Peer(server)) {
            server.start(peer.agent.getLocalUfrag(), peer.agent.getLocalPassword());
            CandidatePair pair = peer.connect();
            IceConnection.Path path = server.connected().toCompletableFuture().get(10, SECONDS);
            TransportAddress peerAddress = pair.getLocalCandidate().getTransportAddress();
            assertEquals(new InetSocketAddress(peerAddress.getAddress(), peerAddress.getPort()), path.remote());

            // Shaped like RTP, which is what will travel this way.
            byte[] sent = {(byte) 0x80, 111, 0, 1, 0, 0, 0, 0, 0, 0, 0, 42, 1, 2, 3};
            peer.component.getSocket().send(
                new DatagramPacket(sent, sent.length, pair.getRemoteCandidate().getTransportAddress()));
            byte[] buffer = new byte[1500];
            DatagramPacket received = new DatagramPacket(buffer, buffer.length);
            path.socket().setSoTimeout(5000);
            path.socket().receive(received);
            assertArrayEquals(sent, Arrays.copyOf(buffer, received.getLength()));
        }
    }

    @Test
    void theConfiguredAddressStandsInFrontOfEachSocketOfItsFamily() {
        try (IceConnection server = port.open()) {
            Set<InetSocketAddress> sockets = server.candidates().stream()
                .filter(c -> c.type().equals("host") && c.address().getAddress() instanceof Inet4Address)
                .map(Candidate::address)
                .collect(toSet());
            List<Candidate> reflexive = server.candidates().stream().filter(c -> c.type().equals("srflx")).toList();
            assertFalse(sockets.isEmpty());
            assertEquals(sockets, reflexive.stream().map(Candidate::related).collect(toSet()));
            for (Candidate c : reflexive) {
                assertEquals(new InetSocketAddress("203.0.113.7", c.related().getPort()), c.address());
            }
        }
    }

    /** Stands in for a browser: it leads the checks, as browsers will with the server. */
    private static final class Peer implements AutoCloseable {

        final Agent agent = new Agent();
        final Component component;

        Peer(IceConnection server) throws IOException {
            agent.setControlling(true);
            IceMediaStream stream = agent.createMediaStream("media");
            component = agent.createComponent(stream, KeepAliveStrategy.SELECTED_ONLY, true);
            stream.setRemoteUfrag(server.ufrag());
            stream.setRemotePassword(server.password());
            for (Candidate c : server.candidates()) {
                component.addRemoteCandidate(new RemoteCandidate(
                    new TransportAddress(c.address().getAddress(), c.address().getPort(), Transport.UDP), component,
                    CandidateType.parse(c.type()), c.foundation(), c.priority(), null));
            }
        }

        /** Runs the checks and returns the pair the peer picked. */
        CandidatePair connect() throws Exception {
            CompletableFuture<CandidatePair> completed = new CompletableFuture<>();
            agent.addStateChangeListener(event -> {
                if (event.getNewValue() == IceProcessingState.COMPLETED) {
                    completed.complete(component.getSelectedPair());
                } else if (event.getNewValue() == IceProcessingState.FAILED) {
                    completed.completeExceptionally(new IOException("the peer's checks failed"));
                }
            });
            agent.startConnectivityEstablishment();
            return completed.get(10, SECONDS);
        }

        @Override
        public void close() {
            agent.free();
        }
    }
}
