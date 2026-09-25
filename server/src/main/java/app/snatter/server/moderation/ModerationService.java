package app.snatter.server.moderation;

import app.snatter.server.account.Account;
import app.snatter.server.account.AccountEvent;
import app.snatter.server.account.AccountId;
import app.snatter.server.account.AccountRepository;
import app.snatter.server.auth.AccountPrincipal;
import app.snatter.server.auth.SessionRepository;
import app.snatter.server.role.RoleService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Bans and timeouts. Both need their permission, checked by the caller, and
 * follow the role rule through {@link RoleService#requireOutranks}: only
 * members whose permissions the actor all holds, never the owner or oneself.
 *
 * <p>Banning ends the member's sessions in the same transaction, and the
 * session lookup refuses banned accounts, so a ban takes effect at once. A
 * timeout keeps the member's roles and sessions but resolves their
 * permissions to none until it ends.
 */
@ApplicationScoped
public class ModerationService {

    private final BanRepository bans;
    private final AccountRepository accounts;
    private final SessionRepository sessions;
    private final RoleService roles;
    private final Event<AccountEvent> events;

    public ModerationService(BanRepository bans, AccountRepository accounts, SessionRepository sessions, RoleService roles,
                             Event<AccountEvent> events) {
        this.bans = bans;
        this.accounts = accounts;
        this.sessions = sessions;
        this.roles = roles;
        this.events = events;
    }

    public List<Ban> bans() {
        return bans.findAll();
    }

    @Transactional
    public Ban ban(AccountPrincipal actor, AccountId accountId, String reason) {
        roles.requireOutranks(actor, accountId);
        Ban ban = new Ban(accountId, reason == null || reason.isBlank() ? null : reason.strip(), actor.accountId(), Instant.now());
        bans.save(ban);
        sessions.deleteByAccount(accountId);
        events.fire(new AccountEvent.Banned(accountId, actor.accountId()));
        return ban;
    }

    @Transactional
    public void unban(AccountId accountId) {
        bans.delete(accountId);
    }

    /** Starts a timeout, or replaces the running one, lasting {@code duration} from now. */
    @Transactional
    public Account timeOut(AccountPrincipal actor, AccountId accountId, Duration duration) {
        roles.requireOutranks(actor, accountId);
        accounts.setTimedOutUntil(accountId, Instant.now().plus(duration));
        events.fire(new AccountEvent.TimeoutChanged(accountId, actor.accountId()));
        return accounts.findById(accountId).orElseThrow();
    }

    @Transactional
    public void endTimeout(AccountPrincipal actor, AccountId accountId) {
        roles.requireOutranks(actor, accountId);
        accounts.setTimedOutUntil(accountId, null);
        events.fire(new AccountEvent.TimeoutChanged(accountId, actor.accountId()));
    }
}
