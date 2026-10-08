package app.snatter.server.media;

import app.snatter.server.media.DtlsServer.Profile;
import app.snatter.server.media.DtlsServer.SrtpKeys;
import app.snatter.server.media.IceConnection.Path;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.DatagramPacket;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.bouncycastle.tls.DTLSServerProtocol;
import org.bouncycastle.tls.DTLSTransport;
import org.bouncycastle.tls.DatagramTransport;
import org.jboss.logging.Logger;
import org.jitsi.srtp.SrtpContextFactory;
import org.jitsi.srtp.SrtpCryptoContext;
import org.jitsi.srtp.SrtpErrorStatus;
import org.jitsi.srtp.SrtpPolicy;
import org.jitsi.utils.logging2.LoggerImpl;

/**
 * SRTP for one connection, on the path ICE found, with keys from a DTLS
 * handshake (RFC 5764). The peer answers the server's offer and starts the
 * handshake, as browsers do, so the server is the DTLS server. Each side
 * shows a certificate whose fingerprint the other knows from signalling.
 *
 * <p>The path's socket carries DTLS and SRTP alike, told apart by their
 * first byte (RFC 7983). This reads it: DTLS goes to the handshake, RTP is
 * decrypted and handed on, and RTCP is dropped for now. The ICE connection
 * owns the socket; closing it ends the reading.
 */
public final class SrtpConnection implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(SrtpConnection.class);
    /**
     * jitsi-srtp's logging. It names its loggers after its classes, under
     * org.jitsi, which application.properties turns down to warnings.
     */
    private static final org.jitsi.utils.logging2.Logger SRTP_LOG =
        new LoggerImpl(SrtpContextFactory.class.getName());
    /** More than browsers put in one datagram: they stay under the usual MTU. */
    private static final int DATAGRAM_LIMIT = 1500;
    /** Fits IPv6's smallest MTU, so the server's handshake records cross any path whole. */
    private static final int SEND_LIMIT = 1200;
    /** The longest tag SRTP appends: AES-GCM's. */
    static final int TAG_LENGTH = 16;
    /** A browser sends its voice from one source; this allows for a few more, and drops the rest. */
    static final int MAX_SOURCES = 4;

    private final DtlsIdentity identity = DtlsIdentity.generate();
    private final Records records = new Records();
    private final CompletableFuture<Void> ready = new CompletableFuture<>();
    /** Contexts for the sources the peer sends, used only by the reading thread. */
    private final Map<Integer, SrtpCryptoContext> incoming = new HashMap<>();
    /** Contexts for the sources the server sends, guarded by this. */
    private final Map<Integer, SrtpCryptoContext> outgoing = new HashMap<>();
    private volatile Path path;
    private volatile SrtpContextFactory incomingKeys;
    private volatile SrtpContextFactory outgoingKeys;
    private volatile boolean closed;

    /** The SHA-256 fingerprint of the server's certificate for this connection, for signalling. */
    public String fingerprint() {
        return identity.fingerprint();
    }

    /**
     * Starts reading the path and answering the peer's handshake, once ICE
     * has found the path. The peer must show the certificate with the
     * SHA-256 fingerprint it named in signalling. RTP it sends is decrypted
     * and handed to {@code received}, on the reading thread.
     */
    public void start(Path path, String peerFingerprint, Consumer<RtpPacket> received) {
        byte[] expected = DtlsIdentity.FINGERPRINT_FORMAT.parseHex(peerFingerprint);
        if (expected.length != 32) {
            throw new IllegalArgumentException("Not a SHA-256 fingerprint: " + peerFingerprint);
        }
        if (this.path != null) {
            throw new IllegalStateException("Already started");
        }
        this.path = path;
        DtlsServer server = new DtlsServer(identity, expected);
        Thread.ofVirtual().name("srtp-read").start(() -> read(received));
        Thread.ofVirtual().name("dtls").start(() -> handshake(server));
    }

    /** Completes when the keys are agreed, or fails with the handshake. */
    public CompletionStage<Void> ready() {
        return ready.minimalCompletionStage();
    }

    /**
     * Encrypts the packet in place and sends it to the peer. Until the
     * handshake is done there are no keys, and the packet is dropped.
     */
    public synchronized void send(RtpPacket packet) throws IOException {
        SrtpContextFactory keys = outgoingKeys;
        if (keys == null || closed) {
            return;
        }
        try {
            SrtpCryptoContext context = outgoing.get(packet.ssrc());
            if (context == null) {
                context = keys.deriveContext(packet.ssrc(), 0);
                outgoing.put(packet.ssrc(), context);
            }
            // jitsi-srtp writes AES-GCM's tag past the end without asking for room.
            packet.grow(TAG_LENGTH);
            SrtpErrorStatus status = context.transformPacket(packet);
            if (status != SrtpErrorStatus.OK) {
                throw new IOException("Could not encrypt RTP: " + status);
            }
        } catch (GeneralSecurityException e) {
            throw new IOException("Could not encrypt RTP", e);
        }
        path.socket().send(
            new DatagramPacket(packet.getBuffer(), packet.getOffset(), packet.getLength(), path.remote()));
    }

    /** Lets go of what was kept for a source the server no longer sends. */
    public synchronized void forget(int ssrc) {
        outgoing.remove(ssrc);
    }

    private void read(Consumer<RtpPacket> received) {
        byte[] buffer = new byte[DATAGRAM_LIMIT];
        DatagramPacket datagram = new DatagramPacket(buffer, buffer.length);
        while (true) {
            try {
                datagram.setLength(buffer.length);
                path.socket().receive(datagram);
            } catch (IOException e) {
                // The ICE connection closed the socket.
                close();
                return;
            }
            // Closed while waiting: whatever woke the thread is not handed on.
            if (closed) {
                return;
            }
            int length = datagram.getLength();
            int first = length > 0 ? buffer[0] & 0xff : -1;
            if (first >= 20 && first <= 63) {
                records.deliver(Arrays.copyOf(buffer, length));
            } else if (first >= 128 && first <= 191 && length >= 12 && !isRtcp(buffer)) {
                receive(new RtpPacket(Arrays.copyOf(buffer, length), 0, length), received);
            }
        }
    }

    /** RTCP's packet types, 192 to 223, sit where RTP has its marker bit and payload type (RFC 5761). */
    private static boolean isRtcp(byte[] packet) {
        int type = packet[1] & 0xff;
        return type >= 192 && type <= 223;
    }

    private void receive(RtpPacket packet, Consumer<RtpPacket> received) {
        SrtpContextFactory keys = incomingKeys;
        if (keys == null) {
            return;
        }
        int ssrc = packet.ssrc();
        try {
            SrtpCryptoContext context = incoming.get(ssrc);
            boolean known = context != null;
            if (!known) {
                if (incoming.size() >= MAX_SOURCES) {
                    LOG.debugf("Dropped RTP from source %d: too many sources", Integer.toUnsignedLong(ssrc));
                    return;
                }
                context = keys.deriveContext(ssrc, 0);
            }
            SrtpErrorStatus status = context.reverseTransformPacket(packet, false);
            if (status != SrtpErrorStatus.OK) {
                LOG.debugf("Dropped RTP from source %d: %s", Integer.toUnsignedLong(ssrc), status);
                return;
            }
            // Kept only once a packet proves the source is the peer's, so others can't fill the map,
            // and capped, so the peer can't either.
            if (!known) {
                incoming.put(ssrc, context);
            }
        } catch (GeneralSecurityException e) {
            LOG.warnf(e, "Could not decrypt RTP from source %d", Integer.toUnsignedLong(ssrc));
            return;
        }
        received.accept(packet);
    }

    private void handshake(DtlsServer server) {
        DTLSTransport dtls;
        try {
            dtls = new DTLSServerProtocol().accept(server, records);
        } catch (IOException e) {
            ready.completeExceptionally(e);
            return;
        }
        SrtpKeys keys = server.keys();
        SrtpPolicy policy = policy(keys.profile());
        incomingKeys = new SrtpContextFactory(false, keys.peerKey(), keys.peerSalt(), policy, policy, SRTP_LOG);
        outgoingKeys = new SrtpContextFactory(true, keys.ownKey(), keys.ownSalt(), policy, policy, SRTP_LOG);
        ready.complete(null);
        LOG.debugf("SRTP keys agreed, %s", keys.profile());
        // Reading on resends the server's last flight if the peer missed it, and sees the peer close.
        byte[] ignored = new byte[DATAGRAM_LIMIT];
        try {
            while (true) {
                dtls.receive(ignored, 0, ignored.length, 0);
            }
        } catch (InterruptedIOException e) {
            if (closed) {
                try {
                    dtls.close();
                } catch (IOException alreadyGone) {
                    // The socket closed first; signalling tells the peer as well.
                }
            }
        } catch (IOException e) {
            LOG.debugf("DTLS ended: %s", e.getMessage());
        }
    }

    private static SrtpPolicy policy(Profile profile) {
        return switch (profile) {
            case AEAD_AES_128_GCM -> new SrtpPolicy(SrtpPolicy.AESGCM_ENCRYPTION, Profile.KEY_LENGTH,
                SrtpPolicy.NULL_AUTHENTICATION, 0, 16, profile.saltLength);
            case AES128_CM_HMAC_SHA1_80 -> new SrtpPolicy(SrtpPolicy.AESCM_ENCRYPTION, Profile.KEY_LENGTH,
                SrtpPolicy.HMACSHA1_AUTHENTICATION, 20, 10, profile.saltLength);
        };
    }

    /**
     * Stops handing on packets and, if the handshake is done, tells the
     * peer DTLS is closing. Reading ends when the ICE connection closes.
     */
    @Override
    public void close() {
        closed = true;
        ready.completeExceptionally(new IOException("SRTP closed"));
        records.close();
    }

    /** DTLS records between the reading thread and the handshake's. */
    private final class Records implements DatagramTransport {

        /** Unread records are a few at most, unless nobody is reading: then they are dropped. */
        private final BlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(16);
        private static final byte[] CLOSED = {};
        private volatile boolean closed;

        void deliver(byte[] record) {
            if (!closed) {
                queue.offer(record);
            }
        }

        @Override
        public int getReceiveLimit() {
            return DATAGRAM_LIMIT;
        }

        @Override
        public int getSendLimit() {
            return SEND_LIMIT;
        }

        /**
         * Waits for a record; zero waits for good. Once closed it throws an
         * InterruptedIOException, which BouncyCastle hands back as it is
         * rather than sending the peer an alert.
         */
        @Override
        public int receive(byte[] buffer, int offset, int length, int waitMillis) throws IOException {
            byte[] record;
            try {
                record = closed ? CLOSED
                    : waitMillis == 0 ? queue.take() : queue.poll(waitMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("DTLS interrupted");
            }
            if (record == CLOSED) {
                throw new InterruptedIOException("DTLS closed");
            }
            if (record == null) {
                return -1;
            }
            System.arraycopy(record, 0, buffer, offset, Math.min(length, record.length));
            return record.length;
        }

        @Override
        public void send(byte[] buffer, int offset, int length) throws IOException {
            path.socket().send(new DatagramPacket(buffer, offset, length, path.remote()));
        }

        /** Called by BouncyCastle when DTLS ends, and by the connection when it closes. */
        @Override
        public void close() {
            if (!closed) {
                closed = true;
                queue.clear();
                queue.offer(CLOSED);
            }
        }
    }
}
