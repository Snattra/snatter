package app.snatter.server.media;

import static java.util.concurrent.TimeUnit.SECONDS;

import app.snatter.server.media.IceConnection.Candidate;
import java.io.IOException;
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

/** Stands in for a browser's ICE: it leads the checks, as browsers will with the server. */
final class IcePeer implements AutoCloseable {

    final Agent agent = new Agent();
    final Component component;

    IcePeer(IceConnection server) throws IOException {
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
