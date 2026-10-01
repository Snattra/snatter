package app.snatter.server.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import app.snatter.server.api.ApiException;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AltchaServiceTest {

    @Inject
    AltchaService challenges;

    /**
     * A solution checked before it expired can reach redemption after, when
     * the password hash in between is slow. Its record would be cleaned up as
     * soon as it was made, so letting it through would let it through again.
     */
    @Test
    void refusesToRedeemASolutionThatExpiredSinceItWasChecked() {
        AltchaService.Solved solved = new AltchaService.Solved(randomChallenge(), Instant.now().minusSeconds(1));

        for (int attempt = 0; attempt < 2; attempt++) {
            ApiException e = assertThrows(ApiException.class, () -> redeem(solved));
            assertEquals("challenge_invalid", e.code());
            assertEquals("Challenge has expired", e.getMessage());
        }
    }

    @Test
    void redeemsASolutionOnce() {
        AltchaService.Solved solved = new AltchaService.Solved(randomChallenge(), Instant.now().plusSeconds(60));

        redeem(solved);
        ApiException e = assertThrows(ApiException.class, () -> redeem(solved));
        assertEquals("Challenge has already been used", e.getMessage());
    }

    private void redeem(AltchaService.Solved solved) {
        QuarkusTransaction.requiringNew().run(() -> challenges.redeem(solved));
    }

    private static String randomChallenge() {
        byte[] bytes = new byte[32];
        ThreadLocalRandom.current().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
