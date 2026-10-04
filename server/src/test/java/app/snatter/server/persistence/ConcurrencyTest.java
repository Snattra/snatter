package app.snatter.server.persistence;

import static app.snatter.server.testing.ApiAssertions.errorOf;
import static app.snatter.server.testing.ApiClientFactory.authApi;
import static app.snatter.server.testing.ApiClientFactory.channelsApi;
import static app.snatter.server.testing.ApiClientFactory.invitesApi;
import static app.snatter.server.testing.ApiClientFactory.messagesApi;
import static app.snatter.server.testing.TestUsers.DEFAULT_PASSWORD;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.snatter.client.ApiException;
import app.snatter.client.model.ChannelCreateDto;
import app.snatter.client.model.ChannelDto;
import app.snatter.client.model.ChannelTypeDto;
import app.snatter.client.model.ChannelUpdateDto;
import app.snatter.client.model.InviteCreateDto;
import app.snatter.client.model.MessageCreateDto;
import app.snatter.client.model.MessageDto;
import app.snatter.client.model.ReadStateUpdateDto;
import app.snatter.client.model.RegisterRequestDto;
import app.snatter.client.model.UserMessageDto;
import app.snatter.server.testing.Messages;
import app.snatter.server.testing.TestDataService;
import app.snatter.server.testing.TestUsers;
import io.quarkus.test.junit.QuarkusTest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;
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

    /** The outcome of a call that succeeded; see {@link #outcome}. */
    private static final String OK = "ok";

    private final TestDataService data = new TestDataService();
    private TestUsers.User owner;

    @BeforeEach
    void setUpServer() {
        owner = data.setUpServer();
    }

    @Test
    void messagesFromManyMembersAtOnceAllArriveInOrder() throws Exception {
        UUID channel = data.createChannel(ChannelTypeDto.TEXT, "race");
        List<TestUsers.User> members = IntStream.range(0, 20).mapToObj(i -> TestUsers.register()).toList();
        int perMember = 25;

        // Each member sends its messages one after another; the members race each other.
        List<Callable<List<String>>> senders = members.stream().<Callable<List<String>>>map(m -> () -> {
            List<String> outcomes = new ArrayList<>();
            for (int i = 0; i < perMember; i++) {
                MessageCreateDto message = new MessageCreateDto().content(m.username() + " " + i);
                outcomes.add(outcome(() -> messagesApi(m).createMessage(channel, message)));
            }
            return outcomes;
        }).toList();
        List<String> outcomes = runTogether(senders).stream().flatMap(List::stream).toList();
        assertEquals(Collections.nCopies(members.size() * perMember, OK), outcomes);

        List<UserMessageDto> messages = allMessages(members.getFirst(), channel);
        assertEquals(members.size() * perMember, messages.size());
        // Version 7 ids order as text, which is not how UUID compares them.
        List<String> ids = messages.stream().map(m -> m.getId().toString()).toList();
        assertEquals(ids.size(), new HashSet<>(ids).size(), "ids are unique");
        for (int i = 1; i < ids.size(); i++) {
            assertTrue(ids.get(i - 1).compareTo(ids.get(i)) < 0, "ids increase: " + ids.get(i - 1) + " then " + ids.get(i));
        }
        // Each member's messages appear in the order that member sent them.
        for (TestUsers.User member : members) {
            List<String> own = messages.stream()
                .map(UserMessageDto::getContent)
                .filter(c -> c.startsWith(member.username() + " "))
                .toList();
            assertEquals(IntStream.range(0, perMember).mapToObj(i -> member.username() + " " + i).toList(), own);
        }
    }

    @Test
    void readMarkersRacingEachOtherOnlyMoveForward() throws Exception {
        UUID channel = data.createChannel(ChannelTypeDto.TEXT, "marker");
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            ids.add(Messages.send(owner, channel, "m" + i).getId());
        }
        TestUsers.User reader = TestUsers.register();
        List<UUID> shuffled = new ArrayList<>(ids);
        Collections.shuffle(shuffled);

        List<String> outcomes = runTogether(shuffled.stream().<Callable<String>>map(id -> () ->
            outcome(() -> messagesApi(reader).markRead(channel, new ReadStateUpdateDto().lastReadMessageId(id)))).toList());
        assertEquals(Collections.nCopies(ids.size(), OK), outcomes);

        // Marking the oldest message leaves the marker at the newest.
        UUID marker = messagesApi(reader).markRead(channel, new ReadStateUpdateDto().lastReadMessageId(ids.getFirst()))
            .getLastReadMessageId();
        assertEquals(ids.getLast(), marker);
    }

    @Test
    void anInviteIsNeverUsedMoreThanItsLimit() throws Exception {
        String code = invitesApi(owner).createInvite(new InviteCreateDto().maxUses(5)).getCode();
        // Challenges are solved beforehand, so the registrations themselves race.
        List<RegisterRequestDto> registrations = IntStream.range(0, 20)
            .mapToObj(i -> TestUsers.registration("invited_" + i, DEFAULT_PASSWORD, null).inviteCode(code))
            .toList();

        List<String> outcomes = runTogether(registrations.stream()
            .<Callable<String>>map(registration -> () -> outcome(() -> authApi().register(registration))).toList());

        assertEquals(5, outcomes.stream().filter(OK::equals).count(), tally(outcomes));
        assertTrue(outcomes.stream().allMatch(o -> o.equals(OK) || o.equals("403 invite_invalid")), tally(outcomes));
        assertEquals(5, invitesApi(owner).listInvites().stream()
            .filter(invite -> code.equals(invite.getCode())).findFirst().orElseThrow().getUses());
    }

    @Test
    void oneUsernameRegisteredManyTimesAtOnceIsTakenOnce() throws Exception {
        String name = "twin";
        List<RegisterRequestDto> registrations = IntStream.range(0, 10).mapToObj(i -> TestUsers.registration(
            i % 2 == 0 ? name : name.toUpperCase(Locale.ROOT), DEFAULT_PASSWORD, null)).toList();

        List<String> outcomes = runTogether(registrations.stream()
            .<Callable<String>>map(registration -> () -> outcome(() -> authApi().register(registration))).toList());

        assertEquals(1, outcomes.stream().filter(OK::equals).count(), tally(outcomes));
        assertTrue(outcomes.stream().allMatch(o -> o.equals(OK) || o.equals("409 username_taken")), tally(outcomes));
    }

    @Test
    void channelsCreatedMovedAndNamedAtOnceKeepOneConsistentList() throws Exception {
        List<UUID> existing = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            existing.add(data.createChannel(ChannelTypeDto.TEXT, "list " + i));
        }
        String clash = "clash";

        List<Callable<String>> work = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            ChannelCreateDto channel = new ChannelCreateDto().type(ChannelTypeDto.TEXT).name("new " + i);
            work.add(() -> outcome(() -> channelsApi(owner).createChannel(channel)));
        }
        for (int i = 0; i < 6; i++) {
            // The same name in different cases; only one may have it.
            ChannelCreateDto channel = new ChannelCreateDto().type(ChannelTypeDto.TEXT)
                .name(i % 2 == 0 ? clash : clash.toUpperCase(Locale.ROOT));
            work.add(() -> outcome(() -> channelsApi(owner).createChannel(channel)));
        }
        for (int i = 0; i < 12; i++) {
            UUID id = existing.get(i % existing.size());
            ChannelUpdateDto move = new ChannelUpdateDto().position((i * 7) % 20);
            work.add(() -> outcome(() -> channelsApi(owner).updateChannel(id, move)));
        }
        Collections.shuffle(work);

        List<String> outcomes = runTogether(work);

        assertTrue(outcomes.stream().allMatch(o -> o.equals(OK) || o.equals("409 channel_name_taken")), tally(outcomes));
        List<ChannelDto> channels = channelsApi(owner).listChannels();
        List<Integer> positions = channels.stream().map(ChannelDto::getPosition).sorted().toList();
        assertEquals(IntStream.range(0, channels.size()).boxed().toList(), positions, "positions are 0..n-1");
        Set<String> names = new HashSet<>();
        for (ChannelDto c : channels) {
            assertTrue(names.add(c.getName().toLowerCase(Locale.ROOT)), "names are unique: " + c.getName());
        }
        assertEquals(1, channels.stream().filter(c -> clash.equalsIgnoreCase(c.getName())).count());
    }

    // --- Helpers -------------------------------------------------------------

    /** {@link #OK}, or the status and error code the call was refused with. */
    private static String outcome(Runnable call) {
        try {
            call.run();
            return OK;
        } catch (ApiException e) {
            return e.getCode() + " " + errorOf(e).getError();
        }
    }

    private static String tally(List<String> outcomes) {
        return "outcomes: " + outcomes.stream().collect(groupingBy(o -> o, TreeMap::new, counting()));
    }

    /** Every user message in the channel, oldest first, paging back from the newest. */
    private static List<UserMessageDto> allMessages(TestUsers.User reader, UUID channel) {
        List<MessageDto> all = new ArrayList<>();
        UUID before = null;
        while (true) {
            List<MessageDto> page = messagesApi(reader).listMessages(channel, before, null, 100);
            if (page.isEmpty()) {
                break;
            }
            all.addAll(0, page);
            before = Messages.id(page.getFirst());
        }
        return all.stream().filter(UserMessageDto.class::isInstance).map(UserMessageDto.class::cast).toList();
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
}
