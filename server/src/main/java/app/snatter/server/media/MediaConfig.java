package app.snatter.server.media;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.Optional;

/**
 * Where voice travels. Set with environment variables like the HTTP port,
 * as server/README.md lists.
 */
@ConfigMapping(prefix = "snatter.media")
public interface MediaConfig {

    /** The UDP port for voice, on every address of the machine. 0 takes any free port, which only tests do. */
    @WithDefault("8080")
    int port();

    /**
     * The address people reach the port at when it is none of the machine's
     * own, behind NAT or in a container: an IP address, or a host name
     * looked up at start.
     */
    Optional<String> address();
}
