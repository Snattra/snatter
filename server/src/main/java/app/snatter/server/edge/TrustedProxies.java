package app.snatter.server.edge;

import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory;
import io.smallrye.config.ConfigValue;
import io.smallrye.config.PropertiesConfigSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * Which proxies the server believes about the client: those that
 * {@code snatter.trusted-proxies} (SNATTER_TRUSTED_PROXIES) names, as IP
 * addresses or CIDR ranges separated by commas, and no others. Only then does
 * it read {@code X-Forwarded-For}, {@code -Proto} and {@code -Host}, from
 * those proxies alone. With none named it reads no forwarded header from
 * anyone, so a client cannot choose the address rate limits count it by.
 *
 * <p>Registered in META-INF/services. Its settings rank below explicit ones,
 * so a {@code QUARKUS_HTTP_PROXY_*} variable still wins.
 */
public class TrustedProxies implements ConfigSourceFactory {

    static final String PROPERTY = "snatter.trusted-proxies";
    private static final int ORDINAL = 200;

    @Override
    public Iterable<ConfigSource> getConfigSources(ConfigSourceContext context) {
        ConfigValue value = context.getValue(PROPERTY);
        return settingsFor(value == null ? null : value.getValue())
            .<Iterable<ConfigSource>>map(settings -> List.of(new PropertiesConfigSource(settings, "snatter trusted proxies", ORDINAL)))
            .orElse(List.of());
    }

    /** Quarkus's proxy settings for the proxies named, or none when none are. */
    static Optional<Map<String, String>> settingsFor(String proxies) {
        if (proxies == null || proxies.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Map.of(
            "quarkus.http.proxy.proxy-address-forwarding", "true",
            "quarkus.http.proxy.allow-x-forwarded", "true",
            // The address the page is at, with its port, which the server
            // needs to know its own pages (origin.ClientOriginFilter).
            "quarkus.http.proxy.enable-forwarded-host", "true",
            "quarkus.http.proxy.trusted-proxies", proxies.strip()));
    }
}
