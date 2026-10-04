package app.snatter.server.testing;

import static app.snatter.server.testing.ApiClientFactory.authApi;
import static app.snatter.server.testing.ApiClientFactory.serverApi;

import app.snatter.client.model.AuthResponseDto;
import app.snatter.client.model.ChallengeDto;
import app.snatter.client.model.LoginRequestDto;
import app.snatter.client.model.RegisterRequestDto;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Registers users as a client would, solving the registration challenge
 * when the server asks for one.
 *
 * <p>The server must be set up first ({@link TestDataService#setUpServer()}),
 * or the first registration makes its account the owner.
 */
public final class TestUsers {

    public record User(String token, UUID id, String username) {
    }

    public static final String DEFAULT_PASSWORD = "a long enough password";

    private TestUsers() {
    }

    /** Registers a fresh user with a random username. */
    public static User register() {
        return register("user_" + UUID.randomUUID().toString().substring(0, 8));
    }

    public static User register(String username) {
        return register(username, DEFAULT_PASSWORD);
    }

    public static User register(String username, String password) {
        AuthResponseDto r = authApi().register(registration(username, password, null));
        return new User(r.getToken(), r.getAccount().getId(), username);
    }

    /** A registration, with a freshly solved challenge if the server requires one. */
    public static RegisterRequestDto registration(String username, String password, String displayName) {
        return new RegisterRequestDto()
            .username(username)
            .password(password)
            .displayName(displayName)
            .altcha(challengeRequired() ? solveChallenge() : null);
    }

    public static AuthResponseDto login(String username, String password) {
        return authApi().login(new LoginRequestDto().username(username).password(password));
    }

    /** The user signed in once more, as a session of its own. */
    public static User newSession(User user) {
        return new User(login(user.username(), DEFAULT_PASSWORD).getToken(), user.id(), user.username());
    }

    public static boolean challengeRequired() {
        return serverApi().getServerInfo().getRegistration().getChallengeRequired();
    }

    /** Fetches a challenge and brute-forces it, returning the base64 payload the server expects. */
    public static String solveChallenge() {
        ChallengeDto c = authApi().getChallenge();
        for (int number = 0; number <= c.getMaxnumber(); number++) {
            if (sha256Hex(c.getSalt() + number).equals(c.getChallenge())) {
                return payload(c.getChallenge(), c.getSalt(), c.getSignature(), number);
            }
        }
        throw new AssertionError("challenge has no solution up to " + c.getMaxnumber());
    }

    public static String payload(String challenge, String salt, String signature, int number) {
        String json = "{\"algorithm\":\"SHA-256\",\"challenge\":\"" + challenge + "\",\"number\":" + number
            + ",\"salt\":\"" + salt + "\",\"signature\":\"" + signature + "\"}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
