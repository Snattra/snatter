package app.snatter.server.media;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import org.ice4j.Transport;
import org.ice4j.TransportAddress;
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
        sockets = SinglePortUdpHarvester.createHarvesters(config.port());
        if (sockets.isEmpty()) {
            throw new IllegalStateException("Could not open UDP port " + config.port() + " for voice on any address");
        }
        harvesters.addAll(sockets);
        if (config.address().isPresent()) {
            InetAddress address = InetAddress.getByName(config.address().get());
            for (SinglePortUdpHarvester socket : sockets) {
                TransportAddress local = socket.getLocalAddress();
                // An IPv4 address stands in front of IPv4 sockets only, and a socket on it needs no stand-in.
                if (local.getAddress().getClass() == address.getClass() && !local.getAddress().equals(address)) {
                    harvesters.add(new StaticMappingCandidateHarvester(
                        new TransportAddress(address, local.getPort(), Transport.UDP), local));
                }
            }
        }
        LOG.infof("Voice on UDP %s%s", sockets.stream().map(SinglePortUdpHarvester::getLocalAddress).toList(),
            config.address().map(address -> ", reached at " + address).orElse(""));
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
