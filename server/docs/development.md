# Developing the Snatter server

This document is for people changing the server. For running it, see the
server README.

## Package layout

Base package is `app.snatter.server`. Each feature gets its own sub-package
containing its resources, services and repositories together, rather than
splitting by technical layer:

| Package       | Contents                                             |
|---------------|------------------------------------------------------|
| `settings`    | Community settings, server info, settings API        |
| `account`     | Accounts and the identity model                      |
| `auth`        | Passwords, sessions, challenges, HTTP authentication |
| `blob`        | Binary content: storage, metadata, image detection   |
| `invite`      | Invite links and their redemption                    |
| `moderation`  | Bans, timeouts and voice mutes                       |
| `role`        | Permissions, roles, assignment rules                 |
| `channel`     | Channels, ordering, required roles                   |
| `message`     | Messages, replies, paging, system notices            |
| `gateway`     | WebSocket gateway: identify, ready, events, voice    |
| `media`       | Voice transport: SDP, ICE, DTLS-SRTP on one UDP port |
| `ratelimit`   | Per-client rate limiting driven by the settings      |
| `api`         | Shared API error types and exception mappers         |
| `common`      | Domain-wide abstractions such as `Value` and `Id`    |
| `persistence` | JDBI producer and small JDBC helpers                 |

## Database

- **Flyway owns the schema.** Migrations live in
  `src/main/resources/db/migration` as `V<n>__<description>.sql` and run at
  startup. Never edit a migration that has been committed; add a new one.
- **Plain SQL through [JDBI 3](https://jdbi.org).** No ORM. Each feature
  package has a repository class that injects `Jdbi`, writes SQL in text
  blocks, and maps rows onto records with an explicit `RowMapper`.
- **SQLite, in one file.** `persistence.SqliteDriver` sets up every
  connection: foreign keys on (SQLite leaves them off), write-ahead logging so
  reads never wait for writes, and transactions that take the write lock as
  they begin. The schema's own conventions are at the top of `V1__schema.sql`.
- **Transactions are JTA, and only for writes.** Put `@Transactional` on the
  service or repository method and use `jdbi.withHandle` / `jdbi.useHandle`
  inside it. Do not use `jdbi.inTransaction` or `handle.begin()`.
- **One writer, many readers.** SQLite runs one write transaction at a time.
  Transactions therefore share a single connection and queue for it in the
  pool; statements outside a transaction run on a pool of read-only
  connections that work side by side (`persistence.JdbiProducer` picks). So:
  - Every write needs a transaction; a write outside one fails.
  - A transaction holds the writer from its first statement, reads
    included, to its end, and every other write waits meanwhile. Keep in it
    only what must be atomic, including the reads a write depends on (a
    position computed from the others, say). Password hashing, challenge
    checks and anything else slow come before it, and reads that decide
    nothing about a write stay outside.
  - `ConcurrencyTest` races requests against each other and checks what the
    database keeps true meanwhile. Extend it when adding a write that others
    may race.
- **Constraint violations** are recognised with `persistence.SqlErrors`, for
  example to turn a lost race for a unique name into a 409.
- **Instants** are microseconds since the epoch in the database and `Instant`
  in Java; UUIDs are text; lists of ids are JSON arrays. Binding is automatic;
  read them with the helpers in `persistence.Rows`.
- **Ids** are UUID version 7 wrapped in typed records such as `AccountId`;
  see "Typed identifiers" below. `persistence.Ids` makes them strictly
  increasing within the process, so they also order rows such as messages.

## Identity and authentication

An account is reached through one or more identities, keyed by
`(issuer, subject)`. Local accounts use issuer `local` with the account id as
subject. External OpenID Connect providers will add rows with their issuer URL
and subject claim, so one account can be reached through several logins.

Passwords for local accounts are hashed with Argon2id in PHC string format
(`$argon2id$v=19$m=65536,t=3,p=1$...`). Parameters are embedded in each hash
and can be raised without invalidating old ones.

Sessions are opaque bearer tokens, not JWTs, so they can be revoked
instantly. A token is `snt_` followed by 32 random bytes in URL-safe base64.
Only its SHA-256 hash is stored, in the `session` table. Clients send it as
`Authorization: Bearer snt_...`. `SessionAuthenticationMechanism` and
`SessionIdentityProvider` resolve it into an `AccountPrincipal` carrying the
account id, username, session id and effective permissions, so
`@Authenticated`, `@PermissionsAllowed` and `SecurityIdentity` work as usual
in resources. `auth.Principals` builds that principal, for HTTP requests and
gateway connections alike.

A session expires after the server setting `sessionLifetimeDays` without use. Each
authenticated request moves `expires_at` forward, written at most every five
minutes together with `last_seen_at`, and open gateway connections do the
same through `AuthService.keepAlive`. Logging out deletes the session and
fires `SessionEvent.Ended`, which closes its gateway connections.

Sessions record the client IP as the server sees it. Behind a reverse proxy
that is the proxy's address until trusted-proxy handling is configured; this
arrives together with IP bans.

## Blobs and avatars

All binary content goes through the `blob` package. A row in the `blob` table
holds the metadata (content type, size, SHA-256, owner, purpose) and the bytes
live in a `BlobStore`. The default store is the filesystem under
`snatter.storage.root`, laid out as `blobs/<first two hex digits>/<id>`. An
object store implementation can be added behind the same interface.

`BlobService` keeps the two in step across transactions: bytes are written
before the row is inserted and removed after the deleting transaction commits,
so readers never see a row without bytes.

Blobs are immutable and their ids unguessable, so `GET /api/v1/blobs/{id}` is
public and served with a one-year immutable cache header. This lets `<img>`
tags load avatars without an Authorization header. Anything that needs access
control later, such as attachments in private channels, will need a different
delivery scheme.

Avatars are stored as uploaded after validation: PNG, JPEG, GIF or WebP as
determined from the bytes (the declared Content-Type is ignored), at most
1 MiB and between 32 and 1024 pixels on each side. The server does not resize,
so clients should crop and scale before uploading. `Account.avatarId` points
at the current blob; replacing or clearing an avatar deletes the old blob.

## Server owner and settings

The first account registered on a fresh server becomes the **server owner**,
recorded in `server_settings.owner_account_id` and published as
`ServerInfo.ownerId`, so clients can mark the owner and leave them out of
moderation. The owner holds every permission, sees every channel, is exempt
from the role management rule, and cannot be demoted. Only the owner changes the server settings unless they
grant `MANAGE_SERVER` explicitly; the seeded `Admin` role does not have it.
Everything else is decided by permissions; see "Roles and permissions".

Everything the owner can change at runtime lives in the
`server_settings` row and is read through `ServerSettingsService`, which
caches the row in memory and fires a `Changed` event on updates. Request paths
never query the table. `application.properties` holds only how the server is
built and wired; environment variables say where it runs (data directory,
database file, port), and anything about how the community runs is a server setting.
Internal timing, such as the challenge lifetime or the gateway's keep-alive
interval, is a constant in the class that uses it.

## Roles and permissions

Authorization is permission-based. `role.Permission` is an enum with a fixed
bit per permission, stored as a bitmask in `role.permissions` and exposed by
name in the API; never renumber a bit. Roles (`role` table) bundle
permissions and are assigned to accounts through `account_role`; an account
has any number of roles, and `Account.roleIds` lists all of them. A member's
effective permissions are the union of their roles; the owner has all of
them. A member without roles can browse the channels they can see and do
nothing else. Permissions are server-wide and apply the same in every channel
the member can see; which channels those are is decided by roles too (see
"Channels"), not by a permission.

A fresh server has three standard roles: `User` (invite, send messages,
voice), `Moderator` (User's plus timeouts, bans and moderating messages and
voice) and `Admin` (every permission except `MANAGE_SERVER`). They are
ordinary roles and can be renamed, changed or deleted. When a permission is
added in a later version, a migration decides which existing roles get it.

The `newMemberRoleId` server setting names the role an account gets when it
registers, `User` by default; with none, new members can only browse until
someone gives them a role. Changing it does not touch existing members. The
role it names cannot be deleted (`role_in_use`, backed by
`ON DELETE RESTRICT`). Roles only ever add permissions, so taking something
away from one member, such as the right to post, means taking away the role
that grants it.

`SessionIdentityProvider` resolves the effective permissions once per request
through `RoleService.resolve` and puts them on the `AccountPrincipal`
together with what the roles grant before any timeout or mute (`granted`),
an owner flag and the ids of the assigned roles. Every access
check reads only the principal, never the database. The provider also
installs a Quarkus permission checker, so resources guard operations with
`@PermissionsAllowed("MANAGE_ROLES")` and the like; a denial is rendered as
the `forbidden` error. Checks that need to look at the arguments live in the
services and use the principal.

One rule keeps role management safe, and `RoleService` enforces it for
everyone except the owner: you may only create, change, delete, assign or
take away a role whose permissions you all hold, and only grant permissions
you hold (`permission_escalation`). What you hold here is what your roles
grant, so a mute takes nothing off it. So a member with `MANAGE_ROLES` can
never end up with, or take away, more than they have. Roles have a `position` for
display only, highest first; new roles are inserted at 0 with everything
else moving up.

## Moderation

Bans (`BAN_MEMBERS`), timeouts (`TIMEOUT_MEMBERS`) and mutes
(`MUTE_MEMBERS`) live in `moderation.ModerationService` and share the role
rule through `RoleService.requireOutranks`: the actor's roles must grant
everything the target's roles grant (`member_outranks_you`), the owner can
never be targeted, and nobody can target themselves (`cannot_moderate_self`).
Rank is judged on what the roles grant, on both sides, so a timed-out Admin
still outranks a Moderator, and a muted Moderator still outranks a User.

**Bans** (`ban` table, one row per account, with an optional reason). Banning
deletes the member's sessions in the same transaction and fires
`AccountEvent.Banned`, on which the gateway closes their connections with
`banned`. `SessionRepository.findByTokenHash` never finds a session of a
banned account, which also covers a login that raced with the ban. Login
checks for a ban only after the password is verified, so guessers learn
nothing, and answers `banned` with the reason in `ApiError.ban`, through
`BannedException`. Messages stay. IP bans are not built yet.

A banned member stays a member, and everyone may know it: `Account.bannedAt`
is the ban's time, read with every account, and the gateway sends
`member_updated` to everyone after the banned member's connections close.
Lifting a ban fires `AccountEvent.Unbanned`, which does the same. The reason
and who banned stay with `BAN_MEMBERS` (`GET /bans`).

**Timeouts** (`account.timed_out_until`). The member keeps roles, sessions
and their view of channels, but `RoleService.resolve` gives them no
permissions until the instant passes, so they can read and nothing else.
`Account.timedOutUntil` shows it to everyone, null once it has passed.
Starting or lifting one fires `AccountEvent.TimeoutChanged`, on which the
gateway re-resolves the member (`permissions_changed`) and sends
`member_updated`. Nothing fires when a timeout runs out, so the gateway
schedules the same refresh for the end of each timeout it sees on a
principal, one per account, replaced whenever the principal is resolved
again.

**Mutes** (`account.muted_at`) turn someone else's microphone off, for a
noisy mic or an open one left behind rather than as a punishment: the
member stays in voice, listens and writes, but `RoleService.resolve` leaves
out `SPEAK` until a moderator unmutes them. A mute has no end and stays with
the account, so leaving and joining voice does not lift it.
`Account.mutedAt` shows it to everyone, and clients draw it like the
member's own mute. Muting or unmuting fires `AccountEvent.MuteChanged`, which
the gateway handles like a timeout change; muting someone already muted
fires nothing and keeps the first time.

## Channels

A channel (`channel` table) is `text`, `voice` or `voice_text`. Voice
channels, and only they, have a bitrate in bits per second and a user limit,
0 meaning none; the table enforces this. A bitrate may be anything Opus
supports, 8 to 510 kbps. New voice channels get the server setting
`voice.defaultBitrate`, published in `ServerInfo.voice` for clients. The type
is fixed at creation. A fresh server has one text channel,
`General`.

Names are unique regardless of case (a unique index on `name_key`, the name
in lower case, folded in Java because SQLite's `lower()` only folds ASCII),
so a message can name a channel and a client can tell which one it means;
`channel_name_taken` otherwise. This holds across channels the caller cannot
see, so it reveals that a hidden channel has a name, and nothing else.

Channels form one flat list ordered by `position`, 0 at the top, and
positions are always contiguous. Every operation that changes positions
(create, move, delete) reads them and writes the new ones in one
transaction, which holds SQLite's write lock throughout, so concurrent
writers renumber from the same list while readers are not blocked.
Categories will group channels later.

**Private channels.** A channel's required roles (`channel_required_role`)
decide who sees it: none means everyone, otherwise only members holding at
least one of them, and the owner (`Channel.isVisibleTo`). Having a permission
never reveals a channel, so administrators manage exactly the channels their
roles let them see. `ChannelService` treats a channel the caller cannot see
as nonexistent (`channel_not_found`), for changes too.

Required roles are given in the create request and stored in the same
transaction, so a private channel is never visible to anyone else, and are
replaced as a whole by an update. They must name existing roles
(`invalid_required_role`), and everyone but the owner must
hold one of them (`required_role_not_held`), so no one locks themselves out
by mistake. A role that channels require cannot be deleted (`role_in_use`,
backed by `ON DELETE RESTRICT`); otherwise deleting it could turn a private
channel public.

**Events.** `ChannelService` fires `ChannelEvent`s (`Created`, `Renamed`,
`TopicChanged`, `VoiceSettingsChanged`, `Moved`, `RequiredRolesChanged`,
`Deleted`) synchronously inside the transaction that made the
change. `message.SystemNotices` observes them to write system messages, so a
notice commits or rolls back together with its change. The gateway observes
them after commit to update connected clients.

## Messages

A message (`message` table) belongs to a channel whose type keeps messages;
every message operation on a voice-only channel fails with
`voice_only_channel`. `Message` is a sealed interface: a `UserMessage` has
content and may reply to another message, a `SystemMessage` carries a
`SystemNotice`, itself sealed with one record per notice type holding exactly
that notice's values. In the table the kind is the `kind` column and a
notice is `system_type` plus its values in `system_data`, a JSON object;
`MessageRepository` is the only place that knows these names. Clients render
notice text from the type, so it can be translated, and must show unknown
types generically.

**Content** is at most 4000 characters, trimmed, and never blank. It is plain
text that clients render as Markdown, in the dialect the contract describes.
Mentions are tokens in the text, `<@accountId>`, `<@&roleId>` and
`<#channelId>`.

**Mentions.** Sending or editing works out the members the content mentions
(`message.Mentions`, the `<@accountId>` tokens) and stores them in
`mentioned_account_ids`, a JSON array read only with its message. Finding a
member's mentions, for notifications, will need them in a table of their
own, since SQLite cannot index into the array. Each account counts once,
in order of first mention, and only accounts that exist; a token for anyone
else stays in the text as it is. A message may mention at most 20 members,
counted before that check (`too_many_mentions`). Role and channel tokens are
not stored: role mentions are for later, and a channel is a link, not
someone to notify.

**Order and paging.** Message ids are UUID version 7 from `persistence.Ids`,
which are strictly increasing within the server process (a counter follows
the millisecond timestamp), so the id alone orders a channel's messages and
is the paging cursor. `GET .../messages` returns pages oldest first: the
latest `limit` messages, the ones just `before` an id when scrolling back, or
the ones just `after` an id when catching up. A deleted message still works
as a cursor.

**Replies** store `reply_to_id` without a foreign key, so a reply keeps
pointing at a deleted message; the response then has `replyToId` and a null
`replyTo` preview. Only a user message in the same channel can be replied to
(`invalid_reply`).

**Editing and deleting.** Authors edit their own user messages; only the new
text is kept, with `edited_at` set. Authors delete their own user messages,
and `MANAGE_MESSAGES` in the channel deletes any message there, notices
included. When an account is deleted its messages stay with a null author.

A deleted user message becomes a `DeletedMessage` (kind `deleted`) in its
place, so the conversation around it still reads in order: its content,
mentions and reply are removed from the row for good, and it records when it
was deleted, by whom (`deleted_by`, kept for moderation records and not
exposed) and whether that was someone other than the author
(`removed_by_moderator`, which clients show as "Removed by a moderator").
The gateway announces it as `message_updated`. A deleted notice has nothing
worth keeping, so it is removed and announced as `message_deleted`.
Deleting a `DeletedMessage` changes nothing, and it cannot be edited or
replied to.

**Purging.** `DELETE /accounts/{id}/messages?since=` lets `MANAGE_MESSAGES`
delete everything a member sent since a time, in the channels the moderator
can see, for cleaning up after a spammer. It is one `UPDATE` over the
`(author_id, created_at)` index, and each channel gets a single
`messages_purged` frame naming the author and the first and last message
deleted, instead of one frame per message.

**System notices** are written by `message.SystemNotices`, which observes
`ChannelEvent`, `AccountEvent` and `ServerSettingsService.Changed` inside the
transaction that made the change:

| Notice                      | Where               | Author                |
|-----------------------------|---------------------|-----------------------|
| `channel_created`           | the new channel     | who created it        |
| `channel_renamed`           | the channel         | who renamed it        |
| `channel_topic_changed`     | the channel         | who changed it        |
| `member_joined`             | the system channel  | the new member        |
| `server_renamed`            | the system channel  | the owner             |
| `registration_mode_changed` | the system channel  | the owner             |

The system channel is the `systemChannelId` server setting, the seeded
`General` channel on a new server. It must be a channel with messages;
setting it to an empty string turns server-wide notices off, and deleting the
channel does the same.

`createMessage` accepts a client-chosen `nonce` and returns it with the
stored message, so a client can replace its pending copy. The gateway hands
it back the same way, to the sending session only.

**Read markers** (`read_state` table) record, per member and channel, the
newest message the member has read; later messages are unread. A marker only
moves forward: `PUT .../read-state` with an older message leaves it, and
`ReadStateRepository.advance` does the comparison in its upsert, since
message ids order messages. Sending a message moves the sender's marker to
it, so members never see their own messages as unread. Each move fires
`ReadStateEvent`, which the gateway sends as `read_state_updated` to that
member's connections only. A member gets a marker the first time they can
see a channel with messages, at its newest message then: what was already
there counts as read. That happens when the gateway builds `ready` or
reveals a channel (`ReadStateRepository.startReading`); a channel that was
empty gets a null marker, so everything posted in it is new. `ready` and
`read_state_updated` carry the channel's newest message id along with the
marker, which is all a client needs to show unread channels.

## Gateway

Live updates go over one WebSocket, `/api/v1/gateway`, built on Quarkus
WebSockets Next. The frames are part of the API contract: the `Gateway*`
schemas in `openapi.yaml`, with `GatewayClientFrame` and
`GatewayServerFrame` as sealed unions on `type`, generated like every other
DTO. The contract's description covers the lifecycle a client follows:
`identify` with a session token within ten seconds (`GatewayConfig`, which
only tests shorten), receive `ready`, then events numbered by `seq`. Reconnecting clients start
over with a fresh `ready` and catch up on messages over REST with `after`.
Close codes are the `GatewayCloseReason` values, mirrored by
`gateway.GatewayClose`: 4000 to 4499 tell the client not to reconnect, 4500
to 4999 to reconnect.

**Versions.** `identify` carries the client's protocol version, and a client
older than `protocol.Protocol.MIN_CLIENT` is closed with `client_outdated`
before its token is looked at. The minimum is the first version of the
server's own major; a release raises it to turn away clients with a known
problem. `Protocol.CURRENT` must equal `info.version`
in the contract, which `ProtocolTest` checks. Client frames of a type the
server does not know are dropped, not closed with `invalid_frame`, so a
newer client can talk to an older server.

**One dispatcher thread.** `GatewayEndpoint` only hands connections and
frames to `Gateway`, which does everything on a single thread: opening,
identifying, closing, and fanning out events. So a connection's `ready`
snapshot and the events after it form one consistent sequence, and the
per-connection state in `Client` needs no locking. Work that blocks, such as
reading the database for `ready`, runs on that thread too; at the scale of
one community this keeps the design simple.

**Events after commit.** Features fire sealed domain events inside their
transactions: `ChannelEvent`, `MessageEvent`, `RoleEvent`, `AccountEvent`,
`SessionEvent` and `ServerSettingsService.Changed`. `Gateway` observes them
with `TransactionPhase.AFTER_SUCCESS` and only queues work, so a connection
never sees a change that rolled back, and the request that made the change
does not wait for the fan-out. A new kind of change reaches clients by
firing an event from its service and handling it in `Gateway`.

**Differences, not per-event frames.** For channels, roles and permissions,
each `Client` remembers what it was last told: the roles, the channels it can
see, and its own permissions. After any event that may affect them, the
dispatcher recomputes that view from the database and sends `*_created`,
`*_updated` and `*_deleted` frames for what differs. Moves that shift other channels, required-role edits, and role changes
that hide or reveal channels come out right without special cases; a channel
that becomes invisible is simply `channel_deleted` for that member. Messages
are sent per event to the connections that can view the channel, and a
channel is always announced before its first message.

**Nonce.** A user message's `nonce` from `createMessage` is carried in the
event and set only on connections of the session that sent it.

**Presence.** A member is `online` while they have at least one identified
connection that is not closing, and `offline` otherwise; nothing about it is
stored. `Gateway` keeps the set of online accounts and sends
`presence_updated` to everyone when an account's first connection
identifies or its last one closes. The newly identified connection is not
live yet when that goes out, and learns its own presence from `ready`'s
`presences` instead. `PresenceStatus` is an enum so that states such as idle,
which the client would report, can be added later.

**Typing.** The client sends `typing` for a channel; the dispatcher checks it
against what the connection already knows (the channel is visible and keeps
messages, the member holds `SEND_MESSAGES`), so no database is involved, and
passes it on as `typing_started` to the other members' connections that see
the channel, never to the typist's own. Invalid targets are dropped silently
rather than answered, so typing cannot probe for hidden channels. Each
connection gets one `typing` per channel through every 5 seconds. Nothing is
remembered: clients show the indicator for 10 seconds or until a message from
that member arrives, and keep it alive by sending `typing` every 8 seconds.

**Voice.** Who is in which voice channel lives only here, like presence:
the map `voice` holds each member's `VoiceState` (channel, own mute and
deafen) and the connection that holds it. A member is in at most one voice
channel, through one connection. The client sends `voice_state` with the
channel it wants to be in: from a connection not in voice that joins,
taking the member over from any other connection they were in voice on,
which gets `voice_ended` (`joined_elsewhere`); from the connection in voice
it changes the mute and deafen, moves, or with no channel leaves. Joining
needs `CONNECT`, a voice channel the connection can see, and room under the
channel's user limit unless the member holds `MOVE_MEMBERS`; a refusal
answers `voice_refused` to that connection only, saying which channel it is
in after all, so a client never has to work that out from broadcasts it
cannot tell apart from another connection's. A connection that takes voice
over always hears its own state, even when no one else sees a change. Each connection gets one
`voice_state` applied per 250 ms, since each reaches everyone who sees the
channel. Unlike typing, one that comes sooner is not dropped: it waits in
`Client.voicePending` for its turn, replaced by any newer one, so the last
one sent counts and the client and the server agree. When the connection in
voice closes, for whatever reason, the member leaves. After role, channel
and timeout changes, `endLostVoice` takes out of voice those who can no
longer see their channel or no longer hold `CONNECT`, with `voice_ended`.

Like channels, voice is sent as differences: each `Client` remembers the
voice states it was told (`voiceStates`), those in the channels it can see,
and gets `voice_state_updated` and `voice_state_deleted` for what changed.
So moving into a channel someone cannot see is leaving, to them. Inside
`syncChannels` the voice states of a channel leave before its
`channel_deleted` and arrive after its `channel_created`.

**Voice signalling.** The connection in voice holds the `VoiceConnection`
that carries its audio (`Client.media`, see "Voice media"). Joining, or
taking voice over, opens one. After every voice or channel change,
`syncMedia` gives each connection the others in its channel and the
channel's bitrate (`VoiceConnection.hear`), and sends `voice_offer` to those
with an offer to make, after the voice states it follows from. Each
`voice_answer` goes to `VoiceConnection.accept`, which may hand back the
next offer; one with no offer waiting, or from a connection not in voice,
is ignored. An answer that cannot be used, or a connection that fails or is
not up 30 seconds after the first offer, ends voice with
`connection_failed`, checked on the dispatcher so a connection closed or
replaced meanwhile is left alone. Whatever ends the member's voice closes
the connection; moving to another channel keeps it.

**Read markers.** `ready` carries the member's read state for every visible
channel with messages, creating markers for channels seen for the first
time. A channel revealed later is followed by `read_state_updated` with its
new marker, and markers moved over REST or by sending reach the member's
other connections the same way (see "Messages").

**Sessions.** Connections authenticate once, with `identify`, and keep the
principal for their lifetime; its permissions are resolved again when roles
change. When a session ends (`SessionEvent.Ended`: logout today, revocation
later) its connections close with `session_ended`; a ban closes all of the
member's connections with `banned` instead. Every five minutes the
dispatcher extends the sessions of open connections through `AuthService.keepAlive`, so a client that is only
listening stays logged in, and closes connections whose session is gone.

**Slow clients.** Frames are sent asynchronously and counted until the
WebSocket has written them. A connection with more than
1000 frames outstanding is closed with `too_slow`;
the client reconnects and gets a fresh `ready`.

## Voice media

Voice travels over WebRTC, through the server: each browser connects to the
server rather than to the others, and the server forwards the audio. The
`media` package carries it, on Jitsi's ice4j for ICE and jitsi-srtp for
encryption, with keys from a DTLS handshake by BouncyCastle. The gateway
offers a connection to whoever joins voice ("Voice signalling" above), and
forwards each member's voice to the others in their channel.

**One UDP port.** `MediaPort` opens `snatter.media.port`
(`SNATTER_MEDIA_PORT`, 8080 by default, the HTTP port's number) at start, on
every address of the machine it can. An address it cannot open is a
warning, and none at all fails the start. Every connection shares the port:
ice4j's `SinglePortUdpHarvester` tells them apart by the ICE username in
their first packet. `snatter.media.address` names the address people reach
the port at behind NAT or in a container, offered as a server-reflexive
candidate in front of each socket of its family, IPv4 or IPv6; with no
socket of its family, the start fails.

**The peer leads ICE.** `IceConnection` is the server's side for one
connection: its credentials and candidates go into the offer, and it leaves
the checks to the peer, as with a server that implements ICE lite. The
peer's address comes from its checks, so the server takes none of its
candidates, which browsers hide behind mDNS names anyway. `connected()`
gives the socket and address to carry media on once the peer has picked a
path.

**Encryption is DTLS-SRTP.** `SrtpConnection` runs over that path. Its
certificate is made for the connection and its fingerprint goes into the
offer; the answer names the peer's, and the handshake fails unless the peer
shows that certificate. The peer starts the handshake, as browsers do when
they answer, so the server is the DTLS server: DTLS 1.2 with an ECDSA
certificate, and SRTP with AES-GCM, or AES-CM and HMAC-SHA1 when that is all
the peer offers. The socket carries DTLS and SRTP alike, told apart by their
first byte. RTP is decrypted and handed on, and RTCP is dropped for now. A
source's keys are kept only once a packet from it decrypts, and for four
sources at most, so neither stray packets nor the peer can fill memory.
`SrtpConnectionTest` adds a BouncyCastle DTLS client and jitsi-srtp to the
stand-in browser, written apart from the server's code.

**The server offers, the browser answers.** `VoiceConnection` is one
member's connection, with ICE and SRTP behind it. Its offer's first m-line
takes the member's voice: Opus, capped at the channel's bitrate with
`maxaveragebitrate`, with DTX (`usedtx=1`) so silence costs next to nothing,
every candidate up front and `a=ice-lite`. Each further m-line carries one
other member's voice, `sendonly`, as a source the server picks and a stream
named after their account id (`a=msid`). The line of a member who left goes
`inactive` and carries the next to come, as a new source, so lines are
reused rather than piling up. Each change is a new offer of the same
session with a higher version, sent only once the last is answered; a
change meanwhile waits for the answer. From an answer it needs only the
ICE credentials, the SHA-256 fingerprint, and `a=setup:active`, the browser
starting DTLS; an answer without them is refused with
`InvalidAnswerException`. `ready()` completes once ICE and the handshake are
done, and fails if either does, or if the peer has not connected within 30
seconds of the first offer.

**Forwarding.** Each member's decrypted RTP goes to every other member in
their channel (`VoiceConnection.hear` names them), on that member's line:
the packet is copied, given the line's source, and encrypted for the
listener. A line starts carrying only once the listener's browser has
answered the offer that made it, and stops at once when its member leaves;
the SRTP context of a source the server stops sending is dropped. Payload
types need no rewriting, as every line uses Opus at 111. RTCP is not
forwarded. `VoiceConnectionTest` has two stand-in browsers hear each other
and checks lines are reused; `VoiceTest` does it through the gateway, and
`SdpTest` checks the offer's text and reads answers shaped like Chrome's
and Firefox's.

**ice4j settings** are system properties, which `MediaPort` sets before
ice4j loads: no link-local addresses, and no probe of the EC2 metadata
address at every start. application.properties turns its logging down to
warnings, and keeps in the native executable the files ice4j's
configuration library reads. In tests the port is any free one, with a documentation address
in front of it, and `MediaPortTest` connects an ice4j agent standing in for
a browser.

## Registration policy

`AuthService.register` applies the policy in this order:

1. The first account on an empty server is always accepted and becomes owner.
2. Otherwise an `inviteCode`, if given, is redeemed (`invite_invalid` when it
   cannot be); without one `registrationMode` must be `open`, else
   `registration_closed` (403).
3. If `challengeRequired` is set, the request must carry a solved
   [ALTCHA](https://altcha.org) proof-of-work in `altcha`, verified by
   `AltchaService`: HMAC signature, `SHA-256(salt + number)`, expiry from the
   salt, and single use via the `used_challenge` table. The HMAC key is random
   per server start, so a restart invalidates outstanding challenges without
   any stored state.
4. The username must be free; then the password is hashed and a session
   created.

## Invites

An invite is a row in `invite` keyed by an 8-character random code
(`InviteCode`, a `CharSequence` value record so the contract's pattern can
validate it as a path parameter). It may carry an expiry, a maximum number of
uses, and a revocation time. `InviteRepository.redeem` counts a use in a
single conditional `UPDATE ... RETURNING`, so concurrent registrations cannot
overspend the last use. Redemption runs inside the registration transaction:
if the registration fails afterwards, for example on a taken username, the
use is rolled back with it.

Creating an invite needs the `CREATE_INVITE` permission, which the default
role grants unless changed. Members with `MANAGE_INVITES` list and revoke all
invites; others only their own. `GET /api/v1/invites/{code}` is public so a client can show what the
invite leads to before the person registers; it is rate limited under the
`invite` policy and answers 404 for unknown or revoked codes and 410 for
expired or used-up ones.

Invite links are `<publicUrl>/invite/<code>`. `publicUrl` is an owner
setting; when it is not set the link is built from the address the request
arrived on. The web app serves `/invite/<code>` and opens registration with
the code filled in. Accounts remember the invite and inviter they came in with
(`account.invite_code`, `account.invited_by`) for later moderation features.

## Rate limiting

`@RateLimited("<policy>")` on a resource method applies the named policy from
`ServerSettings.rateLimits` through `RateLimitFilter`, counted per client IP
or, with `per = ACCOUNT`, per signed-in account. Authentication runs before
the filter, so the account is known there. Policies are token buckets; a
refused request gets 429 with `Retry-After` and the `rate_limited` error.
Buckets live in memory, so this protects a single server instance, and they
are reset whenever the policies change. One switch, `rateLimits.enabled`,
turns them all on or off.

| Policy      | Counted per | Guards                                  | Default      |
|-------------|-------------|-----------------------------------------|--------------|
| `login`     | IP          | Signing in                              | 10 per 60 s  |
| `register`  | IP          | Creating accounts                       | 5 per hour   |
| `challenge` | IP          | Fetching registration challenges        | 30 per 60 s  |
| `invite`    | IP          | The public invite preview               | 30 per 60 s  |
| `message`   | account     | Sending messages, in any channel        | 5 per 5 s    |

Messages are counted per account so that people sharing an address (a
household, a LAN party) do not slow each other down, and one account gains
nothing from switching addresses. Only sending counts; editing does not.

## HTTP API

The API is specification-first. `protocol/openapi/openapi.yaml` is
hand-written and is the contract. On every build the openapi-generator Maven
plugin turns it into JAX-RS interfaces (`app.snatter.api.*Api`, one per tag)
and request/response classes (`app.snatter.api.model.*Dto`) under
`target/generated-sources/openapi`. Resource classes implement the
interfaces, so a change to the contract that the code does not honour fails
to compile. The same file is served verbatim at `/q/openapi`, with Swagger UI
at `/q/swagger-ui` in dev mode; annotation scanning is disabled.

Conventions that follow from this:

- **Resources implement a generated interface** and carry no JAX-RS
  annotations of their own; paths, media types and parameter constraints come
  from the contract. Security annotations such as `@Authenticated` go on the
  implementing class or method.
- **Generated types are DTOs**, suffixed `Dto` to keep them apart from domain
  records. Map at the boundary, for example `AccountDtos.toDto(Account)`.
  Typed ids (`AccountId`, `BlobId`) are the exception: the contract's
  `AccountId` and `BlobId` schemas are mapped straight onto the hand-written
  records, so DTOs and interface parameters use them directly.
- **Methods return `RestResponse<Dto>`**, Quarkus REST's typed response, so
  the body type is checked by the compiler while status and headers stay
  under the resource's control. Binary bodies are `InputStream` in both
  directions.
- **Unions are sealed.** A schema with several shapes is a `oneOf` with a
  `discriminator` and a `mapping` (for example `Message` on `kind`,
  `SystemNotice` on `type`), shared properties coming from an `allOf` base.
  The generator (`useOneOfInterfaces`, `useSealed`) turns it into a sealed
  DTO interface with a final class per shape, and Jackson writes the
  discriminator from the class, so resources never set it. Model the domain
  as a matching sealed interface and map with an exhaustive `switch`.
- **Partial updates** use `*Update` schemas where every field is optional.
  Generated DTOs leave absent arrays `null` (`containerDefaultToNull`), so a
  null field means "unchanged" and an empty array means "set to empty".
- **Bean Validation constraints live in the contract** (`minLength`,
  `pattern`, `required`) and are generated onto the DTOs and interface
  parameters. Do not repeat them on the implementing method.

Errors have one shape, the `ApiError` schema:

```json
{"error": "<stable_code>", "message": "human readable", "fields": {"username": "..."}}
```

`fields` appears only for validation failures. Throw `api.ApiException` for
domain errors; it carries the status and code, and `ApiExceptionMappers`
renders it. When adding a code, document it in the contract on the operation
that produces it.

| Method | Path                | Auth | Purpose                                   |
|--------|---------------------|------|-------------------------------------------|
| GET    | `/server-info`      | no   | Software and community description        |
| GET    | `/server-settings`   | MANAGE_SERVER | Read the settings                |
| PATCH  | `/server-settings`   | MANAGE_SERVER | Change settings, partial         |
| GET    | `/auth/challenge`   | no   | Proof-of-work challenge for registration  |
| POST   | `/auth/register`    | no   | Create a local account, returns a session |
| POST   | `/auth/login`       | no   | Username and password, returns a session  |
| POST   | `/auth/logout`      | yes  | Revoke the calling session                |
| GET    | `/accounts/me`      | yes  | The calling account                       |
| GET    | `/accounts/{id}`    | yes  | Any member's profile                      |
| PUT    | `/accounts/me/avatar` | yes | Replace the profile picture; body is the raw image |
| DELETE | `/accounts/me/avatar` | yes | Remove the profile picture              |
| GET    | `/invites`           | yes  | List invites: all with MANAGE_INVITES, otherwise own |
| POST   | `/invites`           | yes  | Create an invite                          |
| GET    | `/invites/{code}`    | no   | Preview an invite: community and inviter  |
| DELETE | `/invites/{code}`    | yes  | Revoke, by creator or MANAGE_INVITES      |
| GET    | `/roles`             | yes  | List roles, highest first                 |
| POST   | `/roles`             | MANAGE_ROLES | Create a role at the bottom       |
| PATCH  | `/roles/{id}`        | MANAGE_ROLES | Change name, colour, position or permissions |
| DELETE | `/roles/{id}`        | MANAGE_ROLES | Delete a role                     |
| PUT    | `/accounts/{id}/roles/{roleId}` | MANAGE_ROLES | Assign a role         |
| DELETE | `/accounts/{id}/roles/{roleId}` | MANAGE_ROLES | Remove a role         |
| GET    | `/accounts/me/permissions` | yes | Effective permissions of the caller |
| GET    | `/channels`          | yes  | Channels the caller can see, in order     |
| POST   | `/channels`          | MANAGE_CHANNELS | Create a channel at the bottom, optionally private to some roles |
| GET    | `/channels/{id}`     | yes  | One visible channel                       |
| PATCH  | `/channels/{id}`     | MANAGE_CHANNELS | Rename, topic, voice settings, position, required roles |
| DELETE | `/channels/{id}`     | MANAGE_CHANNELS | Delete a channel               |
| GET    | `/channels/{id}/messages` | yes | A page of messages, oldest first; `before`, `after`, `limit` |
| POST   | `/channels/{id}/messages` | SEND_MESSAGES | Send a message, optionally as a reply |
| GET    | `/channels/{id}/messages/{messageId}` | yes | One message |
| PATCH  | `/channels/{id}/messages/{messageId}` | author | Edit your own message |
| DELETE | `/channels/{id}/messages/{messageId}` | author or MANAGE_MESSAGES | Delete a message |
| GET    | `/bans`              | BAN_MEMBERS | Every ban, newest first           |
| PUT    | `/bans/{accountId}`  | BAN_MEMBERS | Ban a member, with an optional reason |
| DELETE | `/bans/{accountId}`  | BAN_MEMBERS | Lift a ban                        |
| PUT    | `/timeouts/{accountId}` | TIMEOUT_MEMBERS | Time a member out for up to 28 days |
| DELETE | `/timeouts/{accountId}` | TIMEOUT_MEMBERS | End a timeout early           |
| PUT    | `/mutes/{accountId}` | MUTE_MEMBERS | Mute a member in voice              |
| DELETE | `/mutes/{accountId}` | MUTE_MEMBERS | Unmute a member                     |
| GET    | `/blobs/{id}`       | no   | Blob bytes, immutable, cache forever      |

Error codes: `validation_failed`, `username_taken`, `registration_closed`,
`challenge_required`, `challenge_invalid`, `forbidden`, `rate_limited`,
`invite_invalid`, `invite_not_found`, `invite_unusable`, `role_not_found`,
`role_in_use`, `permission_escalation`,
`channel_not_found`, `not_a_voice_channel`, `invalid_required_role`,
`required_role_not_held`, `banned`, `member_outranks_you`, `cannot_moderate_self`, `invalid_display_name`,
`voice_only_channel`, `message_not_found`, `invalid_reply`, `invalid_paging`,
`invalid_credentials`, `account_not_found`, `blob_not_found`,
`unsupported_image`, `image_dimensions`, `image_too_large`.

Usernames are 3 to 32 characters of letters A to Z, digits and underscore,
and unique per server regardless of case. Passwords are 8 to 128 characters.

Display names (`account.DisplayNames`) may use letters of any script, digits,
punctuation, symbols and plain spaces, and nothing else: no emoji, control or
format characters (line breaks, bidirectional overrides, zero-width
characters), other kinds of space, combining marks, or letters that draw
nothing. They are trimmed and runs of spaces become one, but not otherwise
normalized. They are checked first in registration, so a refused name spends
no invite or challenge. Clients render names as text, never as markup, so the
rules are about readable names that are hard to fake rather than injection.

## Testing

- `*Test` classes run with `@QuarkusTest` against the application in the same
  JVM, on a fresh SQLite database in `target/test-data` each time the
  application starts. Tests that write to the database directly need a
  transaction of their own (`QuarkusTransaction.requiringNew()`), like any
  other write.
- Every test starts on a fresh server, so tests never clean up after
  themselves. Before each one, `testing.ResetDatabaseBeforeEach` has
  `testing.DatabaseReset` empty every table and put back what the migrations
  left in it, as read at startup. It also destroys the beans that keep
  database state in memory (`ServerSettingsService` and `RateLimitFilter`),
  so they are created afresh; a new bean that does so belongs in its list.
- `testing.TestDataService` sets up what a class's tests share. A test class
  keeps one in a field and calls `setUpServer()` from `@BeforeEach`, which
  registers the well-known `owner` as the first account, opens registration
  and switches rate limiting off. Its other methods act as that owner:
  changing settings, managing roles, registering members with given
  permissions. `testing.TestUsers` registers further accounts as a client
  would, solving the registration challenge.
- Tests call the API through a client generated from the contract into
  `target/generated-test-sources` (package `app.snatter.client`), which is
  never part of the application. `testing.ApiClientFactory` gives each API
  anonymously, as in `channelsApi()`, or signed in, as in `channelsApi(alice)`.
  Its clients refuse responses with fields or values the contract does not
  have, so a server that drifts from the contract fails the tests. A refused
  call throws `ApiException`, which
  `testing.ApiAssertions.assertApiError(status, code, call)` checks; the 401
  for a missing or unknown token has no body, so `assertApiStatus` checks
  that. Kinds of message share no generated type with their fields, so
  `testing.Messages` sends them and reads their ids. Only tests about the
  wire format itself, such as input the contract does not allow, use
  RestAssured directly.
- Nothing tests the native executable. Changes that add dependencies or touch
  serialisation can need reflection registration that only the native build
  shows, so build it (see the README) and try them on it before merging.
- Gateway tests use `testing.GatewayTestClient`, a WebSocket client on the
  JDK's `java.net.http` that reads frames into the generated models, as
  strictly as the API clients. It buffers them, and
  `await(GatewayMessageCreatedDto.class, predicate)` takes the first match, so
  a test waits for the frames it cares about regardless of unrelated ones in
  between. It checks that `seq` has no gaps. To assert that a frame did *not*
  arrive, trigger a later frame on the same connection, await it, then call
  `assertNone`: frames arrive in order.

## Typed identifiers and value records

Identifiers are never bare `UUID`s or `String`s in method signatures. Each
kind of id is its own record implementing `common.Id`, for example
`account.AccountId` and `auth.SessionId`, so the compiler stops an account id
from being passed where a session id belongs. Row mappers construct these
types directly.

Each id record provides `newId()`, `fromString(String)` for JAX-RS path
parameters, and serialises to JSON as the plain UUID string. Repositories
bind them directly with `.bind("id", accountId)`;
`persistence.ValueArgumentFactory` handles the conversion, so call sites never
unwrap the value.

The same pattern covers other single-value records through `common.Value<T>`,
of which `Id` is the UUID case. `invite.InviteCode` wraps a `String` this
way. A `String`-valued record that arrives as a path parameter with a pattern
constraint in the contract must also implement `CharSequence`, or Bean
Validation cannot apply the constraint to it.
