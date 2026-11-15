package app.snatter.server.origin;

import app.snatter.server.common.Value;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A web app allowed to use the server from a browser, named by its origin as
 * browsers send it: {@code https://web.snatter.app}. A port of {@code *}
 * stands for every port of the host, so {@code http://localhost:*} covers
 * {@code http://localhost} and {@code http://localhost:5173} alike. Kept in
 * lower case, as browsers send origins. Implements {@link CharSequence} so
 * that the contract's pattern constraint can validate it.
 */
public record ClientOrigin(String value) implements Value<String>, CharSequence {

    private static final String ANY_PORT = ":*";
    private static final Pattern FORMAT =
        Pattern.compile("^[a-z][a-z0-9+.-]*://([a-z0-9.-]+|\\[[0-9a-f:.]+])(:([0-9]{1,5}|\\*))?$");

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public ClientOrigin {
        Objects.requireNonNull(value, "value");
        value = value.toLowerCase(Locale.ROOT);
        if (value.length() > 255 || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Not an origin: " + value);
        }
    }

    /** Whether a request's {@code Origin} header names this app. */
    public boolean allows(String origin) {
        String candidate = origin.toLowerCase(Locale.ROOT);
        if (!value.endsWith(ANY_PORT)) {
            return candidate.equals(value);
        }
        String host = value.substring(0, value.length() - ANY_PORT.length());
        if (candidate.equals(host)) {
            return true;
        }
        if (!candidate.startsWith(host + ":")) {
            return false;
        }
        String port = candidate.substring(host.length() + 1);
        return !port.isEmpty() && port.length() <= 5 && port.chars().allMatch(Character::isDigit);
    }

    @Override
    @JsonValue
    public String value() {
        return value;
    }

    @Override
    public int length() {
        return value.length();
    }

    @Override
    public char charAt(int index) {
        return value.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return value.subSequence(start, end);
    }

    @Override
    public String toString() {
        return value;
    }
}
