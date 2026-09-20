package app.snatter.server.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void verifiesCorrectPasswordAndRejectsWrongOne() {
        String hash = hasher.hash("correct horse battery staple");
        assertTrue(hasher.verify("correct horse battery staple", hash));
        assertFalse(hasher.verify("correct horse battery stapl", hash));
        assertFalse(hasher.verify("", hash));
    }

    @Test
    void producesPhcFormatWithFreshSaltEachTime() {
        String a = hasher.hash("secret");
        String b = hasher.hash("secret");
        assertTrue(a.startsWith("$argon2id$v=19$m=65536,t=3,p=1$"), a);
        assertNotEquals(a, b, "salt must differ between hashes");
    }

    @Test
    void verifiesHashesWithDifferentEmbeddedParameters() {
        // Same password, cheaper parameters: must still verify because params come from the hash.
        String cheap = "$argon2id$v=19$m=1024,t=1,p=1$c2FsdHNhbHRzYWx0c2Fs$" + hashWith("secret", 1024, 1);
        assertTrue(hasher.verify("secret", cheap));
    }

    @Test
    void rejectsMalformedHashes() {
        assertFalse(hasher.verify("secret", "not-a-hash"));
        assertFalse(hasher.verify("secret", "$bcrypt$something"));
        assertFalse(hasher.verify("secret", "$argon2id$v=19$m=1,x=2$AAAA$AAAA"));
    }

    private static String hashWith(String password, int memoryKb, int iterations) {
        var params = new org.bouncycastle.crypto.params.Argon2Parameters.Builder(
                org.bouncycastle.crypto.params.Argon2Parameters.ARGON2_id)
            .withVersion(org.bouncycastle.crypto.params.Argon2Parameters.ARGON2_VERSION_13)
            .withMemoryAsKB(memoryKb)
            .withIterations(iterations)
            .withParallelism(1)
            .withSalt("saltsaltsaltsal".getBytes())
            .build();
        var gen = new org.bouncycastle.crypto.generators.Argon2BytesGenerator();
        gen.init(params);
        byte[] out = new byte[PasswordHasher.HASH_BYTES];
        gen.generateBytes(password.getBytes(), out);
        return java.util.Base64.getEncoder().withoutPadding().encodeToString(out);
    }
}
