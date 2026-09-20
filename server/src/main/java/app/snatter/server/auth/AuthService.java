package app.snatter.server.auth;

import app.snatter.server.account.Account;
import app.snatter.server.account.AccountRepository;
import app.snatter.server.api.ApiException;
import app.snatter.server.persistence.Ids;
import jakarta.enterprise.context.ApplicationScoped;
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
import java.util.UUID;
import org.jdbi.v3.core.statement.UnableToExecuteStatementException;

@ApplicationScoped
public class AuthService {

    /** Prefix on every token so leaked tokens are recognisable by secret scanners. */
    static final String TOKEN_PREFIX = "snt_";
    private static final int TOKEN_BYTES = 32;
    private static final String UNIQUE_VIOLATION = "23505";
    /** last_seen_at is written at most this often per session, to keep reads cheap. */
    private static final Duration TOUCH_INTERVAL = Duration.ofMinutes(5);

    private final SecureRandom random = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private final AccountRepository accounts;
    private final SessionRepository sessions;
    private final PasswordHasher hasher;
    private final AuthConfig config;

    public AuthService(AccountRepository accounts, SessionRepository sessions,
                       PasswordHasher hasher, AuthConfig config) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.hasher = hasher;
        this.config = config;
    }

    /** Result of a successful login or registration. */
    public record Login(Account account, String token, Instant expiresAt) {
    }

    /** A validated session together with its account. */
    public record Authenticated(Session session, Account account) {
    }

    @Transactional
    public Login register(String username, String password, String displayName, String ip, String userAgent) {
        if (accounts.usernameExists(username)) {
            throw ApiException.conflict("username_taken", "That username is already in use");
        }
        String name = displayName == null || displayName.isBlank() ? username : displayName.strip();
        Account account;
        try {
            account = accounts.createLocal(Ids.newId(), username, name, hasher.hash(password));
        } catch (UnableToExecuteStatementException e) {
            // Lost a race with a concurrent registration of the same username.
            if (e.getCause() instanceof java.sql.SQLException sql && UNIQUE_VIOLATION.equals(sql.getSQLState())) {
                throw ApiException.conflict("username_taken", "That username is already in use");
            }
            throw e;
        }
        return openSession(account, ip, userAgent);
    }

    @Transactional
    public Login login(String username, String password, String ip, String userAgent) {
        Optional<Account> account = accounts.findByUsername(username);
        Optional<String> hash = account.flatMap(a -> accounts.findPasswordHash(a.id()));
        if (hash.isEmpty() || !hasher.verify(password, hash.get())) {
            throw ApiException.unauthorized("invalid_credentials", "Unknown username or wrong password");
        }
        return openSession(account.get(), ip, userAgent);
    }

    @Transactional
    public void logout(UUID sessionId) {
        sessions.delete(sessionId);
    }

    /** Resolves a bearer token to its session and account, or empty if unknown or expired. */
    @Transactional
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
            sessions.delete(session.id());
            return Optional.empty();
        }
        if (session.lastSeenAt().plus(TOUCH_INTERVAL).isBefore(now)) {
            sessions.touch(session.id(), now);
        }
        return accounts.findById(session.accountId()).map(a -> new Authenticated(session, a));
    }

    private Login openSession(Account account, String ip, String userAgent) {
        byte[] raw = new byte[TOKEN_BYTES];
        random.nextBytes(raw);
        String token = TOKEN_PREFIX + B64.encodeToString(raw);
        Instant now = Instant.now();
        Session session = new Session(Ids.newId(), account.id(), now, now.plus(config.sessionLifetime()), now);
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
