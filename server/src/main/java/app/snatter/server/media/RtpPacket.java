package app.snatter.server.media;

import org.jitsi.utils.ByteArrayBuffer;

/**
 * An RTP packet in a byte array, which jitsi-srtp encrypts and decrypts in
 * place. Encrypting appends a tag; growing replaces the array with a larger
 * one when it has no room for it.
 */
public final class RtpPacket implements ByteArrayBuffer {

    private byte[] buffer;
    private int offset;
    private int length;

    public RtpPacket(byte[] buffer, int offset, int length) {
        this.buffer = buffer;
        this.offset = offset;
        this.length = length;
    }

    /** The source the packet says it comes from. Not encrypted, so readable either way. */
    public int ssrc() {
        return (buffer[offset + 8] & 0xff) << 24 | (buffer[offset + 9] & 0xff) << 16
            | (buffer[offset + 10] & 0xff) << 8 | buffer[offset + 11] & 0xff;
    }

    /** Sends it on as another source, as forwarding does: before encrypting, since it is authenticated. */
    public void setSsrc(int ssrc) {
        buffer[offset + 8] = (byte) (ssrc >>> 24);
        buffer[offset + 9] = (byte) (ssrc >>> 16);
        buffer[offset + 10] = (byte) (ssrc >>> 8);
        buffer[offset + 11] = (byte) ssrc;
    }

    @Override
    public byte[] getBuffer() {
        return buffer;
    }

    @Override
    public int getOffset() {
        return offset;
    }

    @Override
    public int getLength() {
        return length;
    }

    @Override
    public void setLength(int length) {
        this.length = length;
    }

    @Override
    public void setOffset(int offset) {
        this.offset = offset;
    }

    @Override
    public boolean isInvalid() {
        return false;
    }

    @Override
    public void readRegionToBuff(int offset, int length, byte[] destination) {
        System.arraycopy(buffer, this.offset + offset, destination, 0, length);
    }

    @Override
    public void grow(int howMuch) {
        if (offset + length + howMuch > buffer.length) {
            byte[] larger = new byte[length + howMuch];
            System.arraycopy(buffer, offset, larger, 0, length);
            buffer = larger;
            offset = 0;
        }
    }

    @Override
    public void append(byte[] data, int length) {
        grow(length);
        System.arraycopy(data, 0, buffer, offset + this.length, length);
        this.length += length;
    }

    @Override
    public void shrink(int howMuch) {
        length = Math.max(0, length - howMuch);
    }
}
