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
import app.snatter.api.model.GatewayPermissionsChangedDto;
import app.snatter.api.model.GatewayPresenceUpdatedDto;
import app.snatter.api.model.GatewayReadyDto;
import app.snatter.api.model.GatewayRoleCreatedDto;
import app.snatter.api.model.GatewayRoleDeletedDto;
import app.snatter.api.model.GatewayRoleUpdatedDto;
import app.snatter.api.model.GatewayServerFrameDto;
import app.snatter.api.model.GatewayServerUpdatedDto;
import app.snatter.api.model.GatewayTypingDto;
import app.snatter.api.model.GatewayTypingStartedDto;
import app.snatter.api.model.MessageDto;
import app.snatter.api.model.PermissionSetDto;
import app.snatter.api.model.PresenceDto;
import app.snatter.api.model.PresenceStatusDto;
import app.snatter.api.model.ServerInfoDto;
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
import app.snatter.server.message.UserMessage;
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
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import java.time.Duration;
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
 * <p>Presence and typing live only here, in memory. A member is online while
 * they have at least one identified connection that is not closing; typing
 * is passed on without being remembered, and clients time it out.
 */
@ApplicationScoped
public class Gateway {

    private static final Logger LOG = Logger.getLogger(Gateway.class);

    /** The least time between two {@code typing} frames passed on for the same channel and connection. */
    private static final long TYPING_INTERVAL_NANOS = Duration.ofSeconds(5).toNanos();

    private final Principals principals;
    private final AuthService auth;
    private final AccountRepository accounts;
    private final RoleRepository roles;
    private final ChannelRepository channels;
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

    public Gateway(Principals principals, AuthService auth, AccountRepository accounts, RoleRepository roles,
                   ChannelRepository channels, ServerSettingsService settings, ServerInfoDtos serverInfo,
                   GatewayConfig config, ObjectMapper json) {
        this.principals = principals;
        this.auth = auth;
        this.accounts = accounts;
        this.roles = roles;
        this.channels = channels;
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
        long keepAlive = config.keepAliveInterval().toMillis();
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
            } catch (JsonProcessingException e) {
                close(client, GatewayClose.INVALID_FRAME);
                return;
            }
            switch (frame) {
                case GatewayIdentifyDto identify -> identify(client, identify.getToken());
                case GatewayTypingDto typing -> typing(client, typing.getChannelId());
            }
        });
    }

    void closed(WebSocketConnection connection) {
        run(() -> {
            Client client = clients.remove(connection.id());
            if (client != null && client.identified()) {
                wentAway(client.principal.accountId());
            }
        });
    }

    private void identifyTimedOut(String connectionId) {
        Client client = clients.get(connectionId);
        if (client != null && !client.identified() && !client.closing) {
            close(client, GatewayClose.IDENTIFY_TIMEOUT);
        }
    }

    private void identify(Client client, String token) {
        if (client.identified()) {
            close(client, GatewayClose.ALREADY_IDENTIFIED);
            return;
        }
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
        client.roles = byId(roles.findAll());
        client.channels = visibleChannels(client.principal, channels.findAll());
        List<Account> members = accounts.findAll();
        send(client, seq -> new GatewayReadyDto()
            .seq(seq)
            .account(AccountDtos.toDto(account))
            .permissions(permissionSet(client.principal))
            .server(serverInfo.toDto(settings.current()))
            .roles(client.roles.values().stream().map(RoleResource::toDto).toList())
            .members(members.stream().map(AccountDtos::toDto).toList())
            .presences(online.stream().map(id -> presence(id, PresenceStatusDto.ONLINE)).toList())
            .channels(client.channels.values().stream().map(ChannelResource::toDto).toList()));
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

    // --- Events ---------------------------------------------------------------
    // Observers run after the change committed and only queue work, so the
    // request that made the change does not wait for the fan-out.

    void onMessage(@Observes(during = TransactionPhase.AFTER_SUCCESS) MessageEvent event) {
        run(() -> dispatchMessage(event));
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
        run(() -> accounts.findById(event.accountId()).ifPresent(account -> {
            switch (event) {
                case AccountEvent.Registered _ -> broadcast(seq -> new GatewayMemberJoinedDto()
                    .seq(seq).member(AccountDtos.toDto(account)));
                case AccountEvent.Updated _ -> broadcast(seq -> new GatewayMemberUpdatedDto()
                    .seq(seq).member(AccountDtos.toDto(account)));
            }
        }));
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

    private void roleAssignmentChanged(AccountId accountId) {
        syncAccess(accountId::equals);
        accounts.findById(accountId).ifPresent(account ->
            broadcast(seq -> new GatewayMemberUpdatedDto().seq(seq).member(AccountDtos.toDto(account))));
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
            case MessageEvent.Deleted _ -> null;
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
                if (before.owner() != client.principal.owner() || !before.permissions().equals(client.principal.permissions())) {
                    send(client, seq -> new GatewayPermissionsChangedDto().seq(seq).permissions(permissionSet(client.principal)));
                }
            }
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
        for (Channel channel : now.values()) {
            Channel before = client.channels.get(channel.id());
            if (before == null) {
                send(client, seq -> new GatewayChannelCreatedDto().seq(seq).channel(ChannelResource.toDto(channel)));
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
        if (client.pending.get() >= config.maxPendingFrames()) {
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
