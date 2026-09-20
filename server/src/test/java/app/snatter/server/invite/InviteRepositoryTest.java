package app.snatter.server.invite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

@QuarkusTest
class InviteRepositoryTest {

    @Inject
    InviteRepository invites;

    private final SecureRandom random = new SecureRandom();

    @Test
    @Transactional
    void expiryIsCheckedAgainstTheGivenClock() {
        Instant now = Instant.now();
        Invite invite = new Invite(InviteCode.random(random), null, now, now.plus(Duration.ofMinutes(1)), null, 0, null);
        assertTrue(invites.insert(invite));

        assertTrue(invites.redeem(invite.code(), now).isPresent());
        assertFalse(invites.redeem(invite.code(), now.plus(Duration.ofMinutes(2))).isPresent(), "expired");
        assertEquals(1, invites.find(invite.code()).orElseThrow().uses());
    }

    @Test
    @Transactional
    void useLimitIsAtomic() {
        Instant now = Instant.now();
        Invite invite = new Invite(InviteCode.random(random), null, now, null, 2, 0, null);
        assertTrue(invites.insert(invite));

        assertEquals(0, invites.redeem(invite.code(), now).orElseThrow().uses());
        assertEquals(1, invites.redeem(invite.code(), now).orElseThrow().uses());
        assertFalse(invites.redeem(invite.code(), now).isPresent(), "exhausted");
        assertTrue(invites.find(invite.code()).orElseThrow().isExhausted());
    }

    @Test
    @Transactional
    void duplicateCodesAreRejectedNotOverwritten() {
        Instant now = Instant.now();
        Invite invite = new Invite(InviteCode.random(random), null, now, null, null, 0, null);
        assertTrue(invites.insert(invite));
        assertFalse(invites.insert(invite));
    }
}
