package app.snatter.server.testing;

import static app.snatter.server.testing.ApiClientFactory.channelsApi;
import static app.snatter.server.testing.ApiClientFactory.rolesApi;
import static app.snatter.server.testing.ApiClientFactory.serverApi;

import app.snatter.client.model.ChannelCreateDto;
import app.snatter.client.model.ChannelTypeDto;
import app.snatter.client.model.PermissionDto;
import app.snatter.client.model.RateLimitPolicyDto;
import app.snatter.client.model.RateLimitsDto;
import app.snatter.client.model.RegistrationModeDto;
import app.snatter.client.model.RoleCreateDto;
import app.snatter.client.model.ServerSettingsDto;
import app.snatter.client.model.ServerSettingsUpdateDto;
import java.util.List;
import java.util.UUID;

/**
 * Sets up what tests work with, through the API as the owner.
 *
 * <p>Every test starts on a fresh server ({@link ResetDatabaseBeforeEach}):
 * no accounts, the seeded roles and General channel, and the default
 * settings. A test class keeps one of these in a field, and so gets a new
 * one with each test, and calls {@link #setUpServer()} from its
 * {@code @BeforeEach} for what its tests share. The other methods act as the
 * owner it registered.
 */
public final class TestDataService {

    public static final String OWNER_USERNAME = "owner";
    public static final String OWNER_PASSWORD = "owner password 1234";

    /** The standard roles seeded by the schema migration; new members get {@link #USER_ROLE}. */
    public static final UUID USER_ROLE = UUID.fromString("00000000-0000-7000-8000-000000000001");
    public static final UUID ADMIN_ROLE = UUID.fromString("00000000-0000-7000-8000-000000000002");
    public static final UUID MODERATOR_ROLE = UUID.fromString("00000000-0000-7000-8000-000000000003");

    private TestUsers.User owner;

    /**
     * Registers the owner, as the server's first account, and applies the
     * settings the tests assume: open registration and no rate limits.
     */
    public TestUsers.User setUpServer() {
        owner = TestUsers.register(OWNER_USERNAME, OWNER_PASSWORD);
        updateSettings(new ServerSettingsUpdateDto()
            .registrationMode(RegistrationModeDto.OPEN)
            .rateLimits(rateLimits(false, 10, 60, 5, 3600, 30, 60)));
        return owner;
    }

    public TestUsers.User owner() {
        if (owner == null) {
            throw new IllegalStateException("No owner yet; call setUpServer() first, usually from @BeforeEach");
        }
        return owner;
    }

    // --- Settings ------------------------------------------------------------

    public ServerSettingsDto updateSettings(ServerSettingsUpdateDto update) {
        return serverApi(owner()).updateServerSettings(update);
    }

    public static RateLimitsDto rateLimits(boolean enabled, int loginLimit, int loginPeriod,
                                           int registerLimit, int registerPeriod,
                                           int challengeLimit, int challengePeriod) {
        return new RateLimitsDto()
            .enabled(enabled)
            .login(policy(loginLimit, loginPeriod))
            .register(policy(registerLimit, registerPeriod))
            .challenge(policy(challengeLimit, challengePeriod))
            .invite(policy(30, 60))
            .message(policy(5, 5));
    }

    /** The test defaults, with rate limits switched on and the given message limit. */
    public static RateLimitsDto messageRateLimit(int limit, int periodSeconds) {
        return rateLimits(true, 10, 60, 5, 3600, 30, 60).message(policy(limit, periodSeconds));
    }

    private static RateLimitPolicyDto policy(int limit, int periodSeconds) {
        return new RateLimitPolicyDto().limit(limit).periodSeconds(periodSeconds);
    }

    // --- Channels ------------------------------------------------------------

    /** Creates a channel as the owner and returns its id. Required roles make it visible only to them. */
    public UUID createChannel(ChannelTypeDto type, String name, UUID... requiredRoleIds) {
        return channelsApi(owner())
            .createChannel(new ChannelCreateDto().type(type).name(name).requiredRoleIds(List.of(requiredRoleIds)))
            .getId();
    }

    // --- Roles ---------------------------------------------------------------

    /** Creates a role as the owner and returns its id. */
    public UUID createRole(String name, PermissionDto... permissions) {
        return rolesApi(owner()).createRole(new RoleCreateDto().name(name).permissions(List.of(permissions))).getId();
    }

    public void assignRole(UUID accountId, UUID roleId) {
        rolesApi(owner()).assignRole(accountId, roleId);
    }

    public void unassignRole(UUID accountId, UUID roleId) {
        rolesApi(owner()).unassignRole(accountId, roleId);
    }

    public void deleteRole(UUID roleId) {
        rolesApi(owner()).deleteRole(roleId);
    }

    /** A fresh member holding exactly the given permissions through a new role, without the User role. */
    public TestUsers.User registerWithPermissions(PermissionDto... permissions) {
        TestUsers.User user = TestUsers.register();
        unassignRole(user.id(), USER_ROLE);
        assignRole(user.id(), createRole("perm_" + UUID.randomUUID().toString().substring(0, 8), permissions));
        return user;
    }
}
