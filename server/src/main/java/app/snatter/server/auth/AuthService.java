package app.snatter.server.auth;

import app.snatter.server.account.Account;
import app.snatter.server.account.AccountEvent;
import app.snatter.server.account.AccountId;
import app.snatter.server.account.AccountRepository;
import app.snatter.server.account.DisplayNames;
import app.snatter.server.api.ApiException;
import app.snatter.server.moderation.Ban;
import app.snatter.server.moderation.BanRepository;
import app.snatter.server.moderation.BannedException;
import app.snatter.server.persistence.SqlErrors;
import app.snatter.server.invite.Invite;
import app.snatter.server.invite.InviteCode;
import app.snatter.server.invite.InviteService;
import app.snatter.server.role.RoleId;
import app.snatter.server.role.RoleRepository;
import app.snatter.server.settings.RegistrationMode;
import app.snatter.server.settings.ServerSettings;
import app.snatter.server.settings.ServerSettingsService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.jdbi.v3.core.statement.UnableToExecuteStatementException;

@ApplicationScoped
public class AuthService {

    /** Prefix on every token so leaked tokens are recognisable by secret scanners. */
    static final String TOKEN_PREFIX = "snt_";
    private static final int TOKEN_BYTES = 32;
    /** A session's use (last_seen_at, and with it expires_at) is written at most this often, to keep reads cheap. */
    private static final Duration TOUCH_INTERVAL = Duration.ofMinutes(5);

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private final AccountRepository accounts;
    private final SessionRepository sessions;
    private final PasswordHasher hasher;
    private final AltchaService challenges;
    private final InviteService invites;
    private final ServerSettingsService settings;
    private final RoleRepository roles;
    private final BanRepository bans;
    private final Event<AccountEvent> events;
    private final Event<SessionEvent> sessionEvents;
    private final SecureRandom random = new SecureRandom();

    public AuthService(AccountRepository accounts, SessionRepository sessions, PasswordHasher hasher,
                       AltchaService challenges, InviteService invites, ServerSettingsService settings, RoleRepository roles,
                       BanRepository bans, Event<AccountEvent> events, Event<SessionEvent> sessionEvents) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.hasher = hasher;
        this.challenges = challenges;
        this.invites = invites;
        this.settings = settings;
        this.roles = roles;
        this.bans = bans;
        this.events = events;
        this.sessionEvents = sessionEvents;
    }

    /** What a new user submits. {@code displayName}, {@code altcha} and {@code inviteCode} may be null. */
    public record Registration(String username, String password, String displayName, String altcha, InviteCode inviteCode) {
    }

    /** Result of a successful login or registration. */
    public record Login(Account account, String token, Instant expiresAt) {
    }

    /** A validated session together with its account. */
    public record Authenticated(Session session, Account account) {
    }

    /**
     * Creates a local account. The first account on a fresh server is always
     * allowed and becomes the server owner; after that the registration mode
     * and challenge policy from the server settings apply.
     *
     * <p>The checks and the deliberately slow password hash come first; only
     * the writes run in a transaction, as every transaction holds SQLite's
     * write lock (see {@code JdbiProducer}).
     */
    public Login register(Registration registration, String ip, String userAgent) {
        // Checked first, so a bad name spends no invite use or challenge.
        String displayName = DisplayNames.normalize(registration.displayName());
        boolean firstAccount = settings.setupRequired();
        AltchaService.Solved solved = firstAccount ? null : admit(registration);
        if (accounts.usernameExists(registration.username())) {
            throw usernameTaken();
        }
        String passwordHash = hasher.hash(registration.password());
        String name = displayName == null ? registration.username() : displayName;
        return createAccount(registration, name, passwordHash, firstAccount, solved, ip, userAgent);
    }

    /**
     * Applies the registration mode and challenge policy to a registration
     * that is not the server's first, and returns its solved challenge to
     * redeem, if one is required.
     */
    private AltchaService.Solved admit(Registration registration) {
        ServerSettings policy = settings.current();
        if (registration.inviteCode() != null) {
            invites.requireUsable(registration.inviteCode());
        } else if (policy.registrationMode() != RegistrationMode.OPEN) {
            throw new ApiException(403, "registration_closed", "Registration on this server requires an invite");
        }
        if (!policy.challengeRequired()) {
            return null;
        }
        if (registration.altcha() == null || registration.altcha().isBlank()) {
            throw ApiException.badRequest("challenge_required", "Solve a challenge from /api/v1/auth/challenge first");
        }
        return challenges.check(registration.altcha());
    }

    /** The writes of a registration: all of them happen, or none, so a failure spends no invite use or challenge. */
    @Transactional
    Login createAccount(Registration registration, String name, String passwordHash, boolean firstAccount,
                        AltchaService.Solved solved, String ip, String userAgent) {
        boolean first = firstAccount;
        AltchaService.Solved challenge = solved;
        if (first && !settings.setupRequired()) {
            // Another registration became the first account meanwhile; this one is judged like any other.
            first = false;
            challenge = admit(registration);
        }
        Invite invite = !first && registration.inviteCode() != null ? invites.redeem(registration.inviteCode()) : null;
        if (challenge != null) {
            challenges.redeem(challenge);
        }
        Account account;
        try {
            account = accounts.createLocal(AccountId.newId(), registration.username(), name, passwordHash);
        } catch (UnableToExecuteStatementException e) {
            // Lost a race with a concurrent registration of the same username.
            if (SqlErrors.isUniqueViolation(e)) {
                throw usernameTaken();
            }
            throw e;
        }
        if (first) {
            settings.claimOwner(account.id());
        }
        if (invite != null) {
            accounts.linkInvite(account.id(), invite.code().value(), invite.createdBy());
        }
        RoleId newMemberRole = settings.current().newMemberRoleId();
        if (newMemberRole != null) {
            roles.assign(account.id(), newMemberRole);
            account = accounts.findById(account.id()).orElseThrow();
        }
        events.fire(new AccountEvent.Registered(account.id()));
        return openSession(account, ip, userAgent);
    }

    private static ApiException usernameTaken() {
        return ApiException.conflict("username_taken", "That username is already in use");
    }

    /** Checks the password, which is deliberately slow, before the transaction that opens the session. */
    public Login login(String username, String password, String ip, String userAgent) {
        Optional<Account> account = accounts.findByUsername(username);
        Optional<String> hash = account.flatMap(a -> accounts.findPasswordHash(a.id()));
        if (hash.isEmpty() || !hasher.verify(password, hash.get())) {
            throw ApiException.unauthorized("invalid_credentials", "Unknown username or wrong password");
        }
        // Only after the password checks out, so the answer tells a guesser nothing.
        Optional<Ban> ban = bans.find(account.get().id());
        if (ban.isPresent()) {
            throw new BannedException(ban.get());
        }
        return openSession(account.get(), ip, userAgent);
    }

    @Transactional
    public void logout(AccountId accountId, SessionId sessionId) {
        if (sessions.delete(sessionId)) {
            sessionEvents.fire(new SessionEvent.Ended(sessionId, accountId));
        }
    }

    /**
     * Keeps a session in use without a request, as an open gateway connection
     * does: moves its expiry like a request would. Returns false if the
     * session has ended.
     */
    @Transactional
    public boolean keepAlive(SessionId sessionId) {
        Instant now = Instant.now();
        return sessions.touch(sessionId, now, now.plus(settings.current().sessionLifetime()));
    }

    /**
     * Resolves a bearer token to its session and account, or empty if unknown
     * or expired. Every request comes through here, so it only reads, and
     * takes a transaction just for the rare writes.
     */
    public Optional<Authenticated> authenticate(String token) {
        if (!token.startsWith(TOKEN_PREFIX)) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        Optional<Session> found = sessions.findByTokenHash(hashToken(token));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Session session = found.get();
        if (session.isExpired(now)) {
            logout(session.accountId(), session.id());
            return Optional.empty();
        }
        // Sessions expire after a period without use, so each use moves the expiry.
        if (session.lastSeenAt().plus(TOUCH_INTERVAL).isBefore(now)) {
            keepAlive(session.id());
        }
        return accounts.findById(session.accountId()).map(a -> new Authenticated(session, a));
    }

    /** Opens a session for the account, in the caller's transaction or its own. Not private, so that applies. */
    @Transactional
    Login openSession(Account account, String ip, String userAgent) {
        byte[] raw = new byte[TOKEN_BYTES];
        random.nextBytes(raw);
        String token = TOKEN_PREFIX + B64.encodeToString(raw);
        Instant now = Instant.now();
        Session session = new Session(SessionId.newId(), account.id(), now, now.plus(settings.current().sessionLifetime()), now);
        sessions.insert(session, hashToken(token), ip, truncate(userAgent, 255));
        return new Login(account, token, session.expiresAt());
    }

    static String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
