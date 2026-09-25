package app.snatter.server.settings;

import app.snatter.server.account.AccountId;
import app.snatter.server.account.AccountRepository;
import app.snatter.server.api.ApiException;
import app.snatter.server.channel.Channel;
import app.snatter.server.channel.ChannelEvent;
import app.snatter.server.channel.ChannelRepository;
import app.snatter.server.role.RoleRepository;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.transaction.Transactional;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Keeps the current settings in memory so request paths never hit the
 * database for them. Every change goes through here and fires a
 * {@link Changed} event, which for example the rate limiter listens to.
 */
@ApplicationScoped
@Startup
public class ServerSettingsService {

    /** Fired inside the transaction that updated the settings. */
    public record Changed(ServerSettings before, ServerSettings after, AccountId actor) {
    }

    private final ServerSettingsRepository repository;
    private final ChannelRepository channels;
    private final RoleRepository roles;
    private final AccountRepository accounts;
    private final Event<Changed> changed;
    private final AtomicReference<ServerSettings> current = new AtomicReference<>();

    public ServerSettingsService(ServerSettingsRepository repository, ChannelRepository channels, RoleRepository roles,
                                 AccountRepository accounts, Event<Changed> changed) {
        this.repository = repository;
        this.channels = channels;
        this.roles = roles;
        this.accounts = accounts;
        this.changed = changed;
        this.current.set(repository.get());
    }

    public ServerSettings current() {
        return current.get();
    }

    /**
     * A fresh server: no owner and no accounts yet. The next registration
     * needs neither an invite nor a challenge and makes its account the owner.
     */
    public boolean setupRequired() {
        return current().ownerId() == null && accounts.count() == 0;
    }

    @Transactional
    public ServerSettings update(AccountId actor, ServerSettings settings) {
        ServerSettings before = current();
        if (settings.systemChannelId() != null && !settings.systemChannelId().equals(before.systemChannelId())) {
            Channel channel = channels.find(settings.systemChannelId())
                .orElseThrow(() -> ApiException.badRequest("channel_not_found", "No such channel"));
            if (!channel.type().hasMessages()) {
                throw ApiException.badRequest("voice_only_channel", "Notices need a channel with messages");
            }
        }
        if (settings.newMemberRoleId() != null && roles.find(settings.newMemberRoleId()).isEmpty()) {
            throw ApiException.badRequest("role_not_found", "No such role");
        }
        repository.update(settings);
        ServerSettings fresh = repository.get();
        current.set(fresh);
        changed.fire(new Changed(before, fresh, actor));
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

    /** Deleting the system channel clears the setting in the database; follow suit. */
    void onChannelDeleted(@Observes(during = TransactionPhase.AFTER_SUCCESS) ChannelEvent.Deleted deleted) {
        if (Objects.equals(deleted.channelId(), current().systemChannelId())) {
            current.set(repository.get());
        }
    }
}
