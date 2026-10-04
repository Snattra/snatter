package app.snatter.server.testing;

import static app.snatter.server.testing.ApiAssertions.assertApiError;
import static app.snatter.server.testing.ApiAssertions.assertApiStatus;
import static app.snatter.server.testing.ApiClientFactory.accountsApi;
import static app.snatter.server.testing.ApiClientFactory.channelsApi;
import static app.snatter.server.testing.ApiClientFactory.rolesApi;
import static app.snatter.server.testing.ApiClientFactory.serverApi;
import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.snatter.client.model.ChannelDto;
import app.snatter.client.model.ChannelTypeDto;
import app.snatter.client.model.RegistrationModeDto;
import app.snatter.client.model.RoleDto;
import app.snatter.client.model.ServerInfoDto;
import app.snatter.client.model.ServerSettingsUpdateDto;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class DatabaseResetTest {

    private static final UUID GENERAL_TEXT = UUID.fromString("00000000-0000-7000-8000-000000000101");

    private final TestDataService data = new TestDataService();

    @Inject
    DatabaseReset reset;

    @Test
    void leavesWhatAFreshInstallHas() {
        data.setUpServer();
        TestUsers.User member = TestUsers.register();
        data.createRole("Leftover");
        data.createChannel(ChannelTypeDto.TEXT, "leftover");
        data.updateSettings(new ServerSettingsUpdateDto().name("Leftover community"));

        reset.reset();

        // The settings are read afresh, not remembered.
        ServerInfoDto info = serverApi().getServerInfo();
        assertTrue(info.getRegistration().getSetupRequired());
        assertEquals(RegistrationModeDto.INVITE_ONLY, info.getRegistration().getMode());
        assertNull(info.getOwnerId());
        assertEquals("My Snatter server", info.getCommunity().getName());
        assertApiStatus(401, () -> accountsApi(member).getCurrentAccount());

        TestUsers.User owner = new TestDataService().setUpServer();
        assertEquals(Set.of("User", "Moderator", "Admin"), rolesApi(owner).listRoles().stream().map(RoleDto::getName).collect(toSet()));
        assertEquals(List.of(GENERAL_TEXT), channelsApi(owner).listChannels().stream().map(ChannelDto::getId).toList());
    }

    @Test
    void rateLimitsStartOver() {
        data.setUpServer();
        TestUsers.User member = TestUsers.register();
        data.updateSettings(new ServerSettingsUpdateDto().rateLimits(TestDataService.rateLimits(true, 1, 3600, 5, 3600, 30, 60)));
        assertApiError(401, "invalid_credentials", () -> TestUsers.login(member.username(), "wrong password"));
        assertApiError(429, "rate_limited", () -> TestUsers.login(member.username(), "wrong password"));

        reset.reset();

        // A fresh server limits logins too, but to ten a minute, and nobody has tried yet.
        assertApiError(401, "invalid_credentials", () -> TestUsers.login(member.username(), "wrong password"));
    }
}
