package app.snatter.server.protocol;

import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A protocol version, {@code major.minor}: minor versions only add, major versions break. */
public record ProtocolVersion(int major, int minor) implements Comparable<ProtocolVersion> {

    private static final Pattern FORMAT = Pattern.compile("(\\d{1,9})\\.(\\d{1,9})");
    private static final Comparator<ProtocolVersion> ORDER =
        Comparator.comparingInt(ProtocolVersion::major).thenComparingInt(ProtocolVersion::minor);

    /** The version as the contract writes it, for example {@code 1.0}; empty if it is not one. */
    public static Optional<ProtocolVersion> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher m = FORMAT.matcher(text);
        if (!m.matches()) {
            return Optional.empty();
        }
        return Optional.of(new ProtocolVersion(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))));
    }

    public boolean isOlderThan(ProtocolVersion other) {
        return compareTo(other) < 0;
    }

    @Override
    public int compareTo(ProtocolVersion other) {
        return ORDER.compare(this, other);
    }

    @Override
    public String toString() {
        return major + "." + minor;
    }
}
