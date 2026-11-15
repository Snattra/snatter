package app.snatter.server.origin;

import app.snatter.server.settings.ServerSettings;
import app.snatter.server.settings.ServerSettingsService;
import io.quarkus.vertx.http.runtime.RouteConstants;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.net.URI;
import java.util.Locale;

/**
 * Which web pages may use the API from a browser: those of the server's own
 * address, and the apps in {@link ServerSettings#clientOrigins()}. For them
 * the API answers CORS requests; browsers keep every other page from reading
 * its answers. The gateway is not covered by CORS, so its handshake from any
 * other page is refused here. Requests without an {@code Origin}, from
 * programs that are not web pages, pass untouched.
 */
@ApplicationScoped
public class ClientOriginFilter {

    static final String GATEWAY_PATH = "/api/v1/gateway";
    private static final String METHODS = "GET, POST, PUT, PATCH, DELETE";
    private static final String HEADERS = "Authorization, Content-Type";
    /** Answers a client may read besides the plain ones: how long to wait after a rate limit. */
    private static final String EXPOSED = "Retry-After";
    private static final String PREFLIGHT_MAX_AGE_SECONDS = "600";

    private final ServerSettingsService settings;

    public ClientOriginFilter(ServerSettingsService settings) {
        this.settings = settings;
    }

    /** Ahead of the API, security and the gateway, so preflights end here. */
    void install(@Observes Router router) {
        router.route("/api/*").order(RouteConstants.ROUTE_ORDER_UPLOAD_LIMIT - 1).handler(this::handle);
    }

    void handle(RoutingContext context) {
        HttpServerRequest request = context.request();
        HttpServerResponse response = context.response();
        // Also on answers without CORS headers, so that a browser does not
        // hand an answer it kept from a plain request to a page that needs them.
        response.headers().add(HttpHeaders.VARY, "Origin");
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null) {
            context.next();
            return;
        }
        boolean allowed = allows(origin, request);

        if (isHandshake(context)) {
            if (allowed) {
                context.next();
            } else {
                response.setStatusCode(403).end();
            }
            return;
        }
        boolean preflight = request.method() == HttpMethod.OPTIONS
            && request.getHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD) != null;
        if (preflight) {
            if (allowed) {
                response.putHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin)
                    .putHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, METHODS)
                    .putHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, HEADERS)
                    .putHeader(HttpHeaders.ACCESS_CONTROL_MAX_AGE, PREFLIGHT_MAX_AGE_SECONDS)
                    .setStatusCode(204);
            } else {
                response.setStatusCode(403);
            }
            response.end();
            return;
        }
        if (allowed) {
            response.putHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin)
                .putHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, EXPOSED);
        }
        context.next();
    }

    /**
     * Whether the request opens a WebSocket, which CORS does not cover: any
     * upgrade to one, and anything for the gateway's path as routing sees it,
     * decoded, so that {@code /api/v1/%67ateway} counts too.
     */
    private static boolean isHandshake(RoutingContext context) {
        return "websocket".equalsIgnoreCase(context.request().getHeader(HttpHeaders.UPGRADE))
            || GATEWAY_PATH.equals(context.normalizedPath());
    }

    private boolean allows(String origin, HttpServerRequest request) {
        ServerSettings current = settings.current();
        return isOwnAddress(origin, request.getHeader(HttpHeaders.HOST), current.publicUrl())
            || current.clientOrigins().stream().anyMatch(app -> app.allows(origin));
    }

    /**
     * Whether the origin is the server's own address: the one the request was
     * sent to, as its {@code Host} header has it, or the public address in the
     * settings. Only host and port are compared, since behind a proxy that
     * terminates TLS the server sees plain HTTP while the page has HTTPS.
     * Behind a trusted proxy the {@code Host} header is the one the proxy
     * forwards as {@code X-Forwarded-Host}.
     */
    static boolean isOwnAddress(String origin, String requestedHost, String publicUrl) {
        String authority = authorityOf(origin);
        if (authority == null) {
            return false;
        }
        if (requestedHost != null && authority.equals(requestedHost.toLowerCase(Locale.ROOT))) {
            return true;
        }
        if (publicUrl != null) {
            String own = authorityOf(publicUrl);
            return authority.equals(own);
        }
        return false;
    }

    /** The host and port of an origin or URL, lower case and without the scheme's own port, or null if it has none. */
    private static String authorityOf(String url) {
        try {
            String authority = URI.create(ClientOrigin.withoutDefaultPort(url)).getRawAuthority();
            return authority == null ? null : authority.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
