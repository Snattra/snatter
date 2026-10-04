package app.snatter.server.testing;

import app.snatter.client.ApiClient;
import app.snatter.client.api.AccountsApi;
import app.snatter.client.api.AuthApi;
import app.snatter.client.api.BlobsApi;
import app.snatter.client.api.ChannelsApi;
import app.snatter.client.api.InvitesApi;
import app.snatter.client.api.MessagesApi;
import app.snatter.client.api.ModerationApi;
import app.snatter.client.api.RolesApi;
import app.snatter.client.api.ServerApi;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;

/**
 * Clients for the API under test, generated from the contract. Each API has
 * two methods: without arguments for an anonymous caller, and with a user
 * for a caller signed in as them.
 *
 * <p>Calls that the server refuses throw {@link app.snatter.client.ApiException};
 * see {@link ApiAssertions}. The clients are stricter than the contract asks
 * of real clients: a response with a field, enum value or message kind the
 * contract does not have fails the call, so the server cannot drift from
 * the contract unnoticed.
 */
public final class ApiClientFactory {

    /**
     * Shared by every client. Allows as many connections as ConcurrencyTest
     * has requests in flight, and never retries: by default it would retry a
     * 429 once its Retry-After had passed, where tests want to see the 429.
     */
    private static final CloseableHttpClient HTTP = HttpClients.custom()
        .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
            .setMaxConnPerRoute(64)
            .setMaxConnTotal(64)
            .build())
        .disableAutomaticRetries()
        .build();

    private ApiClientFactory() {
    }

    public static AccountsApi accountsApi() {
        return new AccountsApi(client(null));
    }

    public static AccountsApi accountsApi(TestUsers.User user) {
        return new AccountsApi(client(user));
    }

    public static AuthApi authApi() {
        return new AuthApi(client(null));
    }

    public static AuthApi authApi(TestUsers.User user) {
        return new AuthApi(client(user));
    }

    public static BlobsApi blobsApi() {
        return new BlobsApi(client(null));
    }

    public static BlobsApi blobsApi(TestUsers.User user) {
        return new BlobsApi(client(user));
    }

    public static ChannelsApi channelsApi() {
        return new ChannelsApi(client(null));
    }

    public static ChannelsApi channelsApi(TestUsers.User user) {
        return new ChannelsApi(client(user));
    }

    public static InvitesApi invitesApi() {
        return new InvitesApi(client(null));
    }

    public static InvitesApi invitesApi(TestUsers.User user) {
        return new InvitesApi(client(user));
    }

    public static MessagesApi messagesApi() {
        return new MessagesApi(client(null));
    }

    public static MessagesApi messagesApi(TestUsers.User user) {
        return new MessagesApi(client(user));
    }

    public static ModerationApi moderationApi() {
        return new ModerationApi(client(null));
    }

    public static ModerationApi moderationApi(TestUsers.User user) {
        return new ModerationApi(client(user));
    }

    public static RolesApi rolesApi() {
        return new RolesApi(client(null));
    }

    public static RolesApi rolesApi(TestUsers.User user) {
        return new RolesApi(client(user));
    }

    public static ServerApi serverApi() {
        return new ServerApi(client(null));
    }

    public static ServerApi serverApi(TestUsers.User user) {
        return new ServerApi(client(user));
    }

    /** The clients' JSON mapping, as strict as theirs, for JSON that does not come through a client. */
    public static ObjectMapper json() {
        return strict(new ApiClient(HTTP).getObjectMapper());
    }

    /** A client for the running application, which Quarkus has told RestAssured where to find. */
    private static ApiClient client(TestUsers.User user) {
        ApiClient client = new ApiClient(HTTP);
        client.setBasePath(RestAssured.baseURI + ":" + RestAssured.port);
        strict(client.getObjectMapper());
        if (user != null) {
            client.setBearerToken(user.token());
        }
        return client;
    }

    private static ObjectMapper strict(ObjectMapper mapper) {
        return mapper
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE);
    }
}
