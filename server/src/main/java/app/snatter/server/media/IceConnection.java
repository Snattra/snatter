package app.snatter.server.media;

import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.ice4j.TransportAddress;
import org.ice4j.ice.Agent;
import org.ice4j.ice.CandidatePair;
import org.ice4j.ice.Component;
import org.ice4j.ice.IceMediaStream;
import org.ice4j.ice.IceProcessingState;
import org.ice4j.ice.KeepAliveStrategy;
import org.ice4j.ice.harvest.CandidateHarvester;

/**
 * The server's side of ICE for one connection, on the media port. It hands
 * out credentials and candidates to offer, then answers the peer's checks.
 * The peer leads: it checks the candidates and picks the path, as with a
 * server that implements ICE lite, so the server needs none of the peer's
 * candidates and learns its address from the checks.
 */
public final class IceConnection implements AutoCloseable {

    /**
     * A candidate to offer: an address the peer may send its checks to. A
     * server-reflexive one, the configured address, stands in front of one
     * of the machine's own: its related address. A host candidate has none.
     */
    public record Candidate(String foundation, long priority, String type, InetSocketAddress address,
                            InetSocketAddress related) {
    }

    /** The path the checks found: the socket to read and write media on, and the peer's address. */
    public record Path(DatagramSocket socket, InetSocketAddress remote) {
    }

    private final Agent agent;
    private final IceMediaStream stream;
    private final Component component;
    private final CompletableFuture<Path> connected = new CompletableFuture<>();

    IceConnection(List<CandidateHarvester> harvesters) {
        agent = new Agent();
        agent.setControlling(false);
        agent.setUseDynamicPorts(false);
        harvesters.forEach(agent::addCandidateHarvester);
        stream = agent.createMediaStream("media");
        try {
            component = agent.createComponent(stream, KeepAliveStrategy.SELECTED_ONLY, true);
        } catch (IOException e) {
            agent.free();
            throw new UncheckedIOException(e);
        }
        agent.addStateChangeListener(this::stateChanged);
    }

    public String ufrag() {
        return agent.getLocalUfrag();
    }

    public String password() {
        return agent.getLocalPassword();
    }

    public List<Candidate> candidates() {
        return component.getLocalCandidates().stream()
            .map(c -> new Candidate(c.getFoundation(), c.getPriority(), c.getType().toString(),
                plain(c.getTransportAddress()), plain(c.getRelatedAddress())))
            .toList();
    }

    /** The address without ice4j's transport, or null. */
    private static InetSocketAddress plain(TransportAddress address) {
        return address == null ? null : new InetSocketAddress(address.getAddress(), address.getPort());
    }

    /** Starts answering the peer's checks, once its credentials are known. */
    public void start(String remoteUfrag, String remotePassword) {
        stream.setRemoteUfrag(remoteUfrag);
        stream.setRemotePassword(remotePassword);
        agent.startConnectivityEstablishment();
    }

    /** Completes when the peer has picked a path, or fails when ICE does. */
    public CompletionStage<Path> connected() {
        return connected.minimalCompletionStage();
    }

    private void stateChanged(PropertyChangeEvent event) {
        if (!Agent.PROPERTY_ICE_PROCESSING_STATE.equals(event.getPropertyName())) {
            return;
        }
        IceProcessingState state = (IceProcessingState) event.getNewValue();
        if (state == IceProcessingState.COMPLETED) {
            CandidatePair pair = component.getSelectedPair();
            connected.complete(new Path(component.getSocket(), pair.getRemoteCandidate().getTransportAddress()));
        } else if (state == IceProcessingState.FAILED) {
            connected.completeExceptionally(new IOException("ICE failed"));
        }
    }

    @Override
    public void close() {
        connected.completeExceptionally(new IOException("ICE closed"));
        agent.free();
    }
}
