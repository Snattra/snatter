package app.snatter.server.invite;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

import app.snatter.server.testing.TestUsers;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class InviteResourceTest {

    private static Response createInvite(String token, Map<String, Object> body) {
        return given()
            .header("Authorization", "Bearer " + token)
            .contentType(ContentType.JSON)
            .body(body)
            .post("/api/v1/invites");
    }

    private static String newCode(String token, Map<String, Object> body) {
        return createInvite(token, body).then().statusCode(201).extract().path("code");
    }

    private static Response registerWithInvite(String code) {
        Map<String, Object> body = TestUsers.registration("invited_" + UUID.randomUUID().toString().substring(0, 8),
            TestUsers.DEFAULT_PASSWORD, null);
        body.put("inviteCode", code);
        return TestUsers.registerRaw(body);
    }

    @Test
    void memberCreatesAnInviteWithALinkAndDefaults() {
        TestUsers.User member = TestUsers.register();
        createInvite(member.token(), Map.of())
            .then()
            .statusCode(201)
            .body("code", matchesPattern("[A-Za-z0-9]{8}"))
            .body("url", matchesPattern("http://[^/]+/invite/[A-Za-z0-9]{8}"))
            .body("createdBy", equalTo(member.id()))
            .body("createdAt", notNullValue())
            .body("expiresAt", nullValue())
            .body("maxUses", nullValue())
            .body("uses", equalTo(0))
            .body("revoked", equalTo(false));
    }

    @Test
    void linkUsesThePublicUrlWhenConfigured() {
        TestUsers.User member = TestUsers.register();
        try {
            TestUsers.patchSettings(Map.of("publicUrl", "https://chat.example.com/"))
                .then().statusCode(200).body("publicUrl", equalTo("https://chat.example.com"));
            createInvite(member.token(), Map.of())
                .then().statusCode(201)
                .body("url", matchesPattern("https://chat\\.example\\.com/invite/[A-Za-z0-9]{8}"));
        } finally {
            TestUsers.patchSettings(Map.of("publicUrl", "")).then().statusCode(200).body("publicUrl", nullValue());
        }
        TestUsers.patchSettings(Map.of("publicUrl", "not a url")).then().statusCode(400).body("error", equalTo("validation_failed"));
    }

    @Test
    void previewIsPublicAndShowsCommunityAndInviter() {
        TestUsers.User member = TestUsers.register();
        String code = newCode(member.token(), Map.of("expiresInSeconds", 3600, "maxUses", 5));

        given().get("/api/v1/invites/" + code)
            .then()
            .statusCode(200)
            .body("code", equalTo(code))
            .body("community.name", notNullValue())
            .body("inviter.id", equalTo(member.id()))
            .body("inviter.username", equalTo(member.username()))
            .body("expiresAt", notNullValue());

        given().get("/api/v1/invites/ZZZZZZZZ").then().statusCode(404).body("error", equalTo("invite_not_found"));
        given().get("/api/v1/invites/not-valid").then().statusCode(404);
    }

    @Test
    void ownerSeesAllInvitesMembersOnlyTheirOwn() {
        TestUsers.User a = TestUsers.register();
        TestUsers.User b = TestUsers.register();
        String codeA = newCode(a.token(), Map.of());
        String codeB = newCode(b.token(), Map.of());

        given().header("Authorization", "Bearer " + a.token()).get("/api/v1/invites")
            .then().statusCode(200)
            .body("code", hasItem(codeA))
            .body("code", not(hasItem(codeB)))
            .body("", hasSize(1));

        given().header("Authorization", "Bearer " + TestUsers.ownerToken()).get("/api/v1/invites")
            .then().statusCode(200)
            .body("code", hasItem(codeA))
            .body("code", hasItem(codeB));

        given().get("/api/v1/invites").then().statusCode(401);
    }

    @Test
    void revocationRights() {
        TestUsers.User creator = TestUsers.register();
        TestUsers.User other = TestUsers.register();
        String code = newCode(creator.token(), Map.of());

        given().header("Authorization", "Bearer " + other.token()).delete("/api/v1/invites/" + code)
            .then().statusCode(403).body("error", equalTo("forbidden"));
        given().header("Authorization", "Bearer " + creator.token()).delete("/api/v1/invites/" + code)
            .then().statusCode(204);
        given().get("/api/v1/invites/" + code).then().statusCode(404);
        given().header("Authorization", "Bearer " + creator.token()).get("/api/v1/invites")
            .then().body("find { it.code == '" + code + "' }.revoked", equalTo(true));

        String another = newCode(creator.token(), Map.of());
        given().header("Authorization", "Bearer " + TestUsers.ownerToken()).delete("/api/v1/invites/" + another)
            .then().statusCode(204);
        given().header("Authorization", "Bearer " + TestUsers.ownerToken()).delete("/api/v1/invites/ZZZZZZZZ")
            .then().statusCode(404).body("error", equalTo("invite_not_found"));
    }

    @Test
    void ownerCanRestrictInvitingToThemselves() {
        TestUsers.User member = TestUsers.register();
        try {
            TestUsers.patchSettings(Map.of("membersCanInvite", false)).then().statusCode(200);
            createInvite(member.token(), Map.of()).then().statusCode(403).body("error", equalTo("forbidden"));
            createInvite(TestUsers.ownerToken(), Map.of()).then().statusCode(201);
        } finally {
            TestUsers.patchSettings(Map.of("membersCanInvite", true)).then().statusCode(200);
        }
    }

    @Test
    void inviteOnlyRegistrationNeedsAUsableInvite() {
        TestUsers.User member = TestUsers.register();
        String singleUse = newCode(member.token(), Map.of("maxUses", 1));
        String revoked = newCode(member.token(), Map.of());
        given().header("Authorization", "Bearer " + member.token()).delete("/api/v1/invites/" + revoked).then().statusCode(204);
        try {
            TestUsers.patchSettings(Map.of("registrationMode", "invite_only")).then().statusCode(200);

            TestUsers.registerRaw(TestUsers.registration("uninvited_" + System.nanoTime() % 100000, TestUsers.DEFAULT_PASSWORD, null))
                .then().statusCode(403).body("error", equalTo("registration_closed"));

            registerWithInvite(revoked).then().statusCode(403).body("error", equalTo("invite_invalid"));
            registerWithInvite("ZZZZZZZZ").then().statusCode(403).body("error", equalTo("invite_invalid"));

            registerWithInvite(singleUse).then().statusCode(201).body("token", startsWith("snt_"));
            given().header("Authorization", "Bearer " + member.token()).get("/api/v1/invites")
                .then().body("find { it.code == '" + singleUse + "' }.uses", equalTo(1));
            given().get("/api/v1/invites/" + singleUse).then().statusCode(410).body("error", equalTo("invite_unusable"));

            registerWithInvite(singleUse).then().statusCode(403).body("error", equalTo("invite_invalid"));
        } finally {
            TestUsers.patchSettings(Map.of("registrationMode", "open")).then().statusCode(200);
        }
    }

    @Test
    void aFailedRegistrationDoesNotConsumeTheInvite() {
        TestUsers.User member = TestUsers.register();
        TestUsers.User existing = TestUsers.register();
        String code = newCode(member.token(), Map.of("maxUses", 1));

        Map<String, Object> clash = TestUsers.registration(existing.username(), TestUsers.DEFAULT_PASSWORD, null);
        clash.put("inviteCode", code);
        TestUsers.registerRaw(clash).then().statusCode(409).body("error", equalTo("username_taken"));

        registerWithInvite(code).then().statusCode(201);
    }

    @Test
    void rejectsLimitsOutsideTheContract() {
        TestUsers.User member = TestUsers.register();
        createInvite(member.token(), Map.of("expiresInSeconds", 5))
            .then().statusCode(400).body("error", equalTo("validation_failed"));
        createInvite(member.token(), Map.of("maxUses", 0))
            .then().statusCode(400).body("error", equalTo("validation_failed"));
    }
}
