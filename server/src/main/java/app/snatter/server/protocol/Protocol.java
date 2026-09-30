package app.snatter.server.protocol;

/** The protocol version this server speaks and the oldest client version it accepts. */
public final class Protocol {

    /** The {@code info.version} of the contract this server implements; a test keeps the two equal. */
    public static final ProtocolVersion CURRENT = new ProtocolVersion(1, 0);

    /**
     * The oldest client version let in: the first of the current major,
     * unless a release raises it to turn away clients with a known problem.
     */
    public static final ProtocolVersion MIN_CLIENT = new ProtocolVersion(1, 0);

    private Protocol() {
    }

    /** Whether a client speaking the version may connect. */
    public static boolean accepts(ProtocolVersion client) {
        return !client.isOlderThan(MIN_CLIENT);
    }
}
