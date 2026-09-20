package app.snatter.server.settings;

import app.snatter.server.account.AccountId;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.transaction.Transactional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Keeps the current settings in memory so request paths never hit the
 * database for them. Every change goes through here and fires a
 * {@link Changed} event, which for example the rate limiter listens to.
 */
@ApplicationScoped
@Startup
public class ServerSettingsService {

    /** Fired after settings have been updated. */
    public record Changed(ServerSettings settings) {
    }

    private final ServerSettingsRepository repository;
    private final Event<Changed> changed;
    private final AtomicReference<ServerSettings> current = new AtomicReference<>();

    public ServerSettingsService(ServerSettingsRepository repository, Event<Changed> changed) {
        this.repository = repository;
        this.changed = changed;
        this.current.set(repository.get());
    }

    public ServerSettings current() {
        return current.get();
    }

    @Transactional
    public ServerSettings update(ServerSettings settings) {
        repository.update(settings);
        ServerSettings fresh = repository.get();
        current.set(fresh);
        changed.fire(new Changed(fresh));
        return fresh;
    }

    /** Called when an account is created on a server that has no owner yet. */
    @Transactional
    public boolean claimOwner(AccountId accountId) {
        boolean claimed = repository.claimOwner(accountId);
        if (claimed) {
            current.set(repository.get());
        }
        return claimed;
    }
}
