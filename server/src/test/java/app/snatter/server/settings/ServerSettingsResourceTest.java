package app.snatter.server.settings;

import static app.snatter.server.testing.ApiAssertions.assertApiError;
import static app.snatter.server.testing.ApiAssertions.assertApiStatus;
import static app.snatter.server.testing.ApiAssertions.errorOf;
import static app.snatter.server.testing.ApiAssertions.header;
import static app.snatter.server.testing.ApiClientFactory.authApi;
import static app.snatter.server.testing.ApiClientFactory.channelsApi;
import static app.snatter.server.testing.ApiClientFactory.serverApi;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.snatter.client.ApiException;
import app.snatter.client.api.ServerApi;
import app.snatter.client.model.ChallengeDto;
import app.snatter.client.model.ChannelTypeDto;
import app.snatter.client.model.CommunityDto;
import app.snatter.client.model.PermissionDto;
import app.snatter.client.model.RegisterRequestDto;
import app.snatter.client.model.RegistrationModeDto;
import app.snatter.client.model.ServerSettingsDto;
import app.snatter.client.model.ServerSettingsUpdateDto;
import app.snatter.client.model.VoiceInfoDto;
import app.snatter.server.testing.TestDataService;
import app.snatter.server.testing.TestUsers;
import io.quarkus.test.junit.QuarkusTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ServerSettingsResourceTest {

    private final TestDataService data = new TestDataService();
    private TestUsers.User owner;

    @BeforeEach
    void setUpServer() {
        owner = data.setUpServer();
    }

    private static ServerSettingsUpdateDto update() {
        return new ServerSettingsUpdateDto();
    }

    @Test
    void settingsRequireTheManageServerPermission() {
        ServerApi asMember = serverApi(TestUsers.register());
        ServerApi asAdmin = serverApi(data.registerWithPermissions(PermissionDto.MANAGE_SERVER));

        assertApiStatus(401, () -> serverApi().getServerSettings());
        assertApiError(403, "forbidden", asMember::getServerSettings);
        assertApiError(403, "forbidden", () -> asMember.updateServerSettings(update().name("hijacked")));
        asAdmin.getServerSettings();

        ServerSettingsDto settings = serverApi(owner).getServerSettings();
        assertNotNull(settings.getName());
        assertEquals(RegistrationModeDto.OPEN, settings.getRegistrationMode());
        assertFalse(settings.getRateLimits().getEnabled());
        assertEquals(10, settings.getRateLimits().getLogin().getLimit());
    }

    @Test
    void ownerCanRenameAndDescribeTheCommunity() {
        ServerSettingsDto renamed = data.updateSettings(update().name("  Snattra HQ  ").description("Where the ducks quack"));
        assertEquals("Snattra HQ", renamed.getName());
        assertEquals("Where the ducks quack", renamed.getDescription());

        CommunityDto community = serverApi().getServerInfo().getCommunity();
        assertEquals("Snattra HQ", community.getName());
        assertEquals("Where the ducks quack", community.getDescription());

        assertNull(data.updateSettings(update().description("")).getDescription());
    }

    @Test
    void rejectsInvalidSettings() {
        assertTrue(assertApiError(400, "validation_failed", () -> data.updateSettings(update().name("")))
            .getFields().containsKey("name"));
        assertTrue(assertApiError(400, "validation_failed",
                () -> data.updateSettings(update().rateLimits(TestDataService.rateLimits(true, 0, 60, 5, 3600, 30, 60))))
            .getFields().containsKey("limit"));
    }

    @Test
    void rateLimitsApplyImmediatelyAndReturn429() {
        TestUsers.User u = TestUsers.register();
        assertTrue(data.updateSettings(update().rateLimits(TestDataService.rateLimits(true, 2, 60, 5, 3600, 30, 60)))
            .getRateLimits().getEnabled());

        for (int i = 0; i < 2; i++) {
            assertApiError(401, "invalid_credentials", () -> TestUsers.login(u.username(), "wrong password"));
        }
        ApiException limited = assertApiStatus(429, () -> TestUsers.login(u.username(), "wrong password"));
        assertEquals("rate_limited", errorOf(limited).getError());
        assertNotNull(header(limited, "Retry-After"));
        serverApi().getServerInfo(); // unrelated endpoints unaffected

        // Switching them off applies as immediately.
        data.updateSettings(update().rateLimits(TestDataService.rateLimits(false, 10, 60, 5, 3600, 30, 60)));
        TestUsers.login(u.username(), TestUsers.DEFAULT_PASSWORD);
    }

    @Test
    void challengeRequirementCanBeSwitchedOff() {
        data.updateSettings(update().challengeRequired(false));
        assertFalse(serverApi().getServerInfo().getRegistration().getChallengeRequired());

        authApi().register(new RegisterRequestDto().username("nochallenge").password(TestUsers.DEFAULT_PASSWORD));
    }

    @Test
    void challengesHaveTheAdvertisedShape() {
        ChallengeDto challenge = authApi().getChallenge();
        assertEquals(ChallengeDto.AlgorithmEnum.SHA_256, challenge.getAlgorithm());
        assertNotNull(challenge.getChallenge());
        assertTrue(challenge.getSalt().contains("?expires="), challenge.getSalt());
        assertNotNull(challenge.getSignature());
        assertTrue(challenge.getMaxnumber() >= 1);
    }

    @Test
    void defaultsMatchWhatConfigurationUsedToSet() {
        ServerSettingsDto settings = serverApi(owner).getServerSettings();
        assertEquals(30, settings.getSessionLifetimeDays());
        assertEquals(100000, settings.getChallengeMaxNumber());
        assertEquals(64000, settings.getVoice().getDefaultBitrate());
    }

    @Test
    void challengeDifficultyAppliesToTheNextChallenge() {
        assertEquals(20000, data.updateSettings(update().challengeMaxNumber(20000)).getChallengeMaxNumber());
        assertEquals(20000, authApi().getChallenge().getMaxnumber());
    }

    @Test
    void newVoiceChannelsGetTheDefaultBitrate() {
        assertEquals(32000, data.updateSettings(update().voice(new VoiceInfoDto().defaultBitrate(32000))).getVoice().getDefaultBitrate());
        assertEquals(32000, serverApi().getServerInfo().getVoice().getDefaultBitrate());

        UUID fresh = data.createChannel(ChannelTypeDto.VOICE, "fresh");
        assertEquals(32000, channelsApi(owner).getChannel(fresh).getBitrate());
    }

    @Test
    void rejectsSettingsOutOfRange() {
        assertApiError(400, "validation_failed", () -> data.updateSettings(update().voice(new VoiceInfoDto().defaultBitrate(600000))));
        assertApiError(400, "validation_failed", () -> data.updateSettings(update().sessionLifetimeDays(0)));
        assertApiError(400, "validation_failed", () -> data.updateSettings(update().challengeMaxNumber(10)));
    }
}
