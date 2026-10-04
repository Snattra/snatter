package app.snatter.server.auth;

import static app.snatter.server.testing.ApiAssertions.assertApiError;
import static app.snatter.server.testing.ApiAssertions.assertApiStatus;
import static app.snatter.server.testing.ApiAssertions.header;
import static app.snatter.server.testing.ApiClientFactory.accountsApi;
import static app.snatter.server.testing.ApiClientFactory.authApi;
import static app.snatter.server.testing.ApiClientFactory.serverApi;
import static app.snatter.server.testing.TestUsers.DEFAULT_PASSWORD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.snatter.client.ApiException;
import app.snatter.client.api.AuthApi;
import app.snatter.client.model.AccountDto;
import app.snatter.client.model.ApiErrorDto;
import app.snatter.client.model.AuthResponseDto;
import app.snatter.client.model.ChallengeDto;
import app.snatter.client.model.RegisterRequestDto;
import app.snatter.client.model.RegistrationModeDto;
import app.snatter.client.model.ServerSettingsUpdateDto;
import app.snatter.server.testing.TestDataService;
import app.snatter.server.testing.TestUsers;
import io.quarkus.test.junit.QuarkusTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AuthResourceTest {

    private final TestDataService data = new TestDataService();
    private AuthApi auth;

    @BeforeEach
    void setUpServer() {
        data.setUpServer();
        auth = authApi();
    }

    private static String uniqueUsername() {
        return "user_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** A registration with the given challenge solution, or none. */
    private static RegisterRequestDto withAltcha(String altcha) {
        return new RegisterRequestDto().username(uniqueUsername()).password(DEFAULT_PASSWORD).altcha(altcha);
    }

    @Test
    void registerThenReadOwnAccount() {
        String username = uniqueUsername();
        AuthResponseDto registered = auth.register(TestUsers.registration(username, DEFAULT_PASSWORD, null));
        assertTrue(registered.getToken().startsWith("snt_"));
        assertNotNull(registered.getExpiresAt());
        AccountDto account = registered.getAccount();
        assertEquals(7, account.getId().version(), "a version 7 UUID");
        assertEquals(2, account.getId().variant(), "of the standard variant");
        assertEquals(username, account.getUsername());
        assertEquals(username, account.getDisplayName());

        TestUsers.User u = TestUsers.register();
        assertEquals(u.username(), accountsApi(u).getCurrentAccount().getUsername());
    }

    @Test
    void registerUsesDisplayNameWhenGiven() {
        AuthResponseDto registered = auth.register(TestUsers.registration(uniqueUsername(), DEFAULT_PASSWORD, "Quacky"));
        assertEquals("Quacky", registered.getAccount().getDisplayName());
    }

    @Test
    void displayNamesAreTrimmedAndCheckedBeforeAnythingElse() {
        AuthResponseDto trimmed = auth.register(TestUsers.registration(uniqueUsername(), DEFAULT_PASSWORD, "  Robin    Jönsson "));
        assertEquals("Robin Jönsson", trimmed.getAccount().getDisplayName());
        String username = uniqueUsername();
        RegisterRequestDto duck = TestUsers.registration(username, DEFAULT_PASSWORD, "Mallard 🦆");
        assertApiError(400, "invalid_display_name", () -> auth.register(duck));
        // Nothing was created, so the username is still free; a blank name falls back to it.
        AuthResponseDto blank = auth.register(TestUsers.registration(username, DEFAULT_PASSWORD, "   "));
        assertEquals(username, blank.getAccount().getDisplayName());
    }

    @Test
    void usernameIsUniqueIgnoringCase() {
        TestUsers.User u = TestUsers.register();
        RegisterRequestDto shouted = TestUsers.registration(u.username().toUpperCase(), DEFAULT_PASSWORD, null);
        assertApiError(409, "username_taken", () -> auth.register(shouted));
    }

    @Test
    void rejectsInvalidRegistration() {
        RegisterRequestDto invalid = TestUsers.registration("no spaces allowed", "short", null);
        ApiErrorDto error = assertApiError(400, "validation_failed", () -> auth.register(invalid));
        assertTrue(error.getFields().keySet().containsAll(List.of("username", "password")), error.getFields().toString());
        String suffix = UUID.randomUUID().toString().substring(0, 4);
        for (String username : new String[] {"dotted.name" + suffix, "dashed-name" + suffix, "åsa_" + suffix}) {
            RegisterRequestDto registration = TestUsers.registration(username, DEFAULT_PASSWORD, null);
            assertTrue(assertApiError(400, "validation_failed", () -> auth.register(registration)).getFields().containsKey("username"));
        }
    }

    @Test
    void registrationRequiresASolvedChallenge() {
        assertApiError(400, "challenge_required", () -> auth.register(withAltcha(null)));
        assertApiError(400, "challenge_invalid", () -> auth.register(withAltcha("bm90IGEgY2hhbGxlbmdl")));
    }

    @Test
    void rejectsWrongSolutionsAndReplays() {
        ChallengeDto c = auth.getChallenge();
        assertEquals(ChallengeDto.AlgorithmEnum.SHA_256, c.getAlgorithm());

        // A number that is (almost certainly) not the solution.
        String wrong = TestUsers.payload(c.getChallenge(), c.getSalt(), c.getSignature(), -1);
        assertApiError(400, "challenge_invalid", () -> auth.register(withAltcha(wrong)));

        // A forged signature.
        String forged = TestUsers.payload(c.getChallenge(), c.getSalt(), "00" + c.getSignature().substring(2), 1);
        assertApiError(400, "challenge_invalid", () -> auth.register(withAltcha(forged)));

        // A real solution works once and only once.
        String solved = TestUsers.solveChallenge();
        auth.register(withAltcha(solved));
        assertApiError(400, "challenge_invalid", () -> auth.register(withAltcha(solved)));
    }

    @Test
    void registrationCanBeClosedByTheOwner() {
        data.updateSettings(new ServerSettingsUpdateDto().registrationMode(RegistrationModeDto.INVITE_ONLY));
        assertEquals(RegistrationModeDto.INVITE_ONLY, serverApi().getServerInfo().getRegistration().getMode());

        RegisterRequestDto registration = TestUsers.registration(uniqueUsername(), DEFAULT_PASSWORD, null);
        assertApiError(403, "registration_closed", () -> auth.register(registration));
    }

    @Test
    void loginWithCorrectAndWrongPassword() {
        TestUsers.User u = TestUsers.register();

        AuthResponseDto session = TestUsers.login(u.username(), DEFAULT_PASSWORD);
        assertTrue(session.getToken().startsWith("snt_"));
        assertEquals(u.username(), session.getAccount().getUsername());

        assertApiError(401, "invalid_credentials", () -> TestUsers.login(u.username(), "wrong password"));
    }

    @Test
    void loginWithUnknownUserLooksLikeWrongPassword() {
        assertApiError(401, "invalid_credentials", () -> TestUsers.login("nobody_" + UUID.randomUUID(), "whatever it is"));
    }

    @Test
    void logoutRevokesTheToken() {
        TestUsers.User u = TestUsers.register();
        authApi(u).logout();
        assertApiStatus(401, () -> accountsApi(u).getCurrentAccount());
    }

    @Test
    void protectedEndpointsRequireAValidToken() {
        ApiException missing = assertApiStatus(401, () -> accountsApi().getCurrentAccount());
        assertEquals("Bearer", header(missing, "WWW-Authenticate"));

        TestUsers.User forged = new TestUsers.User("snt_not_a_real_token", UUID.randomUUID(), "nobody");
        assertApiStatus(401, () -> accountsApi(forged).getCurrentAccount());
    }
}
