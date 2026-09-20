package app.snatter.server.auth;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.UnableToExecuteStatementException;

@ApplicationScoped
public class UsedChallengeRepository {

    private static final String UNIQUE_VIOLATION = "23505";

    private final Jdbi jdbi;

    public UsedChallengeRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    /** Records a challenge as used. Returns false if it had already been used. */
    public boolean markUsed(String challenge, Instant expiresAt) {
        try {
            jdbi.useHandle(h -> h
                .createUpdate("INSERT INTO used_challenge (challenge, expires_at) VALUES (:challenge, :expiresAt)")
                .bind("challenge", challenge)
                .bind("expiresAt", expiresAt)
                .execute());
            return true;
        } catch (UnableToExecuteStatementException e) {
            if (e.getCause() instanceof java.sql.SQLException sql && UNIQUE_VIOLATION.equals(sql.getSQLState())) {
                return false;
            }
            throw e;
        }
    }

    public int deleteExpired(Instant now) {
        return jdbi.withHandle(h -> h
            .createUpdate("DELETE FROM used_challenge WHERE expires_at <= :now")
            .bind("now", now)
            .execute());
    }
}
