package app.snatter.server.gateway;

import app.snatter.api.model.GatewayChannelCreatedDto;
import app.snatter.api.model.GatewayChannelDeletedDto;
import app.snatter.api.model.GatewayChannelUpdatedDto;
import app.snatter.api.model.GatewayClientFrameDto;
import app.snatter.api.model.GatewayIdentifyDto;
import app.snatter.api.model.GatewayMemberJoinedDto;
import app.snatter.api.model.GatewayMemberUpdatedDto;
import app.snatter.api.model.GatewayMessageCreatedDto;
import app.snatter.api.model.GatewayMessageDeletedDto;
import app.snatter.api.model.GatewayMessageUpdatedDto;
import app.snatter.api.model.GatewayMessagesPurgedDto;
import app.snatter.api.model.GatewayPermissionsChangedDto;
import app.snatter.api.model.GatewayPresenceUpdatedDto;
import app.snatter.api.model.GatewayReadStateUpdatedDto;
import app.snatter.api.model.GatewayReadyDto;
import app.snatter.api.model.GatewayRoleCreatedDto;
import app.snatter.api.model.GatewayRoleDeletedDto;
import app.snatter.api.model.GatewayRoleUpdatedDto;
import app.snatter.api.model.GatewayServerFrameDto;
import app.snatter.api.model.GatewayServerUpdatedDto;
import app.snatter.api.model.GatewayTypingDto;
import app.snatter.api.model.GatewayTypingStartedDto;
import app.snatter.api.model.GatewayVoiceEndedDto;
import app.snatter.api.model.GatewayVoiceRefusedDto;
import app.snatter.api.model.GatewayVoiceStateDeletedDto;
import app.snatter.api.model.GatewayVoiceStateDto;
import app.snatter.api.model.GatewayVoiceStateUpdatedDto;
import app.snatter.api.model.MessageDto;
import app.snatter.api.model.PermissionSetDto;
import app.snatter.api.model.PresenceDto;
import app.snatter.api.model.PresenceStatusDto;
import app.snatter.api.model.ServerInfoDto;
import app.snatter.api.model.VoiceEndReasonDto;
import app.snatter.api.model.VoiceRefusalDto;
import app.snatter.server.account.Account;
import app.snatter.server.account.AccountDtos;
import app.snatter.server.account.AccountEvent;
import app.snatter.server.account.AccountId;
import app.snatter.server.account.AccountRepository;
import app.snatter.server.auth.AccountPrincipal;
import app.snatter.server.auth.AuthService;
import app.snatter.server.auth.Principals;
import app.snatter.server.auth.SessionEvent;
import app.snatter.server.auth.SessionId;
import app.snatter.server.channel.Channel;
import app.snatter.server.channel.ChannelEvent;
import app.snatter.server.channel.ChannelId;
import app.snatter.server.channel.ChannelRepository;
import app.snatter.server.channel.ChannelResource;
import app.snatter.server.message.MessageEvent;
import app.snatter.server.message.MessageResource;
import app.snatter.server.message.ReadState;
import app.snatter.server.message.ReadStateEvent;
import app.snatter.server.message.ReadStateRepository;
import app.snatter.server.message.UserMessage;
import app.snatter.server.protocol.Protocol;
import app.snatter.server.protocol.ProtocolVersion;
import app.snatter.server.role.Permission;
import app.snatter.server.role.PermissionDtos;
import app.snatter.server.role.Role;
import app.snatter.server.role.RoleEvent;
import app.snatter.server.role.RoleId;
import app.snatter.server.role.RoleRepository;
import app.snatter.server.role.RoleResource;
import app.snatter.server.settings.ServerInfoDtos;
import app.snatter.server.settings.ServerSettingsService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongFunction;
import java.util.function.Predicate;
import org.jboss.logging.Logger;

/**
 * The gateway's state and fan-out. Everything that reads or changes
 * connection state runs on one dispatcher thread, in order: connections
 * opening, identifying and closing, and the domain events that committed.
 * So a connection's {@code ready} and the events after it form one
 * consistent sequence, and no locking is needed.
 *
 * <p>Channels, roles and permissions are sent as differences: each client
 * remembers what it was told, and after any change that might affect them
 * the dispatcher recomputes the client's view and sends what changed. Moves,
 * required-role edits and role changes that hide or reveal channels need no
 * special handling.
 *
 * <p>Presence, typing and voice live only here, in memory. A member is online
 * while they have at least one identified connection that is not closing;
 * typing is passed on without being remembered, and clients time it out. A
 * member is in at most one voice channel, held by the connection that joined
 * it, and leaves when that connection goes. Voice states are sent as
 * differences too, of who is in the channels each client can see.
 */
@ApplicationScoped
public class Gateway {

    private static final Logger LOG = Logger.getLogger(Gateway.class);

    /** The least time between two {@code typing} frames passed on for the same channel and connection. */
    private static final long TYPING_INTERVAL_NANOS = Duration.ofSeconds(5).toNanos();

    /** The least time between two {@code voice_state} frames applied for the same connection. */
    private static final long VOICE_INTERVAL_NANOS = Duration.ofMillis(250).toNanos();

    /** How often open connections keep their sessions alive and check they have not ended. */
    private static final Duration KEEP_ALIVE_INTERVAL = Duration.ofMinutes(5);

    /** Frames a connection may have unsent before it counts as too slow and is closed. */
    private static final int MAX_PENDING_FRAMES = 1000;

    private final Principals principals;
    private final AuthService auth;
    private final AccountRepository accounts;
    private final RoleRepository roles;
    private final ChannelRepository channels;
    private final ReadStateRepository readStates;
    private final ServerSettingsService settings;
    private final ServerInfoDtos serverInfo;
    private final GatewayConfig config;
    private final ObjectMapper json;
    private final ObjectWriter frameWriter;
    private final ScheduledExecutorService dispatcher;

    /** Open connections by id; touched only on the dispatcher thread. */
    private final Map<String, Client> clients = new HashMap<>();

    /** Accounts with a live connection; touched only on the dispatcher thread. */
    private final Set<AccountId> online = new HashSet<>();

    /** A pending refresh for each account whose timeout ends later; dispatcher thread only. */
    private final Map<AccountId, ScheduledFuture<?>> timeoutEnds = new HashMap<>();

    /** Who is in voice, in the order they joined, and through which connection; dispatcher thread only. */
    private final Map<AccountId, Voice> voice = new LinkedHashMap<>();

    /** A member in voice and the connection that holds it. */
    private record Voice(Client holder, VoiceState state) {
    }

    public Gateway(Principals principals, AuthService auth, AccountRepository accounts, RoleRepository roles,
                   ChannelRepository channels, ReadStateRepository readStates, ServerSettingsService settings,
                   ServerInfoDtos serverInfo, GatewayConfig config, ObjectMapper json) {
        this.principals = principals;
        this.auth = auth;
        this.accounts = accounts;
        this.roles = roles;
        this.channels = channels;
        this.readStates = readStates;
        this.settings = settings;
        this.serverInfo = serverInfo;
        this.config = config;
        this.json = json;
        this.frameWriter = json.writerFor(GatewayServerFrameDto.class);
        this.dispatcher = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "gateway-dispatcher");
            thread.setDaemon(true);
            return thread;
        });
        long keepAlive = KEEP_ALIVE_INTERVAL.toMillis();
        dispatcher.scheduleWithFixedDelay(guarded(this::keepSessionsAlive), keepAlive, keepAlive, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stop() {
        dispatcher.shutdownNow();
    }

    // --- Connections ----------------------------------------------------------

    void opened(WebSocketConnection connection) {
        run(() -> {
            clients.put(connection.id(), new Client(connection));
            dispatcher.schedule(guarded(() -> identifyTimedOut(connection.id())),
                config.identifyTimeout().toMillis(), TimeUnit.MILLISECONDS);
        });
    }

    void received(WebSocketConnection connection, String text) {
        run(() -> {
            Client client = clients.get(connection.id());
            if (client == null || client.closing) {
                return;
            }
            GatewayClientFrameDto frame;
            try {
                frame = json.readValue(text, GatewayClientFrameDto.class);
            } catch (InvalidTypeIdException e) {
                // A frame type from a newer client is ignored, as the contract promises; one without a type is invalid.
                if (e.getTypeId() == null) {
                    close(client, GatewayClose.INVALID_FRAME);
                }
                return;
            } catch (JsonProcessingException e) {
                close(client, GatewayClose.INVALID_FRAME);
                return;
            }
            switch (frame) {
                case GatewayIdentifyDto identify -> identify(client, identify);
                case GatewayTypingDto typing -> typing(client, typing.getChannelId());
                case GatewayVoiceStateDto voiceState -> voiceStateReceived(client, voiceState);
            }
        });
    }

    void closed(WebSocketConnection connection) {
        run(() -> {
            Client client = clients.remove(connection.id());
            if (client != null && client.identified()) {
                wentAway(client.principal.accountId());
                leaveVoice(client);
            }
        });
    }

    private void identifyTimedOut(String connectionId) {
        Client client = clients.get(connectionId);
        if (client != null && !client.identified() && !client.closing) {
            close(client, GatewayClose.IDENTIFY_TIMEOUT);
        }
    }

    private void identify(Client client, GatewayIdentifyDto identify) {
        if (client.identified()) {
            close(client, GatewayClose.ALREADY_IDENTIFIED);
            return;
        }
        Optional<ProtocolVersion> version = ProtocolVersion.parse(identify.getProtocol());
        if (version.isEmpty()) {
            close(client, GatewayClose.INVALID_FRAME);
            return;
        }
        if (!Protocol.accepts(version.get())) {
            close(client, GatewayClose.CLIENT_OUTDATED);
            return;
        }
        String token = identify.getToken();
        Optional<AccountPrincipal> principal = token == null ? Optional.empty() : principals.authenticate(token);
        if (principal.isEmpty()) {
            close(client, GatewayClose.AUTHENTICATION_FAILED);
            return;
        }
        AccountId accountId = principal.get().accountId();
        Account account = accounts.findById(accountId).orElseThrow();
        // Announced before the client is live, so its own ready carries its presence instead.
        if (online.add(accountId)) {
            broadcast(seq -> new GatewayPresenceUpdatedDto().seq(seq).presence(presence(accountId, PresenceStatusDto.ONLINE)));
        }
        client.principal = principal.get();
        watchTimeout(client.principal);
        client.roles = byId(roles.findAll());
        client.channels = visibleChannels(client.principal, channels.findAll());
        client.voiceStates = visibleVoice(client.channels);
        List<ReadState> reading = startReading(accountId, client.channels.values());
        List<Account> members = accounts.findAll();
        send(client, seq -> new GatewayReadyDto()
            .seq(seq)
            .account(AccountDtos.toDto(account))
            .permissions(permissionSet(client.principal))
            .server(serverInfo.toDto(settings.current()))
            .roles(client.roles.values().stream().map(RoleResource::toDto).toList())
            .members(members.stream().map(AccountDtos::toDto).toList())
            .presences(online.stream().map(id -> presence(id, PresenceStatusDto.ONLINE)).toList())
            .channels(client.channels.values().stream().map(ChannelResource::toDto).toList())
            .readStates(reading.stream().map(MessageResource::toDto).toList())
            .voiceStates(client.voiceStates.values().stream().map(VoiceState::toDto).toList()));
    }

    /**
     * The member's read states in those of the channels that keep messages,
     * starting them at the newest message where they are seen for the first
     * time.
     */
    private List<ReadState> startReading(AccountId accountId, Collection<Channel> seen) {
        List<ChannelId> ids = seen.stream().filter(c -> c.type().hasMessages()).map(Channel::id).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        readStates.startReading(accountId, ids);
        return readStates.find(accountId, ids);
    }

    /**
     * Passes typing on to the other members who can see the channel. Dropped
     * silently, so as not to reveal anything, for a channel the member cannot
     * see or send messages to, and when it comes too soon after the last one.
     */
    private void typing(Client client, ChannelId channelId) {
        if (!client.identified()) {
            close(client, GatewayClose.NOT_IDENTIFIED);
            return;
        }
        Channel channel = client.channels.get(channelId);
        if (channel == null || !channel.type().hasMessages() || !client.principal.has(Permission.SEND_MESSAGES)) {
            return;
        }
        long now = System.nanoTime();
        Long last = client.typingPassedOn.get(channelId);
        if (last != null && now - last < TYPING_INTERVAL_NANOS) {
            return;
        }
        client.typingPassedOn.put(channelId, now);
        AccountId typist = client.principal.accountId();
        for (Client other : liveClients()) {
            if (!other.principal.accountId().equals(typist) && other.channels.containsKey(channelId)) {
                send(other, seq -> new GatewayTypingStartedDto().seq(seq).channelId(channelId).accountId(typist));
            }
        }
    }

    /**
     * Arranges for the account to be refreshed when its timeout ends, since
     * nothing else fires then, replacing any earlier arrangement.
     */
    private void watchTimeout(AccountPrincipal principal) {
        AccountId accountId = principal.accountId();
        ScheduledFuture<?> earlier = timeoutEnds.remove(accountId);
        if (earlier != null) {
            earlier.cancel(false);
        }
        if (principal.timedOutUntil() != null) {
            long delay = Math.max(0, Duration.between(Instant.now(), principal.timedOutUntil()).toMillis() + 1);
            timeoutEnds.put(accountId, dispatcher.schedule(guarded(() -> {
                timeoutEnds.remove(accountId);
                restrictionChanged(accountId);
            }), delay, TimeUnit.MILLISECONDS));
        }
    }

    /**
     * A timeout or mute started, changed or ended: everyone's view of the
     * member first, so a client told what follows from it, such as voice
     * ending, already knows why, then the member's permissions.
     */
    private void restrictionChanged(AccountId accountId) {
        memberUpdated(accountId);
        syncAccess(accountId::equals);
    }

    private void memberUpdated(AccountId accountId) {
        accounts.findById(accountId).ifPresent(account ->
            broadcast(seq -> new GatewayMemberUpdatedDto().seq(seq).member(AccountDtos.toDto(account))));
    }

    /** After one of the account's connections stopped being live: announces it offline if it was the last. */
    private void wentAway(AccountId accountId) {
        boolean stillHere = liveClients().stream().anyMatch(c -> c.principal.accountId().equals(accountId));
        if (!stillHere && online.remove(accountId)) {
            broadcast(seq -> new GatewayPresenceUpdatedDto().seq(seq).presence(presence(accountId, PresenceStatusDto.OFFLINE)));
        }
    }

    /** Extends the sessions of open connections and closes those whose session has ended. */
    private void keepSessionsAlive() {
        Set<SessionId> sessions = new HashSet<>();
        for (Client client : clients.values()) {
            if (client.live()) {
                sessions.add(client.principal.sessionId());
            }
        }
        for (SessionId session : sessions) {
            if (!auth.keepAlive(session)) {
                closeSession(session);
            }
        }
    }

    // --- Voice ----------------------------------------------------------------

    /**
     * Applies the frame, or one that comes too soon after the last waits for
     * the connection's turn, so toggling cannot flood everyone who sees the
     * channel. A waiting frame is replaced by any newer one: unlike typing,
     * none is dropped without the last one being applied, so the client and
     * the server end up agreeing.
     */
    private void voiceStateReceived(Client client, GatewayVoiceStateDto frame) {
        if (!client.identified()) {
            close(client, GatewayClose.NOT_IDENTIFIED);
            return;
        }
        if (client.voicePending != null) {
            client.voicePending = frame;
            return;
        }
        long now = System.nanoTime();
        Long last = client.voiceAppliedAt;
        if (last != null && now - last < VOICE_INTERVAL_NANOS) {
            client.voicePending = frame;
            dispatcher.schedule(guarded(() -> applyPendingVoice(client)),
                VOICE_INTERVAL_NANOS - (now - last), TimeUnit.NANOSECONDS);
            return;
        }
        voiceState(client, frame);
    }

    private void applyPendingVoice(Client client) {
        GatewayVoiceStateDto frame = client.voicePending;
        client.voicePending = null;
        if (frame != null && client.live()) {
            voiceState(client, frame);
        }
    }

    /**
     * Joins, moves, leaves, or changes the member's own mute and deafen,
     * depending on whether this connection is the one in voice; see the
     * contract's {@code GatewayVoiceState}.
     */
    private void voiceState(Client client, GatewayVoiceStateDto frame) {
        client.voiceAppliedAt = System.nanoTime();
        AccountId accountId = client.principal.accountId();
        Voice current = voice.get(accountId);
        boolean here = current != null && current.holder() == client;
        ChannelId channelId = frame.getChannelId();
        if (channelId == null) {
            if (here) {
                voice.remove(accountId);
                syncVoice();
            }
            return;
        }
        VoiceState state = new VoiceState(accountId, channelId,
            Boolean.TRUE.equals(frame.getSelfMuted()), Boolean.TRUE.equals(frame.getSelfDeafened()));
        if (here && channelId.equals(current.state().channelId())) {
            // Mute or deafen only, so the member keeps their place in the channel.
            voice.put(accountId, new Voice(client, state));
            syncVoice();
            return;
        }
        Optional<VoiceRefusalDto> refusal = refusal(client, channelId);
        if (refusal.isPresent()) {
            ChannelId stays = here ? current.state().channelId() : null;
            send(client, seq -> new GatewayVoiceRefusedDto()
                .seq(seq).channelId(channelId).reason(refusal.get()).currentChannelId(stays));
            return;
        }
        if (current != null && !here) {
            endVoice(current, VoiceEndReasonDto.JOINED_ELSEWHERE);
            // Told its own state again, even when nothing about it changed, as word that this connection holds it now.
            client.voiceStates.remove(accountId);
        }
        // Taken out first, so someone who moves is listed last in their new channel.
        voice.remove(accountId);
        voice.put(accountId, new Voice(client, state));
        syncVoice();
    }

    /** Why the connection may not join the channel, if it may not. */
    private Optional<VoiceRefusalDto> refusal(Client client, ChannelId channelId) {
        Channel channel = client.channels.get(channelId);
        if (channel == null) {
            return Optional.of(VoiceRefusalDto.CHANNEL_NOT_FOUND);
        }
        if (!channel.type().hasVoice()) {
            return Optional.of(VoiceRefusalDto.NOT_A_VOICE_CHANNEL);
        }
        if (!client.principal.has(Permission.CONNECT)) {
            return Optional.of(VoiceRefusalDto.FORBIDDEN);
        }
        int limit = channel.voice().userLimit();
        if (limit > 0 && !client.principal.has(Permission.MOVE_MEMBERS)) {
            AccountId self = client.principal.accountId();
            long others = voice.values().stream()
                .filter(v -> v.state().channelId().equals(channelId) && !v.state().accountId().equals(self))
                .count();
            if (others >= limit) {
                return Optional.of(VoiceRefusalDto.CHANNEL_FULL);
            }
        }
        return Optional.empty();
    }

    /** After a connection stopped being live: if it was the one in voice, the member leaves. */
    private void leaveVoice(Client client) {
        AccountId accountId = client.principal.accountId();
        Voice current = voice.get(accountId);
        if (current != null && current.holder() == client) {
            voice.remove(accountId);
            syncVoice();
        }
    }

    /**
     * Takes out of voice those whose channel is gone or hidden from them, and
     * those who no longer hold CONNECT, telling the connection why. Everyone
     * else learns it from the voice differences sent next.
     */
    private void endLostVoice(List<Channel> allChannels) {
        Map<ChannelId, Channel> byId = new HashMap<>();
        for (Channel channel : allChannels) {
            byId.put(channel.id(), channel);
        }
        for (Voice held : List.copyOf(voice.values())) {
            AccountPrincipal member = held.holder().principal;
            Channel channel = byId.get(held.state().channelId());
            VoiceEndReasonDto reason = channel == null || !channel.isVisibleTo(member)
                ? VoiceEndReasonDto.CHANNEL_UNAVAILABLE
                : member.has(Permission.CONNECT) ? null : VoiceEndReasonDto.FORBIDDEN;
            if (reason != null) {
                endVoice(held, reason);
            }
        }
    }

    /**
     * Takes the member out of voice without their asking, telling the
     * connection that held it why. A frame still waiting from that connection
     * is dropped, or it would take voice back.
     */
    private void endVoice(Voice held, VoiceEndReasonDto reason) {
        voice.remove(held.state().accountId());
        held.holder().voicePending = null;
        send(held.holder(), seq -> new GatewayVoiceEndedDto().seq(seq).reason(reason));
    }

    /** Tells every client what changed among those in the voice channels it can see. */
    private void syncVoice() {
        for (Client client : liveClients()) {
            Map<AccountId, VoiceState> now = visibleVoice(client.channels);
            sendVoiceRemovals(client, now);
            sendVoiceUpdates(client, now);
        }
    }

    private void sendVoiceRemovals(Client client, Map<AccountId, VoiceState> now) {
        for (AccountId gone : client.voiceStates.keySet()) {
            if (!now.containsKey(gone)) {
                send(client, seq -> new GatewayVoiceStateDeletedDto().seq(seq).accountId(gone));
            }
        }
    }

    private void sendVoiceUpdates(Client client, Map<AccountId, VoiceState> now) {
        for (VoiceState state : now.values()) {
            if (!state.equals(client.voiceStates.get(state.accountId()))) {
                send(client, seq -> new GatewayVoiceStateUpdatedDto().seq(seq).voiceState(state.toDto()));
            }
        }
        client.voiceStates = now;
    }

    /** Those in voice in the given channels, in the order they joined. */
    private Map<AccountId, VoiceState> visibleVoice(Map<ChannelId, Channel> visible) {
        Map<AccountId, VoiceState> states = new LinkedHashMap<>();
        for (Voice held : voice.values()) {
            if (visible.containsKey(held.state().channelId())) {
                states.put(held.state().accountId(), held.state());
            }
        }
        return states;
    }

    // --- Events ---------------------------------------------------------------
    // Observers run after the change committed and only queue work, so the
    // request that made the change does not wait for the fan-out.

    void onMessage(@Observes(during = TransactionPhase.AFTER_SUCCESS) MessageEvent event) {
        run(() -> dispatchMessage(event));
    }

    void onReadState(@Observes(during = TransactionPhase.AFTER_SUCCESS) ReadStateEvent event) {
        run(() -> {
            ChannelId channelId = event.readState().channelId();
            for (Client client : liveClients()) {
                if (client.principal.accountId().equals(event.accountId()) && client.channels.containsKey(channelId)) {
                    send(client, seq -> new GatewayReadStateUpdatedDto().seq(seq).readState(MessageResource.toDto(event.readState())));
                }
            }
        });
    }

    void onChannel(@Observes(during = TransactionPhase.AFTER_SUCCESS) ChannelEvent event) {
        run(() -> syncAccess(account -> false));
    }

    void onRole(@Observes(during = TransactionPhase.AFTER_SUCCESS) RoleEvent event) {
        run(() -> {
            switch (event) {
                case RoleEvent.Assigned assigned -> roleAssignmentChanged(assigned.accountId());
                case RoleEvent.Unassigned unassigned -> roleAssignmentChanged(unassigned.accountId());
                case RoleEvent.Created _, RoleEvent.Updated _, RoleEvent.Deleted _ -> syncAccess(account -> true);
            }
        });
    }

    void onAccount(@Observes(during = TransactionPhase.AFTER_SUCCESS) AccountEvent event) {
        run(() -> {
            switch (event) {
                case AccountEvent.Registered _ -> accounts.findById(event.accountId()).ifPresent(account ->
                    broadcast(seq -> new GatewayMemberJoinedDto().seq(seq).member(AccountDtos.toDto(account))));
                case AccountEvent.Updated _ -> memberUpdated(event.accountId());
                case AccountEvent.TimeoutChanged changed -> restrictionChanged(changed.accountId());
                case AccountEvent.MuteChanged changed -> restrictionChanged(changed.accountId());
                case AccountEvent.Banned banned -> {
                    for (Client client : liveClients()) {
                        if (client.principal.accountId().equals(banned.accountId())) {
                            close(client, GatewayClose.BANNED);
                        }
                    }
                    memberUpdated(banned.accountId());
                }
                case AccountEvent.Unbanned unbanned -> memberUpdated(unbanned.accountId());
            }
        });
    }

    void onSettings(@Observes(during = TransactionPhase.AFTER_SUCCESS) ServerSettingsService.Changed changed) {
        run(() -> {
            ServerInfoDto after = serverInfo.toDto(changed.after());
            if (!after.equals(serverInfo.toDto(changed.before()))) {
                broadcast(seq -> new GatewayServerUpdatedDto().seq(seq).server(after));
            }
        });
    }

    void onSession(@Observes(during = TransactionPhase.AFTER_SUCCESS) SessionEvent event) {
        run(() -> {
            switch (event) {
                case SessionEvent.Ended ended -> closeSession(ended.sessionId());
            }
        });
    }

    /** Like {@link #restrictionChanged}: the member's new roles first, then what follows from them. */
    private void roleAssignmentChanged(AccountId accountId) {
        memberUpdated(accountId);
        syncAccess(accountId::equals);
    }

    private void dispatchMessage(MessageEvent event) {
        Optional<Channel> found = channels.find(event.channelId());
        if (found.isEmpty()) {
            return;
        }
        Channel channel = found.get();
        MessageDto shared = switch (event) {
            case MessageEvent.Created created -> MessageResource.toDto(created.message());
            case MessageEvent.Updated updated -> MessageResource.toDto(updated.message());
            case MessageEvent.Deleted _, MessageEvent.Purged _ -> null;
        };
        for (Client client : liveClients()) {
            if (!channel.isVisibleTo(client.principal)) {
                continue;
            }
            if (!client.channels.containsKey(channel.id())) {
                // Announce the channel before its first message, whatever order the events came in.
                syncChannels(client, channels.findAll());
            }
            switch (event) {
                case MessageEvent.Created created -> {
                    MessageDto message = created.message() instanceof UserMessage user
                            && client.principal.sessionId().equals(created.origin())
                        ? MessageResource.toUserDto(user).nonce(created.nonce())
                        : shared;
                    send(client, seq -> new GatewayMessageCreatedDto().seq(seq).message(message));
                }
                case MessageEvent.Updated _ -> send(client, seq -> new GatewayMessageUpdatedDto().seq(seq).message(shared));
                case MessageEvent.Deleted deleted -> send(client, seq -> new GatewayMessageDeletedDto()
                    .seq(seq).channelId(deleted.channelId()).messageId(deleted.messageId()));
                case MessageEvent.Purged purged -> send(client, seq -> new GatewayMessagesPurgedDto()
                    .seq(seq)
                    .channelId(purged.channelId())
                    .authorId(purged.authorId())
                    .fromMessageId(purged.fromId())
                    .toMessageId(purged.toId())
                    .deletedAt(purged.deletedAt())
                    .removedByModerator(purged.removedByModerator()));
            }
        }
    }

    /**
     * Brings every client's roles, permissions and channels up to date,
     * resolving permissions again for the accounts {@code reResolve} accepts.
     */
    private void syncAccess(Predicate<AccountId> reResolve) {
        List<Role> allRoles = roles.findAll();
        List<Channel> allChannels = channels.findAll();
        for (Client client : liveClients()) {
            syncRoles(client, allRoles);
            if (reResolve.test(client.principal.accountId())) {
                AccountPrincipal before = client.principal;
                client.principal = principals.refresh(before);
                watchTimeout(client.principal);
                if (before.owner() != client.principal.owner() || !before.permissions().equals(client.principal.permissions())) {
                    send(client, seq -> new GatewayPermissionsChangedDto().seq(seq).permissions(permissionSet(client.principal)));
                }
            }
        }
        endLostVoice(allChannels);
        for (Client client : liveClients()) {
            syncChannels(client, allChannels);
        }
    }

    private void syncRoles(Client client, List<Role> allRoles) {
        Map<RoleId, Role> now = byId(allRoles);
        for (Role role : now.values()) {
            Role before = client.roles.get(role.id());
            if (before == null) {
                send(client, seq -> new GatewayRoleCreatedDto().seq(seq).role(RoleResource.toDto(role)));
            } else if (!before.equals(role)) {
                send(client, seq -> new GatewayRoleUpdatedDto().seq(seq).role(RoleResource.toDto(role)));
            }
        }
        for (RoleId gone : client.roles.keySet()) {
            if (!now.containsKey(gone)) {
                send(client, seq -> new GatewayRoleDeletedDto().seq(seq).roleId(gone));
            }
        }
        client.roles = now;
    }

    private void syncChannels(Client client, List<Channel> allChannels) {
        Map<ChannelId, Channel> now = visibleChannels(client.principal, allChannels);
        // Those in voice leave before their channel does, and arrive after it.
        Map<AccountId, VoiceState> voiceNow = visibleVoice(now);
        sendVoiceRemovals(client, voiceNow);
        List<Channel> appeared = new ArrayList<>();
        for (Channel channel : now.values()) {
            Channel before = client.channels.get(channel.id());
            if (before == null) {
                send(client, seq -> new GatewayChannelCreatedDto().seq(seq).channel(ChannelResource.toDto(channel)));
                appeared.add(channel);
            } else if (!before.equals(channel)) {
                send(client, seq -> new GatewayChannelUpdatedDto().seq(seq).channel(ChannelResource.toDto(channel)));
            }
        }
        for (ChannelId gone : client.channels.keySet()) {
            if (!now.containsKey(gone)) {
                send(client, seq -> new GatewayChannelDeletedDto().seq(seq).channelId(gone));
            }
        }
        client.channels = now;
        sendVoiceUpdates(client, voiceNow);
        for (ReadState state : startReading(client.principal.accountId(), appeared)) {
            send(client, seq -> new GatewayReadStateUpdatedDto().seq(seq).readState(MessageResource.toDto(state)));
        }
    }

    // --- Sending --------------------------------------------------------------

    private void broadcast(LongFunction<GatewayServerFrameDto> frame) {
        for (Client client : liveClients()) {
            send(client, frame);
        }
    }

    /** Numbers the frame for this client and queues it; a client too far behind is closed instead. */
    private void send(Client client, LongFunction<GatewayServerFrameDto> frame) {
        if (client.closing) {
            return;
        }
        if (client.pending.get() >= MAX_PENDING_FRAMES) {
            close(client, GatewayClose.TOO_SLOW);
            return;
        }
        String text;
        try {
            text = frameWriter.writeValueAsString(frame.apply(++client.seq));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise a gateway frame", e);
        }
        client.pending.incrementAndGet();
        client.connection.sendText(text).subscribe().with(
            sent -> client.pending.decrementAndGet(),
            failure -> {
                client.pending.decrementAndGet();
                LOG.debugf(failure, "Gateway send to %s failed", client.connection.id());
            });
    }

    private void close(Client client, GatewayClose reason) {
        boolean wasLive = client.live();
        client.closing = true;
        client.connection.close(reason.toCloseReason()).subscribe().with(
            closed -> { },
            failure -> LOG.debugf(failure, "Closing gateway connection %s failed", client.connection.id()));
        if (wasLive) {
            wentAway(client.principal.accountId());
            leaveVoice(client);
        }
    }

    private void closeSession(SessionId session) {
        for (Client client : liveClients()) {
            if (client.principal.sessionId().equals(session)) {
                close(client, GatewayClose.SESSION_ENDED);
            }
        }
    }

    // --- Helpers --------------------------------------------------------------

    private List<Client> liveClients() {
        return clients.values().stream().filter(Client::live).toList();
    }

    private static Map<RoleId, Role> byId(List<Role> roles) {
        Map<RoleId, Role> map = new LinkedHashMap<>();
        for (Role role : roles) {
            map.put(role.id(), role);
        }
        return map;
    }

    private static Map<ChannelId, Channel> visibleChannels(AccountPrincipal member, List<Channel> all) {
        Map<ChannelId, Channel> visible = new LinkedHashMap<>();
        for (Channel channel : all) {
            if (channel.isVisibleTo(member)) {
                visible.put(channel.id(), channel);
            }
        }
        return visible;
    }

    private static PermissionSetDto permissionSet(AccountPrincipal principal) {
        return new PermissionSetDto().owner(principal.owner()).permissions(PermissionDtos.toDto(principal.permissions()));
    }

    private static PresenceDto presence(AccountId accountId, PresenceStatusDto status) {
        return new PresenceDto().accountId(accountId).status(status);
    }

    private void run(Runnable task) {
        try {
            dispatcher.execute(guarded(task));
        } catch (RejectedExecutionException shuttingDown) {
            // The server is stopping and its connections are closing anyway.
        }
    }

    /** A task that logs its failure instead of dying silently or stopping a periodic schedule. */
    private static Runnable guarded(Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                LOG.error("Gateway task failed", e);
            }
        };
    }
}
