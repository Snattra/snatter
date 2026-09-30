package app.snatter.server.auth;

import app.snatter.server.api.ApiException;
import app.snatter.server.settings.ServerSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Proof-of-work challenges in the <a href="https://altcha.org">ALTCHA</a>
 * format. The server issues {@code challenge = SHA-256(salt + number)} for a
 * secret random number and signs the challenge with an HMAC key that lives
 * only in memory, so restarting the server invalidates outstanding challenges
 * but nothing needs to be stored when issuing them. Redeemed challenges are
 * recorded so a solution cannot be replayed.
 */
@ApplicationScoped
public class AltchaService {

    /** An issued challenge, in the shape clients expect. */
    public record Challenge(String algorithm, String challenge, String salt, String signature, int maxnumber) {
    }

    private static final String ALGORITHM = "SHA-256";
    private static final HexFormat HEX = HexFormat.of();

    /** How long a challenge may be used after it was issued. */
    private static final Duration TTL = Duration.ofMinutes(10);

    private final ServerSettingsService settings;
    private final UsedChallengeRepository used;
    private final ObjectMapper json;
    private final SecureRandom random = new SecureRandom();
    private final byte[] hmacKey = new byte[32];

    public AltchaService(ServerSettingsService settings, UsedChallengeRepository used, ObjectMapper json) {
        this.settings = settings;
        this.used = used;
        this.json = json;
        random.nextBytes(hmacKey);
    }

    public Challenge create() {
        int maxNumber = settings.current().challengeMaxNumber();
        long expires = Instant.now().plus(TTL).getEpochSecond();
        byte[] saltBytes = new byte[12];
        random.nextBytes(saltBytes);
        String salt = HEX.formatHex(saltBytes) + "?expires=" + expires;
        int number = random.nextInt(maxNumber + 1);
        String challenge = sha256Hex(salt + number);
        return new Challenge(ALGORITHM, challenge, salt, hmac(challenge), maxNumber);
    }

    /**
     * Checks a solved challenge payload: the base64 JSON object
     * {@code {algorithm, challenge, number, salt, signature}}.
     *
     * @throws ApiException {@code challenge_invalid} when anything is off
     */
    @Transactional
    public void verify(String payload) {
        JsonNode solution;
        try {
            solution = json.readTree(Base64.getDecoder().decode(payload.strip()));
        } catch (Exception e) {
            throw invalid("Challenge payload is not valid");
        }
        String algorithm = text(solution, "algorithm");
        String challenge = text(solution, "challenge");
        String salt = text(solution, "salt");
        String signature = text(solution, "signature");
        JsonNode number = solution.get("number");
        if (!ALGORITHM.equals(algorithm) || challenge == null || salt == null || signature == null
                || number == null || !number.canConvertToInt()) {
            throw invalid("Challenge payload is incomplete");
        }
        // No upper bound: the difficulty may have changed since the challenge was issued, and the
        // signed challenge already pins the number.
        if (number.intValue() < 0) {
            throw invalid("Challenge number out of range");
        }
        Instant expiresAt = expiresAt(salt);
        if (expiresAt == null || !Instant.now().isBefore(expiresAt)) {
            throw invalid("Challenge has expired");
        }
        if (!MessageDigest.isEqual(hmac(challenge).getBytes(StandardCharsets.US_ASCII),
                signature.getBytes(StandardCharsets.US_ASCII))) {
            throw invalid("Challenge was not issued by this server");
        }
        if (!MessageDigest.isEqual(sha256Hex(salt + number.intValue()).getBytes(StandardCharsets.US_ASCII),
                challenge.getBytes(StandardCharsets.US_ASCII))) {
            throw invalid("Challenge solution is wrong");
        }
        if (!used.markUsed(challenge, expiresAt)) {
            throw invalid("Challenge has already been used");
        }
        // Opportunistic cleanup; rows are tiny and this keeps the table bounded.
        used.deleteExpired(Instant.now());
    }

    private static Instant expiresAt(String salt) {
        int at = salt.indexOf("?expires=");
        if (at < 0) {
            return null;
        }
        try {
            return Instant.ofEpochSecond(Long.parseLong(salt.substring(at + "?expires=".length())));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isTextual() ? null : value.asText();
    }

    private static ApiException invalid(String message) {
        return ApiException.badRequest("challenge_invalid", message);
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
            return HEX.formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static String sha256Hex(String data) {
        try {
            return HEX.formatHex(MessageDigest.getInstance(ALGORITHM).digest(data.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
