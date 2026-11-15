package app.snatter.server.origin;

import app.snatter.server.common.Value;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A web app allowed to use the server from a browser, named by its origin as
 * browsers send it: {@code https://web.snatter.app}. A port of {@code *}
 * stands for every port of the host, so {@code http://localhost:*} covers
 * {@code http://localhost} and {@code http://localhost:5173} alike. Kept as
 * browsers send origins: in lower case, and without a port that is the
 * scheme's own, 443 for https and 80 for http.
 */
public record ClientOrigin(String value) implements Value<String> {

    private static final String ANY_PORT = ":*";
    private static final Pattern FORMAT =
        Pattern.compile("^[a-z][a-z0-9+.-]*://([a-z0-9.-]+|\\[[0-9a-f:.]+])(:([0-9]{1,5}|\\*))?$");

    public ClientOrigin {
        Objects.requireNonNull(value, "value");
        value = value.toLowerCase(Locale.ROOT);
        if (value.length() > 255 || !FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Not an origin: " + value);
        }
        value = withoutDefaultPort(value);
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

    /** The origin or URL with its port left out if it is the scheme's own. */
    static String withoutDefaultPort(String url) {
        int scheme = url.indexOf("://");
        if (scheme < 0) {
            return url;
        }
        String own = switch (url.substring(0, scheme).toLowerCase(Locale.ROOT)) {
            case "http" -> ":80";
            case "https" -> ":443";
            default -> null;
        };
        int end = url.indexOf('/', scheme + 3);
        String authority = end < 0 ? url.substring(scheme + 3) : url.substring(scheme + 3, end);
        if (own == null || !authority.endsWith(own)) {
            return url;
        }
        int cut = scheme + 3 + authority.length() - own.length();
        return url.substring(0, cut) + url.substring(cut + own.length());
    }

    @Override
    public String toString() {
        return value;
    }
}
