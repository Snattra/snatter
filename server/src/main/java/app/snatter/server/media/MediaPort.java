package app.snatter.server.media;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.ice4j.Transport;
import org.ice4j.TransportAddress;
import org.ice4j.ice.harvest.AbstractUdpListener;
import org.ice4j.ice.harvest.CandidateHarvester;
import org.ice4j.ice.harvest.SinglePortUdpHarvester;
import org.ice4j.ice.harvest.StaticMappingCandidateHarvester;
import org.jboss.logging.Logger;

/**
 * The one UDP port all voice travels through. Every connection runs its ICE
 * checks and media over it, told apart by their ICE credentials, so whoever
 * runs the server opens one port however many people talk.
 */
@ApplicationScoped
@Startup
public class MediaPort {

    private static final Logger LOG = Logger.getLogger(MediaPort.class);

    /** One socket per address of the machine, all on the port. */
    private final List<SinglePortUdpHarvester> sockets;
    /** The sockets, and the configured address in front of them. */
    private final List<CandidateHarvester> harvesters = new ArrayList<>();

    public MediaPort(MediaConfig config) throws UnknownHostException {
        configureIce4j();
        // Looked up first, so a name that does not resolve leaves no sockets open.
        Optional<InetAddress> address = config.address().isPresent()
            ? Optional.of(InetAddress.getByName(config.address().get()))
            : Optional.empty();
        sockets = open(config.port());
        harvesters.addAll(sockets);
        address.ifPresent(reached -> {
            for (SinglePortUdpHarvester socket : sockets) {
                TransportAddress local = socket.getLocalAddress();
                // An IPv4 address stands in front of IPv4 sockets only, and a socket on it needs no stand-in.
                if (local.getAddress().getClass() == reached.getClass() && !local.getAddress().equals(reached)) {
                    harvesters.add(new StaticMappingCandidateHarvester(
                        new TransportAddress(reached, local.getPort(), Transport.UDP), local));
                }
            }
        });
        LOG.infof("Voice on UDP %s%s", sockets.stream().map(SinglePortUdpHarvester::getLocalAddress).toList(),
            config.address().map(reached -> ", reached at " + reached).orElse(""));
    }

    /**
     * Opens the port on every address of the machine that it can. An address
     * it cannot open is a warning, as it may be one nobody uses; none at all
     * fails the start.
     */
    private static List<SinglePortUdpHarvester> open(int port) {
        List<SinglePortUdpHarvester> opened = new ArrayList<>();
        for (TransportAddress address : AbstractUdpListener.getAllowedAddresses(port)) {
            try {
                opened.add(new SinglePortUdpHarvester(address));
            } catch (IOException e) {
                LOG.warnf("Could not open UDP %s for voice: %s", address, e.getMessage());
            }
        }
        if (opened.isEmpty()) {
            throw new IllegalStateException("Could not open UDP port " + port + " for voice on any address");
        }
        return opened;
    }

    /**
     * ice4j reads its settings from system properties when its classes
     * first load, so they are set before anything touches it.
     */
    private static void configureIce4j() {
        // Never reachable from another machine.
        System.setProperty("ice4j.harvest.use-link-local-addresses", "false");
        // Otherwise ice4j asks the EC2 metadata address whether it runs on AWS, at every start.
        System.setProperty("ice4j.harvest.mapping.aws.enabled", "false");
    }

    /** Starts the server's side of ICE for one more connection. */
    public IceConnection open() {
        return new IceConnection(harvesters);
    }

    @PreDestroy
    void close() {
        sockets.forEach(SinglePortUdpHarvester::close);
    }
}
