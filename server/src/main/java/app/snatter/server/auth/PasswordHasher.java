package app.snatter.server.auth;

import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

/**
 * Argon2id password hashing, stored in PHC string format:
 * {@code $argon2id$v=19$m=65536,t=3,p=1$<salt>$<hash>}.
 *
 * <p>The parameters are embedded in each hash, so they can be raised later
 * and old hashes remain verifiable.
 */
@ApplicationScoped
public class PasswordHasher {

    static final int MEMORY_KB = 64 * 1024;
    static final int ITERATIONS = 3;
    static final int PARALLELISM = 1;
    static final int SALT_BYTES = 16;
    static final int HASH_BYTES = 32;

    private static final Base64.Encoder B64 = Base64.getEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getDecoder();
    // Instance field on purpose: a static SecureRandom cannot live in a GraalVM image heap.
    private final SecureRandom random = new SecureRandom();

    public String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] hash = argon2(password, salt, MEMORY_KB, ITERATIONS, PARALLELISM);
        return "$argon2id$v=19$m=" + MEMORY_KB + ",t=" + ITERATIONS + ",p=" + PARALLELISM
            + "$" + B64.encodeToString(salt) + "$" + B64.encodeToString(hash);
    }

    public boolean verify(String password, String encoded) {
        // $argon2id$v=19$m=..,t=..,p=..$salt$hash  ->  ["", "argon2id", "v=19", "m=..,t=..,p=..", salt, hash]
        String[] parts = encoded.split("\\$");
        if (parts.length != 6 || !parts[1].equals("argon2id") || !parts[2].equals("v=19")) {
            return false;
        }
        int memory = -1;
        int iterations = -1;
        int parallelism = -1;
        for (String kv : parts[3].split(",")) {
            int eq = kv.indexOf('=');
            if (eq < 0) {
                return false;
            }
            int value = Integer.parseInt(kv.substring(eq + 1));
            switch (kv.substring(0, eq)) {
                case "m" -> memory = value;
                case "t" -> iterations = value;
                case "p" -> parallelism = value;
                default -> {
                    return false;
                }
            }
        }
        if (memory < 0 || iterations < 0 || parallelism < 0) {
            return false;
        }
        byte[] salt = B64D.decode(parts[4]);
        byte[] expected = B64D.decode(parts[5]);
        byte[] actual = argon2(password, salt, memory, iterations, parallelism);
        return MessageDigest.isEqual(expected, actual);
    }

    private static byte[] argon2(String password, byte[] salt, int memoryKb, int iterations, int parallelism) {
        Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withMemoryAsKB(memoryKb)
            .withIterations(iterations)
            .withParallelism(parallelism)
            .withSalt(salt)
            .build();
        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(params);
        byte[] out = new byte[HASH_BYTES];
        generator.generateBytes(password.getBytes(StandardCharsets.UTF_8), out);
        return out;
    }
}
