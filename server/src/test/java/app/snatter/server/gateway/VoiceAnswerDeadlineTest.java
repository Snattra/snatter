package app.snatter.server.gateway;

import static app.snatter.client.model.VoiceEndReasonDto.CONNECTION_FAILED;
import static org.junit.jupiter.api.Assertions.assertEquals;

import app.snatter.client.model.ChannelTypeDto;
import app.snatter.client.model.GatewayVoiceAnswerDto;
import app.snatter.client.model.GatewayVoiceEndedDto;
import app.snatter.client.model.GatewayVoiceOfferDto;
import app.snatter.client.model.GatewayVoiceStateDto;
import app.snatter.server.media.StandInBrowser;
import app.snatter.server.testing.GatewayTestClient;
import app.snatter.server.testing.TestDataService;
import app.snatter.server.testing.TestUsers;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * An offer must be answered in time, or voice ends. A deadline of its own,
 * as other tests join voice without answering and would be ended by one
 * this short.
 */
@QuarkusTest
@TestProfile(VoiceAnswerDeadlineTest.ShortDeadline.class)
class VoiceAnswerDeadlineTest {

    public static final class ShortDeadline implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("snatter.gateway.voice-answer-timeout", "PT1S");
        }
    }

    private final TestDataService data = new TestDataService();

    private static GatewayVoiceStateDto join(UUID channel) {
        return new GatewayVoiceStateDto().channelId(channel).selfMuted(false).selfDeafened(false);
    }

    @Test
    void anOfferLeftUnansweredOnceConnectedEndsVoice() throws Exception {
        data.setUpServer();
        UUID lounge = data.createChannel(ChannelTypeDto.VOICE, "Lounge");
        TestUsers.User alice = TestUsers.register();
        TestUsers.User bob = TestUsers.register();
        try (GatewayTestClient aliceGateway = GatewayTestClient.identified(alice.token());
             GatewayTestClient bobGateway = GatewayTestClient.identified(bob.token())) {
            aliceGateway.send(join(lounge));
            try (StandInBrowser browser = new StandInBrowser(aliceGateway.await(GatewayVoiceOfferDto.class).getSdp())) {
                aliceGateway.send(new GatewayVoiceAnswerDto().sdp(browser.answer()));
                browser.connect();

                // Bob comes, and alice never answers the offer of his voice.
                bobGateway.send(join(lounge));
                aliceGateway.await(GatewayVoiceOfferDto.class, frame -> frame.getSdp().contains("a=msid:" + bob.id()));
                assertEquals(CONNECTION_FAILED, aliceGateway.await(GatewayVoiceEndedDto.class).getReason());
            }
        }
    }
}
