package app.snatter.server.persistence;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.snatter.server.testing.TestUsers;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Many requests at once, checking what the database has to keep true while
 * they race: SQLite runs one write transaction at a time, and none of them
 * may fail or see another half done. Every request here must succeed or fail
 * the way the API says, never with a server error.
 */
@QuarkusTest
class ConcurrencyTest {

    /** Requests in flight at once. */
    private static final int PARALLEL = 32;

    private static RequestSpecification as(String token) {
        return given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON);
    }

    /** Some tests register without TestUsers.register, so the server must be set up first. */
    @BeforeEach
    void bootstrap() {
        TestUsers.ownerToken();
    }

    @Test
    void messagesFromManyMembersAtOnceAllArriveInOrder() throws Exception {
        String channel = createChannel("race " + suffix());
        List<TestUsers.User> members = IntStream.range(0, 20).mapToObj(i -> TestUsers.register()).toList();
        int perMember = 25;

        // Each member sends its messages one after another; the members race each other.
        List<Callable<List<Integer>>> senders = members.stream().<Callable<List<Integer>>>map(m -> () -> {
            List<Integer> statuses = new ArrayList<>();
            for (int i = 0; i < perMember; i++) {
                statuses.add(as(m.token()).body(Map.of("content", m.username() + " " + i))
                    .post("/api/v1/channels/" + channel + "/messages").statusCode());
            }
            return statuses;
        }).toList();
        List<Integer> statuses = runTogether(senders).stream().flatMap(List::stream).toList();
        assertEquals(Collections.nCopies(members.size() * perMember, 201), statuses);

        List<Map<String, Object>> messages = allMessages(members.getFirst().token(), channel);
        assertEquals(members.size() * perMember, messages.size());
        List<String> ids = messages.stream().map(m -> (String) m.get("id")).toList();
        assertEquals(ids.size(), new HashSet<>(ids).size(), "ids are unique");
        for (int i = 1; i < ids.size(); i++) {
            assertTrue(ids.get(i - 1).compareTo(ids.get(i)) < 0, "ids increase: " + ids.get(i - 1) + " then " + ids.get(i));
        }
        // Each member's messages appear in the order that member sent them.
        for (TestUsers.User member : members) {
            List<String> own = messages.stream()
                .map(m -> (String) m.get("content"))
                .filter(c -> c.startsWith(member.username() + " "))
                .toList();
            assertEquals(IntStream.range(0, perMember).mapToObj(i -> member.username() + " " + i).toList(), own);
        }
    }

    @Test
    void readMarkersRacingEachOtherOnlyMoveForward() throws Exception {
        String channel = createChannel("marker " + suffix());
        String owner = TestUsers.ownerToken();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            ids.add(as(owner).body(Map.of("content", "m" + i)).post("/api/v1/channels/" + channel + "/messages")
                .then().statusCode(201).extract().path("id"));
        }
        TestUsers.User reader = TestUsers.register();
        List<String> shuffled = new ArrayList<>(ids);
        Collections.shuffle(shuffled);

        List<Integer> statuses = runTogether(shuffled.stream().<Callable<Integer>>map(id -> () ->
            as(reader.token()).body(Map.of("lastReadMessageId", id))
                .put("/api/v1/channels/" + channel + "/read-state").statusCode()).toList());
        assertEquals(Collections.nCopies(ids.size(), 200), statuses);

        // Marking the oldest message leaves the marker at the newest.
        String marker = as(reader.token()).body(Map.of("lastReadMessageId", ids.getFirst()))
            .put("/api/v1/channels/" + channel + "/read-state").then().statusCode(200).extract().path("lastReadMessageId");
        assertEquals(ids.getLast(), marker);
    }

    @Test
    void anInviteIsNeverUsedMoreThanItsLimit() throws Exception {
        String code = as(TestUsers.ownerToken()).body(Map.of("maxUses", 5))
            .post("/api/v1/invites").then().statusCode(201).extract().path("code");
        // Challenges are solved beforehand, so the registrations themselves race.
        List<Map<String, Object>> bodies = IntStream.range(0, 20).mapToObj(i -> {
            Map<String, Object> body = TestUsers.registration("inv_" + suffix(), TestUsers.DEFAULT_PASSWORD, null);
            body.put("inviteCode", code);
            return body;
        }).toList();

        List<Response> responses = runTogether(bodies.stream().<Callable<Response>>map(b -> () -> TestUsers.registerRaw(b)).toList());

        assertEquals(5, responses.stream().filter(r -> r.statusCode() == 201).count(), statusesOf(responses));
        assertTrue(responses.stream().allMatch(r -> r.statusCode() == 201
            || (r.statusCode() == 403 && "invite_invalid".equals(r.path("error")))), statusesOf(responses));
        List<Map<String, Object>> invites = as(TestUsers.ownerToken()).get("/api/v1/invites").then().statusCode(200).extract().path("");
        assertEquals(5, invites.stream().filter(i -> code.equals(i.get("code"))).findFirst().orElseThrow().get("uses"));
    }

    @Test
    void oneUsernameRegisteredManyTimesAtOnceIsTakenOnce() throws Exception {
        String name = "twin_" + suffix();
        List<Map<String, Object>> bodies = IntStream.range(0, 10).mapToObj(i -> TestUsers.registration(
            i % 2 == 0 ? name : name.toUpperCase(Locale.ROOT), TestUsers.DEFAULT_PASSWORD, null)).toList();

        List<Response> responses = runTogether(bodies.stream().<Callable<Response>>map(b -> () -> TestUsers.registerRaw(b)).toList());

        assertEquals(1, responses.stream().filter(r -> r.statusCode() == 201).count(), statusesOf(responses));
        assertTrue(responses.stream().allMatch(r -> r.statusCode() == 201
            || (r.statusCode() == 409 && "username_taken".equals(r.path("error")))), statusesOf(responses));
    }

    @Test
    void channelsCreatedMovedAndNamedAtOnceKeepOneConsistentList() throws Exception {
        String owner = TestUsers.ownerToken();
        List<String> existing = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            existing.add(createChannel("list " + suffix()));
        }
        String clash = "clash " + suffix();

        List<Callable<Response>> work = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            String name = "new " + suffix();
            work.add(() -> as(owner).body(Map.of("type", "text", "name", name)).post("/api/v1/channels"));
        }
        for (int i = 0; i < 6; i++) {
            // The same name in different cases; only one may have it.
            String name = i % 2 == 0 ? clash : clash.toUpperCase(Locale.ROOT);
            work.add(() -> as(owner).body(Map.of("type", "text", "name", name)).post("/api/v1/channels"));
        }
        for (int i = 0; i < 12; i++) {
            String id = existing.get(i % existing.size());
            int position = (i * 7) % 20;
            work.add(() -> as(owner).body(Map.of("position", position)).patch("/api/v1/channels/" + id));
        }
        Collections.shuffle(work);

        List<Response> responses = runTogether(work);

        assertTrue(responses.stream().allMatch(r -> r.statusCode() == 200 || r.statusCode() == 201
            || (r.statusCode() == 409 && "channel_name_taken".equals(r.path("error")))), statusesOf(responses));
        List<Map<String, Object>> channels = as(owner).get("/api/v1/channels").then().statusCode(200).extract().path("");
        List<Integer> positions = channels.stream().map(c -> (Integer) c.get("position")).sorted().toList();
        assertEquals(IntStream.range(0, channels.size()).boxed().toList(), positions, "positions are 0..n-1");
        Set<String> names = new HashSet<>();
        for (Map<String, Object> c : channels) {
            assertTrue(names.add(((String) c.get("name")).toLowerCase(Locale.ROOT)), "names are unique: " + c.get("name"));
        }
        assertEquals(1, channels.stream().filter(c -> clash.equalsIgnoreCase((String) c.get("name"))).count());
    }

    // --- Helpers -------------------------------------------------------------

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static String createChannel(String name) {
        return as(TestUsers.ownerToken()).body(Map.of("type", "text", "name", name))
            .post("/api/v1/channels").then().statusCode(201).extract().path("id");
    }

    /** Every message in the channel, oldest first, paging back from the newest. */
    private static List<Map<String, Object>> allMessages(String token, String channel) {
        List<Map<String, Object>> all = new ArrayList<>();
        String before = null;
        while (true) {
            String query = "?limit=100" + (before == null ? "" : "&before=" + before);
            List<Map<String, Object>> page = as(token).get("/api/v1/channels/" + channel + "/messages" + query)
                .then().statusCode(200).extract().path("");
            if (page.isEmpty()) {
                break;
            }
            all.addAll(0, page);
            before = (String) page.getFirst().get("id");
        }
        return all.stream().filter(m -> "user".equals(m.get("kind"))).toList();
    }

    /** Starts every task at the same moment on {@link #PARALLEL} threads and returns their results in order. */
    private static <T> List<T> runTogether(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(PARALLEL);
        try {
            CountDownLatch ready = new CountDownLatch(Math.min(PARALLEL, tasks.size()));
            CountDownLatch go = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            ready.await(30, TimeUnit.SECONDS);
            go.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(2, TimeUnit.MINUTES));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private static String statusesOf(List<Response> responses) {
        Map<String, Integer> counts = new HashMap<>();
        for (Response r : responses) {
            String error = r.statusCode() >= 400 && r.contentType().contains("json") ? " " + r.path("error") : "";
            counts.merge(r.statusCode() + error, 1, Integer::sum);
        }
        return "statuses: " + counts;
    }
}
