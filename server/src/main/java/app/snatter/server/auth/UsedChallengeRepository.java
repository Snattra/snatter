package app.snatter.server.auth;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import org.jdbi.v3.core.Jdbi;

@ApplicationScoped
public class UsedChallengeRepository {

    private final Jdbi jdbi;

    public UsedChallengeRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    /** Records a challenge as used. Returns false if it had already been used. */
    public boolean markUsed(String challenge, Instant expiresAt) {
        return jdbi.withHandle(h -> h
            .createUpdate("""
                INSERT INTO used_challenge (challenge, expires_at) VALUES (:challenge, :expiresAt)
                ON CONFLICT (challenge) DO NOTHING
                """)
            .bind("challenge", challenge)
            .bind("expiresAt", expiresAt)
            .execute()) == 1;
    }

    public int deleteExpired(Instant now) {
        return jdbi.withHandle(h -> h
            .createUpdate("DELETE FROM used_challenge WHERE expires_at <= :now")
            .bind("now", now)
            .execute());
    }
}
