package app.snatter.server.settings;

import static app.snatter.server.persistence.Rows.id;
import static app.snatter.server.persistence.Rows.instant;

import app.snatter.server.account.AccountId;
import app.snatter.server.channel.ChannelId;
import app.snatter.server.role.RoleId;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.time.Instant;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;

@ApplicationScoped
public class ServerSettingsRepository {

    /** Primary key of the single settings row, created by the V1 migration. */
    static final short SINGLETON_ID = 1;

    private static final RowMapper<ServerSettings> MAPPER = (rs, ctx) -> new ServerSettings(
        rs.getString("name"),
        rs.getString("description"),
        rs.getString("public_url"),
        id(rs, "owner_account_id", AccountId::new),
        RegistrationMode.fromDbValue(rs.getString("registration_mode")),
        rs.getBoolean("registration_challenge"),
        new RateLimits(
            rs.getBoolean("rate_limits_enabled"),
            policy(rs.getInt("rate_limit_login_limit"), rs.getInt("rate_limit_login_period")),
            policy(rs.getInt("rate_limit_register_limit"), rs.getInt("rate_limit_register_period")),
            policy(rs.getInt("rate_limit_challenge_limit"), rs.getInt("rate_limit_challenge_period")),
            policy(rs.getInt("rate_limit_invite_limit"), rs.getInt("rate_limit_invite_period")),
            policy(rs.getInt("rate_limit_message_limit"), rs.getInt("rate_limit_message_period"))),
        id(rs, "system_channel_id", ChannelId::new),
        id(rs, "new_member_role_id", RoleId::new),
        instant(rs, "created_at"),
        instant(rs, "updated_at"));

    private static RateLimitPolicy policy(int limit, int periodSeconds) {
        return new RateLimitPolicy(limit, Duration.ofSeconds(periodSeconds));
    }

    private final Jdbi jdbi;

    public ServerSettingsRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public ServerSettings get() {
        return jdbi.withHandle(h -> h
            .createQuery("""
                SELECT name, description, public_url, owner_account_id,
                       registration_mode, registration_challenge,
                       rate_limits_enabled,
                       rate_limit_login_limit, rate_limit_login_period,
                       rate_limit_register_limit, rate_limit_register_period,
                       rate_limit_challenge_limit, rate_limit_challenge_period,
                       rate_limit_invite_limit, rate_limit_invite_period,
                       rate_limit_message_limit, rate_limit_message_period,
                       system_channel_id, new_member_role_id, created_at, updated_at
                FROM server_settings
                WHERE id = :id
                """)
            .bind("id", SINGLETON_ID)
            .map(MAPPER)
            .findOne()
            .orElseThrow(() -> new IllegalStateException(
                "server_settings row is missing; database migrations did not run")));
    }

    /** Writes every owner-editable field. The owner itself is set only through {@link #claimOwner}. */
    public void update(ServerSettings s) {
        int rows = jdbi.withHandle(h -> h
            .createUpdate("""
                UPDATE server_settings
                SET name = :name,
                    description = :description,
                    public_url = :publicUrl,
                    registration_mode = :registrationMode,
                    registration_challenge = :challengeRequired,
                    rate_limits_enabled = :rateLimitsEnabled,
                    rate_limit_login_limit = :loginLimit,
                    rate_limit_login_period = :loginPeriod,
                    rate_limit_register_limit = :registerLimit,
                    rate_limit_register_period = :registerPeriod,
                    rate_limit_challenge_limit = :challengeLimit,
                    rate_limit_challenge_period = :challengePeriod,
                    rate_limit_invite_limit = :inviteLimit,
                    rate_limit_invite_period = :invitePeriod,
                    rate_limit_message_limit = :messageLimit,
                    rate_limit_message_period = :messagePeriod,
                    system_channel_id = :systemChannelId,
                    new_member_role_id = :newMemberRoleId,
                    updated_at = :now
                WHERE id = :id
                """)
            .bind("name", s.name())
            .bind("description", s.description())
            .bind("publicUrl", s.publicUrl())
            .bind("registrationMode", s.registrationMode().dbValue())
            .bind("challengeRequired", s.challengeRequired())
            .bind("rateLimitsEnabled", s.rateLimits().enabled())
            .bind("loginLimit", s.rateLimits().login().limit())
            .bind("loginPeriod", s.rateLimits().login().period().toSeconds())
            .bind("registerLimit", s.rateLimits().register().limit())
            .bind("registerPeriod", s.rateLimits().register().period().toSeconds())
            .bind("challengeLimit", s.rateLimits().challenge().limit())
            .bind("challengePeriod", s.rateLimits().challenge().period().toSeconds())
            .bind("inviteLimit", s.rateLimits().invite().limit())
            .bind("invitePeriod", s.rateLimits().invite().period().toSeconds())
            .bind("messageLimit", s.rateLimits().message().limit())
            .bind("messagePeriod", s.rateLimits().message().period().toSeconds())
            .bind("systemChannelId", s.systemChannelId())
            .bind("newMemberRoleId", s.newMemberRoleId())
            .bind("now", Instant.now())
            .bind("id", SINGLETON_ID)
            .execute());
        if (rows != 1) {
            throw new IllegalStateException("expected to update 1 server_settings row, updated " + rows);
        }
    }

    /** Makes the account the owner if no owner exists yet. Returns whether it did. */
    public boolean claimOwner(AccountId accountId) {
        return jdbi.withHandle(h -> h
            .createUpdate("""
                UPDATE server_settings
                SET owner_account_id = :accountId, updated_at = :now
                WHERE id = :id AND owner_account_id IS NULL
                """)
            .bind("accountId", accountId)
            .bind("now", Instant.now())
            .bind("id", SINGLETON_ID)
            .execute()) == 1;
    }
}
