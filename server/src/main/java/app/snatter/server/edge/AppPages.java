package app.snatter.server.edge;

import app.snatter.server.invite.InviteCode;
import app.snatter.server.settings.ServerSettingsService;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.net.URI;
import java.util.Locale;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * The pages a server standing alone answers outside the API: its own address,
 * and its invite links, {@code /invite/{code}} (as {@code InviteResource}
 * makes them), both sent on to the web app, {@code snatter.web-app}
 * (SNATTER_WEB_APP), web.snatter.app unless set. An invite goes on as
 * {@code /invite#{this server}/{code}}, for the app to add this server and
 * sign up with the invite. Where an app is served in front of the server at
 * its address, the app answers these paths itself and they never come here.
 */
@ApplicationScoped
public class AppPages {

    private final String webApp;
    private final ServerSettingsService settings;

    AppPages(@ConfigProperty(name = "snatter.web-app", defaultValue = "https://web.snatter.app") String webApp,
             ServerSettingsService settings) {
        this.webApp = checked(webApp);
        this.settings = settings;
    }

    /** An http(s) address with nothing after its host and port, trailing slash ignored. */
    static String checked(String webApp) {
        String trimmed = webApp.strip().replaceAll("/+$", "");
        URI uri = URI.create(trimmed);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!(scheme.equals("https") || scheme.equals("http")) || uri.getRawAuthority() == null
            || !(uri.getRawPath() == null || uri.getRawPath().isEmpty()) || uri.getRawQuery() != null) {
            throw new IllegalArgumentException("snatter.web-app is not the address of a web app: " + webApp);
        }
        return trimmed;
    }

    void install(@Observes Router router) {
        router.get("/").handler(context -> redirect(context, webApp + "/"));
        router.get("/invite/:code").handler(this::invite);
    }

    private void invite(RoutingContext context) {
        InviteCode code;
        try {
            code = new InviteCode(context.pathParam("code"));
        } catch (IllegalArgumentException e) {
            context.response().setStatusCode(404).end();
            return;
        }
        redirect(context, webApp + "/invite#" + ownAddress(context) + "/" + code);
    }

    /**
     * This server as members reach it, as the web app takes addresses: the
     * host and port of its public URL, or else of the address the request was
     * sent to.
     */
    private String ownAddress(RoutingContext context) {
        String publicUrl = settings.current().publicUrl();
        if (publicUrl != null) {
            String authority = URI.create(publicUrl).getRawAuthority();
            if (authority != null) {
                return authority;
            }
        }
        return context.request().getHeader(HttpHeaders.HOST);
    }

    private static void redirect(RoutingContext context, String location) {
        context.response().setStatusCode(302).putHeader(HttpHeaders.LOCATION, location).end();
    }
}
