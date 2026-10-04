package app.snatter.server.invite;

import static app.snatter.server.testing.ApiAssertions.assertApiError;
import static app.snatter.server.testing.ApiAssertions.assertApiStatus;
import static app.snatter.server.testing.ApiClientFactory.authApi;
import static app.snatter.server.testing.ApiClientFactory.invitesApi;
import static app.snatter.server.testing.TestUsers.DEFAULT_PASSWORD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.snatter.client.api.InvitesApi;
import app.snatter.client.model.InviteCreateDto;
import app.snatter.client.model.InviteDto;
import app.snatter.client.model.InvitePreviewDto;
import app.snatter.client.model.PermissionDto;
import app.snatter.client.model.RegisterRequestDto;
import app.snatter.client.model.RegistrationModeDto;
import app.snatter.client.model.ServerSettingsUpdateDto;
import app.snatter.server.testing.TestDataService;
import app.snatter.server.testing.TestUsers;
import io.quarkus.test.junit.QuarkusTest;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class InviteResourceTest {

    private final TestDataService data = new TestDataService();

    @BeforeEach
    void setUpServer() {
        data.setUpServer();
    }

    private static String newCode(TestUsers.User creator, InviteCreateDto invite) {
        return invitesApi(creator).createInvite(invite).getCode();
    }

    /** A registration with the invite, under a fresh username. */
    private static RegisterRequestDto invited(String code) {
        return TestUsers.registration("invited_" + UUID.randomUUID().toString().substring(0, 8), DEFAULT_PASSWORD, null)
            .inviteCode(code);
    }

    private static List<String> codes(List<InviteDto> invites) {
        return invites.stream().map(InviteDto::getCode).toList();
    }

    private static InviteDto find(List<InviteDto> invites, String code) {
        return invites.stream().filter(invite -> invite.getCode().equals(code)).findFirst().orElseThrow();
    }

    @Test
    void memberCreatesAnInviteWithALinkAndDefaults() {
        TestUsers.User member = TestUsers.register();
        InviteDto invite = invitesApi(member).createInvite(new InviteCreateDto());
        assertTrue(invite.getCode().matches("[A-Za-z0-9]{8}"), invite.getCode());
        assertTrue(invite.getUrl().matches("http://[^/]+/invite/[A-Za-z0-9]{8}"), invite.getUrl());
        assertEquals(member.id(), invite.getCreatedBy());
        assertNotNull(invite.getCreatedAt());
        assertNull(invite.getExpiresAt());
        assertNull(invite.getMaxUses());
        assertEquals(0, invite.getUses());
        assertFalse(invite.getRevoked());
    }

    @Test
    void linkUsesThePublicUrlWhenConfigured() {
        TestUsers.User member = TestUsers.register();
        assertEquals("https://chat.example.com",
            data.updateSettings(new ServerSettingsUpdateDto().publicUrl("https://chat.example.com/")).getPublicUrl());
        String url = invitesApi(member).createInvite(new InviteCreateDto()).getUrl();
        assertTrue(url.matches("https://chat\\.example\\.com/invite/[A-Za-z0-9]{8}"), url);
        assertNull(data.updateSettings(new ServerSettingsUpdateDto().publicUrl("")).getPublicUrl());
        assertApiError(400, "validation_failed", () -> data.updateSettings(new ServerSettingsUpdateDto().publicUrl("not a url")));
    }

    @Test
    void previewIsPublicAndShowsCommunityAndInviter() {
        TestUsers.User member = TestUsers.register();
        String code = newCode(member, new InviteCreateDto().expiresInSeconds(3600L).maxUses(5));

        InvitePreviewDto preview = invitesApi().previewInvite(code);
        assertEquals(code, preview.getCode());
        assertNotNull(preview.getCommunity().getName());
        assertEquals(member.id(), preview.getInviter().getId());
        assertEquals(member.username(), preview.getInviter().getUsername());
        assertNotNull(preview.getExpiresAt());

        assertApiError(404, "invite_not_found", () -> invitesApi().previewInvite("ZZZZZZZZ"));
        assertApiStatus(404, () -> invitesApi().previewInvite("not-valid"));
    }

    @Test
    void ownerSeesAllInvitesMembersOnlyTheirOwn() {
        TestUsers.User a = TestUsers.register();
        TestUsers.User b = TestUsers.register();
        String codeA = newCode(a, new InviteCreateDto());
        String codeB = newCode(b, new InviteCreateDto());

        assertEquals(List.of(codeA), codes(invitesApi(a).listInvites()));
        assertEquals(Set.of(codeA, codeB), Set.copyOf(codes(invitesApi(data.owner()).listInvites())));
        assertApiStatus(401, () -> invitesApi().listInvites());
    }

    @Test
    void revocationRights() {
        TestUsers.User creator = TestUsers.register();
        InvitesApi asCreator = invitesApi(creator);
        InvitesApi asOther = invitesApi(TestUsers.register());
        InvitesApi asOwner = invitesApi(data.owner());
        String code = newCode(creator, new InviteCreateDto());

        assertApiError(403, "forbidden", () -> asOther.revokeInvite(code));
        asCreator.revokeInvite(code);
        assertApiError(404, "invite_not_found", () -> invitesApi().previewInvite(code));
        assertTrue(find(asCreator.listInvites(), code).getRevoked());

        asOwner.revokeInvite(newCode(creator, new InviteCreateDto()));
        assertApiError(404, "invite_not_found", () -> asOwner.revokeInvite("ZZZZZZZZ"));
    }

    @Test
    void invitingRequiresTheCreateInvitePermission() {
        TestUsers.User member = TestUsers.register();
        InvitesApi asMember = invitesApi(member);
        asMember.createInvite(new InviteCreateDto());
        data.unassignRole(member.id(), TestDataService.USER_ROLE);

        assertApiError(403, "forbidden", () -> asMember.createInvite(new InviteCreateDto()));
        invitesApi(data.owner()).createInvite(new InviteCreateDto());
        invitesApi(data.registerWithPermissions(PermissionDto.CREATE_INVITE)).createInvite(new InviteCreateDto());
    }

    @Test
    void inviteOnlyRegistrationNeedsAUsableInvite() {
        TestUsers.User member = TestUsers.register();
        InvitesApi asMember = invitesApi(member);
        String singleUse = newCode(member, new InviteCreateDto().maxUses(1));
        String revoked = newCode(member, new InviteCreateDto());
        asMember.revokeInvite(revoked);
        data.updateSettings(new ServerSettingsUpdateDto().registrationMode(RegistrationModeDto.INVITE_ONLY));

        RegisterRequestDto uninvited = TestUsers.registration("uninvited", DEFAULT_PASSWORD, null);
        assertApiError(403, "registration_closed", () -> authApi().register(uninvited));

        RegisterRequestDto withRevoked = invited(revoked);
        RegisterRequestDto withUnknown = invited("ZZZZZZZZ");
        assertApiError(403, "invite_invalid", () -> authApi().register(withRevoked));
        assertApiError(403, "invite_invalid", () -> authApi().register(withUnknown));

        assertTrue(authApi().register(invited(singleUse)).getToken().startsWith("snt_"));
        assertEquals(1, find(asMember.listInvites(), singleUse).getUses());
        assertApiError(410, "invite_unusable", () -> invitesApi().previewInvite(singleUse));

        RegisterRequestDto withUsedUp = invited(singleUse);
        assertApiError(403, "invite_invalid", () -> authApi().register(withUsedUp));
    }

    @Test
    void aFailedRegistrationDoesNotConsumeTheInvite() {
        TestUsers.User member = TestUsers.register();
        TestUsers.User existing = TestUsers.register();
        String code = newCode(member, new InviteCreateDto().maxUses(1));

        RegisterRequestDto clash = TestUsers.registration(existing.username(), DEFAULT_PASSWORD, null).inviteCode(code);
        assertApiError(409, "username_taken", () -> authApi().register(clash));

        authApi().register(invited(code));
    }

    @Test
    void rejectsLimitsOutsideTheContract() {
        InvitesApi asMember = invitesApi(TestUsers.register());
        assertApiError(400, "validation_failed", () -> asMember.createInvite(new InviteCreateDto().expiresInSeconds(5L)));
        assertApiError(400, "validation_failed", () -> asMember.createInvite(new InviteCreateDto().maxUses(0)));
    }
}
