package app.snatter.server.gateway;

import static app.snatter.client.model.PermissionDto.SEND_MESSAGES;
import static app.snatter.client.model.VoiceEndReasonDto.CHANNEL_UNAVAILABLE;
import static app.snatter.client.model.VoiceEndReasonDto.CONNECTION_FAILED;
import static app.snatter.client.model.VoiceEndReasonDto.FORBIDDEN;
import static app.snatter.client.model.VoiceEndReasonDto.JOINED_ELSEWHERE;
import static app.snatter.server.testing.ApiClientFactory.channelsApi;
import static app.snatter.server.testing.ApiClientFactory.moderationApi;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.snatter.client.model.ChannelCreateDto;
import app.snatter.client.model.ChannelTypeDto;
import app.snatter.client.model.GatewayChannelCreatedDto;
import app.snatter.client.model.GatewayChannelDeletedDto;
import app.snatter.client.model.GatewayMemberUpdatedDto;
import app.snatter.client.model.GatewayMessageCreatedDto;
import app.snatter.client.model.GatewayTypingDto;
import app.snatter.client.model.GatewayTypingStartedDto;
import app.snatter.client.model.GatewayVoiceAnswerDto;
import app.snatter.client.model.GatewayVoiceEndedDto;
import app.snatter.client.model.GatewayVoiceOfferDto;
import app.snatter.client.model.GatewayVoiceRefusedDto;
import app.snatter.client.model.GatewayVoiceStateDeletedDto;
import app.snatter.client.model.GatewayVoiceStateDto;
import app.snatter.client.model.GatewayVoiceStateUpdatedDto;
import app.snatter.client.model.TimeoutCreateDto;
import app.snatter.client.model.VoiceRefusalDto;
import app.snatter.client.model.VoiceStateDto;
import app.snatter.server.media.StandInBrowser;
import app.snatter.server.testing.GatewayTestClient;
import app.snatter.server.testing.Messages;
import app.snatter.server.testing.TestDataService;
import app.snatter.server.testing.TestUsers;
import io.quarkus.test.junit.QuarkusTest;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class VoiceTest {

    private static final UUID GENERAL_TEXT = UUID.fromString("00000000-0000-7000-8000-000000000101");

    private final TestDataService data = new TestDataService();
    private TestUsers.User owner;
    private UUID lounge;

    @BeforeEach
    void setUpServer() {
        owner = data.setUpServer();
        lounge = data.createChannel(ChannelTypeDto.VOICE, "Lounge");
    }

    private static GatewayVoiceStateDto join(UUID channel) {
        return new GatewayVoiceStateDto().channelId(channel).selfMuted(false).selfDeafened(false);
    }

    private static GatewayVoiceStateDto leave() {
        return new GatewayVoiceStateDto().channelId(null).selfMuted(false).selfDeafened(false);
    }

    private static Predicate<GatewayVoiceStateUpdatedDto> in(TestUsers.User member, UUID channel) {
        return frame -> frame.getVoiceState().getAccountId().equals(member.id())
            && frame.getVoiceState().getChannelId().equals(channel);
    }

    private static Predicate<GatewayVoiceStateDeletedDto> left(TestUsers.User member) {
        return frame -> frame.getAccountId().equals(member.id());
    }

    private static List<UUID> inVoice(GatewayTestClient gateway) {
        return gateway.ready().getVoiceStates().stream().map(VoiceStateDto::getAccountId).toList();
    }

    /** Joins from out of voice and returns why the server refused. */
    private static VoiceRefusalDto refusal(GatewayTestClient gateway, UUID channel) {
        gateway.send(join(channel));
        GatewayVoiceRefusedDto refused =
            gateway.await(GatewayVoiceRefusedDto.class, frame -> frame.getChannelId().equals(channel));
        assertEquals(null, refused.getCurrentChannelId());
        return refused.getReason();
    }

    /** Frames arrive in order, so anything sent before this marker has arrived once it has. */
    private static void awaitMarker(GatewayTestClient gateway, TestUsers.User sender) {
        UUID marker = Messages.send(sender, GENERAL_TEXT, "marker").getId();
        gateway.await(GatewayMessageCreatedDto.class, frame -> Messages.id(frame.getMessage()).equals(marker));
    }

    @Test
    void whoIsInVoiceReachesEveryoneWhoSeesTheChannel() {
        TestUsers.User alice = TestUsers.register();
        TestUsers.User bob = TestUsers.register();
        try (GatewayTestClient aliceGateway = GatewayTestClient.identified(alice.token());
             GatewayTestClient bobGateway = GatewayTestClient.identified(bob.token())) {
            assertEquals(List.of(), inVoice(bobGateway));

            aliceGateway.send(join(lounge));
            VoiceStateDto joined = bobGateway.await(GatewayVoiceStateUpdatedDto.class, in(alice, lounge)).getVoiceState();
            assertFalse(joined.getSelfMuted());
            assertFalse(joined.getSelfDeafened());
            aliceGateway.await(GatewayVoiceStateUpdatedDto.class, in(alice, lounge));

            // A connection opened later finds them in ready.
            try (GatewayTestClient later = GatewayTestClient.identified(bob.token())) {
                assertEquals(List.of(alice.id()), inVoice(later));
            }

            // Deafening mutes too.
            aliceGateway.send(new GatewayVoiceStateDto().channelId(lounge).selfMuted(false).selfDeafened(true));
            VoiceStateDto deafened = bobGateway.await(GatewayVoiceStateUpdatedDto.class,
                frame -> frame.getVoiceState().getSelfDeafened()).getVoiceState();
            assertTrue(deafened.getSelfMuted());

            aliceGateway.send(leave());
            bobGateway.await(GatewayVoiceStateDeletedDto.class, left(alice));
            aliceGateway.await(GatewayVoiceStateDeletedDto.class, left(alice));
        }
    }

    @Test
    void aMemberIsInVoiceOnOneConnectionAtATime() {
        TestUsers.User alice = TestUsers.register();
        TestUsers.User watcher = TestUsers.register();
        UUID den = data.createChannel(ChannelTypeDto.VOICE_TEXT, "Den");
        try (GatewayTestClient first = GatewayTestClient.identified(alice.token());
             GatewayTestClient second = GatewayTestClient.identified(TestUsers.newSession(alice).token());
             GatewayTestClient watcherGateway = GatewayTestClient.identified(watcher.token())) {
            first.send(join(lounge));
            watcherGateway.await(GatewayVoiceStateUpdatedDto.class, in(alice, lounge));
            // The other connection hears where the member is, too.
            second.await(GatewayVoiceStateUpdatedDto.class, in(alice, lounge));

            // Joining on another connection moves the member there.
            second.send(join(den));
            assertEquals(JOINED_ELSEWHERE, first.await(GatewayVoiceEndedDto.class).getReason());
            watcherGateway.await(GatewayVoiceStateUpdatedDto.class, in(alice, den));

            // Only the connection in voice can leave it; the frame after it is the marker.
            first.send(leave());
            first.send(new GatewayTypingDto().channelId(GENERAL_TEXT));
            watcherGateway.await(GatewayTypingStartedDto.class, frame -> frame.getAccountId().equals(alice.id()));
            watcherGateway.assertNone(GatewayVoiceStateDeletedDto.class, left(alice));

            // Closing the connection in voice leaves.
            second.close();
            watcherGateway.await(GatewayVoiceStateDeletedDto.class, left(alice));
        }
    }

    @Test
    void aTakeoverIsConfirmedAndOutlastsWhatTheOtherConnectionHadWaiting() throws InterruptedException {
        TestUsers.User alice = TestUsers.register();
        TestUsers.User watcher = TestUsers.register();
        UUID den = data.createChannel(ChannelTypeDto.VOICE, "Den");
        try (GatewayTestClient first = GatewayTestClient.identified(alice.token());
             GatewayTestClient second = GatewayTestClient.identified(TestUsers.newSession(alice).token());
             GatewayTestClient watcherGateway = GatewayTestClient.identified(watcher.token())) {
            first.send(join(lounge));
            second.await(GatewayVoiceStateUpdatedDto.class, in(alice, lounge));

            // Joining the same channel the same way changes nothing anyone sees, but the joining connection hears it.
            second.send(join(lounge));
            assertEquals(JOINED_ELSEWHERE, first.await(GatewayVoiceEndedDto.class).getReason());
            second.await(GatewayVoiceStateUpdatedDto.class, in(alice, lounge));

            // The second connection's mute waits its turn, as it just joined; the first takes voice over meanwhile.
            second.send(new GatewayVoiceStateDto().channelId(lounge).selfMuted(true).selfDeafened(false));
            first.send(join(den));
            assertEquals(JOINED_ELSEWHERE, second.await(GatewayVoiceEndedDto.class).getReason());
            watcherGateway.await(GatewayVoiceStateUpdatedDto.class, in(alice, den));

            // What the second connection had waiting does not take voice back once its turn comes.
            Thread.sleep(500);
            awaitMarker(watcherGateway, owner);
            watcherGateway.assertNone(GatewayVoiceStateUpdatedDto.class,
                frame -> in(alice, lounge).test(frame) && frame.getVoiceState().getSelfMuted());
        }
    }

    @Test
    void joiningNeedsAVisibleVoiceChannelConnectAndRoom() {
        UUID seers = data.createRole("Seers");
        UUID hidden = data.createChannel(ChannelTypeDto.VOICE, "Hidden", seers);
        UUID small = channelsApi(owner)
            .createChannel(new ChannelCreateDto().type(ChannelTypeDto.VOICE).name("Small").userLimit(1))
            .getId();
        TestUsers.User member = TestUsers.register();
        TestUsers.User other = TestUsers.register();
        TestUsers.User listener = data.registerWithPermissions(SEND_MESSAGES);
        TestUsers.User mod = TestUsers.register();
        data.assignRole(mod.id(), TestDataService.MODERATOR_ROLE);
        try (GatewayTestClient memberGateway = GatewayTestClient.identified(member.token());
             GatewayTestClient otherGateway = GatewayTestClient.identified(other.token());
             GatewayTestClient listenerGateway = GatewayTestClient.identified(listener.token());
             GatewayTestClient modGateway = GatewayTestClient.identified(mod.token())) {
            assertEquals(VoiceRefusalDto.CHANNEL_NOT_FOUND, refusal(memberGateway, UUID.randomUUID()));
            assertEquals(VoiceRefusalDto.CHANNEL_NOT_FOUND, refusal(memberGateway, hidden));
            assertEquals(VoiceRefusalDto.NOT_A_VOICE_CHANNEL, refusal(memberGateway, GENERAL_TEXT));
            assertEquals(VoiceRefusalDto.FORBIDDEN, refusal(listenerGateway, lounge));

            memberGateway.send(join(small));
            otherGateway.await(GatewayVoiceStateUpdatedDto.class, in(member, small));
            otherGateway.send(join(lounge));
            otherGateway.await(GatewayVoiceStateUpdatedDto.class, in(other, lounge));
            // The mute comes right after another frame, so it waits its turn, and the move replaces it.
            otherGateway.send(join(lounge));
            otherGateway.send(new GatewayVoiceStateDto().channelId(lounge).selfMuted(true).selfDeafened(false));
            otherGateway.send(new GatewayVoiceStateDto().channelId(small).selfMuted(true).selfDeafened(false));
            GatewayVoiceRefusedDto full = otherGateway.await(GatewayVoiceRefusedDto.class, frame -> frame.getChannelId().equals(small));
            assertEquals(VoiceRefusalDto.CHANNEL_FULL, full.getReason());
            assertEquals(lounge, full.getCurrentChannelId());
            // Refused, the move still counts its mute.
            otherGateway.await(GatewayVoiceStateUpdatedDto.class,
                frame -> in(other, lounge).test(frame) && frame.getVoiceState().getSelfMuted());

            // MOVE_MEMBERS goes past the limit.
            modGateway.send(join(small));
            modGateway.await(GatewayVoiceStateUpdatedDto.class, in(mod, small));

            // Refused, the member stays where they were.
            try (GatewayTestClient later = GatewayTestClient.identified(owner.token())) {
                assertTrue(later.ready().getVoiceStates().stream()
                    .anyMatch(s -> s.getAccountId().equals(other.id()) && s.getChannelId().equals(lounge)));
            }
        }
    }

    @Test
    void voiceInAPrivateChannelShowsOnlyToThoseWhoSeeIt() {
        UUID seers = data.createRole("Seers");
        UUID den = data.createChannel(ChannelTypeDto.VOICE, "Den", seers);
        TestUsers.User insider = TestUsers.register();
        data.assignRole(insider.id(), seers);
        TestUsers.User outsider = TestUsers.register();
        try (GatewayTestClient insiderGateway = GatewayTestClient.identified(insider.token());
             GatewayTestClient ownerGateway = GatewayTestClient.identified(owner.token());
             GatewayTestClient outsiderGateway = GatewayTestClient.identified(outsider.token())) {
            insiderGateway.send(join(den));
            ownerGateway.await(GatewayVoiceStateUpdatedDto.class, in(insider, den));
            awaitMarker(outsiderGateway, owner);
            outsiderGateway.assertNone(GatewayVoiceStateUpdatedDto.class, in(insider, den));

            // Seeing the channel shows who is in it, after the channel itself.
            data.assignRole(outsider.id(), seers);
            long channelSeq = outsiderGateway.await(GatewayChannelCreatedDto.class,
                frame -> frame.getChannel().getId().equals(den)).getSeq();
            assertTrue(outsiderGateway.await(GatewayVoiceStateUpdatedDto.class, in(insider, den)).getSeq() > channelSeq);

            // Moving to a channel they cannot see is leaving, to them.
            UUID back = data.createRole("Back");
            UUID backroom = data.createChannel(ChannelTypeDto.VOICE, "Backroom", back);
            data.assignRole(insider.id(), back);
            insiderGateway.send(join(backroom));
            ownerGateway.await(GatewayVoiceStateUpdatedDto.class, in(insider, backroom));
            outsiderGateway.await(GatewayVoiceStateDeletedDto.class, left(insider));
        }
    }

    @Test
    void losingTheChannelOrConnectEndsVoice() {
        UUID seers = data.createRole("Seers");
        UUID den = data.createChannel(ChannelTypeDto.VOICE, "Den", seers);
        TestUsers.User member = TestUsers.register();
        data.assignRole(member.id(), seers);
        TestUsers.User watcher = TestUsers.register();
        try (GatewayTestClient memberGateway = GatewayTestClient.identified(member.token());
             GatewayTestClient ownerGateway = GatewayTestClient.identified(owner.token());
             GatewayTestClient watcherGateway = GatewayTestClient.identified(watcher.token())) {
            // Hidden from them, the channel takes them out of voice.
            memberGateway.send(join(den));
            ownerGateway.await(GatewayVoiceStateUpdatedDto.class, in(member, den));
            data.unassignRole(member.id(), seers);
            assertEquals(CHANNEL_UNAVAILABLE, memberGateway.await(GatewayVoiceEndedDto.class).getReason());
            ownerGateway.await(GatewayVoiceStateDeletedDto.class, left(member));

            // A timeout takes CONNECT away.
            memberGateway.send(join(lounge));
            watcherGateway.await(GatewayVoiceStateUpdatedDto.class, in(member, lounge));
            moderationApi(owner).timeOutMember(member.id(), new TimeoutCreateDto().durationSeconds(600));
            GatewayVoiceEndedDto ended = memberGateway.await(GatewayVoiceEndedDto.class);
            assertEquals(FORBIDDEN, ended.getReason());
            // The member hears of their timeout first, so the client can say why voice ended.
            assertTrue(memberGateway.await(GatewayMemberUpdatedDto.class,
                frame -> frame.getMember().getId().equals(member.id()) && frame.getMember().getTimedOutUntil() != null)
                .getSeq() < ended.getSeq());
            watcherGateway.await(GatewayVoiceStateDeletedDto.class, left(member));
            moderationApi(owner).endTimeout(member.id());

            // Deleting the channel: those in it leave before it goes.
            memberGateway.send(join(lounge));
            watcherGateway.await(GatewayVoiceStateUpdatedDto.class, in(member, lounge));
            channelsApi(owner).deleteChannel(lounge);
            assertEquals(CHANNEL_UNAVAILABLE, memberGateway.await(GatewayVoiceEndedDto.class).getReason());
            long leftSeq = watcherGateway.await(GatewayVoiceStateDeletedDto.class, left(member)).getSeq();
            assertTrue(watcherGateway.await(GatewayChannelDeletedDto.class,
                frame -> frame.getChannelId().equals(lounge)).getSeq() > leftSeq);
        }
    }

    @Test
    void togglingQuicklyReachesOthersAsItsLastState() {
        TestUsers.User alice = TestUsers.register();
        TestUsers.User watcher = TestUsers.register();
        try (GatewayTestClient aliceGateway = GatewayTestClient.identified(alice.token());
             GatewayTestClient watcherGateway = GatewayTestClient.identified(watcher.token())) {
            aliceGateway.send(join(lounge));
            watcherGateway.await(GatewayVoiceStateUpdatedDto.class, in(alice, lounge));

            for (int i = 0; i < 20; i++) {
                aliceGateway.send(new GatewayVoiceStateDto().channelId(lounge).selfMuted(i % 2 == 0).selfDeafened(false));
            }
            aliceGateway.send(new GatewayVoiceStateDto().channelId(lounge).selfMuted(true).selfDeafened(true));
            int between = 0;
            while (!watcherGateway.await(GatewayVoiceStateUpdatedDto.class, in(alice, lounge)).getVoiceState().getSelfDeafened()) {
                between++;
            }
            assertTrue(between < 5, "the others got " + between + " states on the way to the last");
        }
    }

    @Test
    void joiningOffersAVoiceConnectionThatTheAnswerBringsUp() throws Exception {
        TestUsers.User alice = TestUsers.register();
        UUID den = data.createChannel(ChannelTypeDto.VOICE, "Den");
        try (GatewayTestClient gateway = GatewayTestClient.identified(alice.token())) {
            gateway.send(join(lounge));
            String offer = gateway.await(GatewayVoiceOfferDto.class).getSdp();
            try (StandInBrowser browser = new StandInBrowser(offer)) {
                gateway.send(new GatewayVoiceAnswerDto().sdp(browser.answer()));
                browser.connect();
            }

            // Moving to another channel keeps the connection, so nothing is offered again.
            gateway.send(join(den));
            gateway.await(GatewayVoiceStateUpdatedDto.class, in(alice, den));
            awaitMarker(gateway, owner);
            gateway.assertNone(GatewayVoiceOfferDto.class, frame -> true);
        }
    }

    @Test
    void anAnswerTheServerCannotUseEndsVoice() {
        TestUsers.User alice = TestUsers.register();
        TestUsers.User watcher = TestUsers.register();
        try (GatewayTestClient aliceGateway = GatewayTestClient.identified(alice.token());
             GatewayTestClient watcherGateway = GatewayTestClient.identified(watcher.token())) {
            aliceGateway.send(join(lounge));
            aliceGateway.await(GatewayVoiceOfferDto.class);

            aliceGateway.send(new GatewayVoiceAnswerDto().sdp("v=0\r\n"));
            assertEquals(CONNECTION_FAILED, aliceGateway.await(GatewayVoiceEndedDto.class).getReason());
            watcherGateway.await(GatewayVoiceStateDeletedDto.class, left(alice));
        }
    }

    @Test
    void aConnectionThatFailsEndsVoice() throws Exception {
        TestUsers.User alice = TestUsers.register();
        TestUsers.User watcher = TestUsers.register();
        try (GatewayTestClient aliceGateway = GatewayTestClient.identified(alice.token());
             GatewayTestClient watcherGateway = GatewayTestClient.identified(watcher.token())) {
            aliceGateway.send(join(lounge));
            try (StandInBrowser browser = new StandInBrowser(aliceGateway.await(GatewayVoiceOfferDto.class).getSdp())) {
                // The answer names a certificate other than the one the browser shows, so DTLS fails.
                String another = String.join(":", Collections.nCopies(32, "00"));
                aliceGateway.send(new GatewayVoiceAnswerDto()
                    .sdp(browser.answer().replaceFirst("(a=fingerprint:sha-256 )\\S+", "$1" + another)));
                assertThrows(Exception.class, browser::connect);
            }
            assertEquals(CONNECTION_FAILED, aliceGateway.await(GatewayVoiceEndedDto.class).getReason());
            watcherGateway.await(GatewayVoiceStateDeletedDto.class, left(alice));
        }
    }
}
