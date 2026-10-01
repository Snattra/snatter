-- The Snatter schema for SQLite.
--
-- Conventions, because SQLite has fewer column types than the domain:
--   * Tables are STRICT, so a value of the wrong type is refused.
--   * Ids are UUIDs as lowercase text. Version 7 ids compare as text in
--     creation order, which is what orders messages.
--   * Instants are INTEGER microseconds since the Unix epoch, UTC.
--   * Booleans are INTEGER 0 or 1.
--   * Length limits on text are CHECK constraints.
-- The connection turns on foreign keys, which SQLite leaves off by default;
-- see persistence.SqliteDriver.

-- Accounts are the users of this community. An account is reached through one
-- or more identities: (issuer, subject) pairs. Local accounts use the issuer
-- 'local' with the account id as subject; external OpenID Connect providers
-- use their issuer URL and the subject claim.
--
-- invite_code and invited_by remember how an account got in. A member in a
-- timeout keeps their roles but holds no permissions until timed_out_until
-- passes; null or a past instant means no timeout.
CREATE TABLE account (
    id              TEXT    PRIMARY KEY,
    username        TEXT    NOT NULL CHECK (length(username) <= 32),
    display_name    TEXT    NOT NULL CHECK (length(display_name) <= 64),
    avatar_blob_id  TEXT    REFERENCES blob (id) ON DELETE SET NULL,
    invite_code     TEXT    REFERENCES invite (code) ON DELETE SET NULL,
    invited_by      TEXT    REFERENCES account (id) ON DELETE SET NULL,
    timed_out_until INTEGER,
    created_at      INTEGER NOT NULL,
    updated_at      INTEGER NOT NULL
) STRICT;

-- Usernames are unique regardless of case. SQLite's lower() only folds ASCII,
-- which is all a username may contain.
CREATE UNIQUE INDEX account_username_lower_idx ON account (lower(username));

CREATE TABLE identity (
    issuer     TEXT    NOT NULL CHECK (length(issuer) <= 255),
    subject    TEXT    NOT NULL CHECK (length(subject) <= 255),
    account_id TEXT    NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    created_at INTEGER NOT NULL,
    PRIMARY KEY (issuer, subject)
) STRICT;

CREATE INDEX identity_account_idx ON identity (account_id);

-- Password for local accounts. Absent for accounts that only sign in through
-- an external provider. Hash is in PHC string format ($argon2id$...).
CREATE TABLE local_credential (
    account_id    TEXT    PRIMARY KEY REFERENCES account (id) ON DELETE CASCADE,
    password_hash TEXT    NOT NULL,
    updated_at    INTEGER NOT NULL
) STRICT;

-- Bearer sessions. Only the SHA-256 of the token is stored, so a database
-- leak does not leak usable tokens. Deleting a row revokes the session.
CREATE TABLE session (
    id           TEXT    PRIMARY KEY,
    account_id   TEXT    NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    token_hash   TEXT    NOT NULL UNIQUE CHECK (length(token_hash) = 64),
    created_at   INTEGER NOT NULL,
    expires_at   INTEGER NOT NULL,
    last_seen_at INTEGER NOT NULL,
    created_ip   TEXT    CHECK (length(created_ip) <= 45),
    user_agent   TEXT    CHECK (length(user_agent) <= 255)
) STRICT;

CREATE INDEX session_account_idx ON session (account_id);

-- Binary content: avatars now, message attachments later. Row holds the
-- metadata; the bytes live in the blob store (filesystem by default) under
-- the blob id. Ids are unguessable, and blobs are immutable, so clients may
-- cache them forever.
CREATE TABLE blob (
    id               TEXT    PRIMARY KEY,
    content_type     TEXT    NOT NULL CHECK (length(content_type) <= 100),
    size_bytes       INTEGER NOT NULL,
    sha256           TEXT    NOT NULL CHECK (length(sha256) = 64),
    owner_account_id TEXT    REFERENCES account (id) ON DELETE SET NULL,
    purpose          TEXT    NOT NULL CHECK (length(purpose) <= 32),
    created_at       INTEGER NOT NULL
) STRICT;

CREATE INDEX blob_owner_idx ON blob (owner_account_id);

-- Proof-of-work challenges that have been redeemed, so a solution cannot be
-- replayed. Rows are pruned once the challenge would have expired anyway.
CREATE TABLE used_challenge (
    challenge  TEXT    PRIMARY KEY CHECK (length(challenge) = 64),
    expires_at INTEGER NOT NULL
) STRICT;

CREATE INDEX used_challenge_expires_idx ON used_challenge (expires_at);

-- Invite links. A code may be limited in time and in number of uses, and
-- revoked. Redeeming increments "uses" atomically.
CREATE TABLE invite (
    code       TEXT    PRIMARY KEY CHECK (length(code) <= 16),
    created_by TEXT    REFERENCES account (id) ON DELETE SET NULL,
    created_at INTEGER NOT NULL,
    expires_at INTEGER,
    max_uses   INTEGER CHECK (max_uses > 0),
    uses       INTEGER NOT NULL DEFAULT 0,
    revoked_at INTEGER
) STRICT;

CREATE INDEX invite_created_by_idx ON invite (created_by);

-- Roles bundle permissions (a bitmask, see the Permission enum) and are
-- assigned to accounts. An account's permissions are the union of its roles;
-- an account without roles can only browse. Position is display order,
-- highest first.
CREATE TABLE role (
    id          TEXT    PRIMARY KEY,
    name        TEXT    NOT NULL CHECK (length(name) <= 64),
    color       TEXT    CHECK (color GLOB '#[0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f]'),
    position    INTEGER NOT NULL CHECK (position >= 0),
    permissions INTEGER NOT NULL DEFAULT 0,
    created_at  INTEGER NOT NULL,
    updated_at  INTEGER NOT NULL
) STRICT;

CREATE TABLE account_role (
    account_id  TEXT    NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    role_id     TEXT    NOT NULL REFERENCES role (id) ON DELETE CASCADE,
    assigned_at INTEGER NOT NULL,
    PRIMARY KEY (account_id, role_id)
) STRICT;

CREATE INDEX account_role_role_idx ON account_role (role_id);

-- Channels form one ordered list: position 0 is the top and positions are
-- contiguous. Voice settings exist exactly for the channel types with voice;
-- a user limit of 0 means no limit.
--
-- Channel names are unique regardless of case, so a channel can be named in
-- a message. name_key is the name in lower case, folded by the server:
-- SQLite's lower() only folds ASCII, and names are not limited to it.
CREATE TABLE channel (
    id          TEXT    PRIMARY KEY,
    type        TEXT    NOT NULL CHECK (type IN ('text', 'voice', 'voice_text')),
    name        TEXT    NOT NULL CHECK (length(name) <= 100),
    name_key    TEXT    NOT NULL,
    topic       TEXT    CHECK (length(topic) <= 1024),
    position    INTEGER NOT NULL CHECK (position >= 0),
    bitrate     INTEGER CHECK (bitrate > 0),
    user_limit  INTEGER CHECK (user_limit >= 0),
    created_at  INTEGER NOT NULL,
    updated_at  INTEGER NOT NULL,
    CHECK ((type = 'text') = (bitrate IS NULL)),
    CHECK ((type = 'text') = (user_limit IS NULL))
) STRICT;

CREATE UNIQUE INDEX channel_name_key_idx ON channel (name_key);

-- Channels a member may see: a channel without rows here is public, one with
-- rows is visible only to members holding at least one of the roles (and to
-- the owner). A role that channels require cannot be deleted, so a private
-- channel never turns public by accident.
CREATE TABLE channel_required_role (
    channel_id  TEXT NOT NULL REFERENCES channel (id) ON DELETE CASCADE,
    role_id     TEXT NOT NULL REFERENCES role (id) ON DELETE RESTRICT,
    PRIMARY KEY (channel_id, role_id)
) STRICT;

CREATE INDEX channel_required_role_role_idx ON channel_required_role (role_id);

-- Messages in channels. User messages carry content; system messages carry a
-- notice type and its data as a JSON object (see SystemNotice). Ids are UUID
-- version 7 with a per-process counter, so they order the messages of a
-- channel.
--
-- reply_to_id has no foreign key on purpose: a reply keeps pointing at a
-- deleted message so clients can show that it was a reply.
--
-- mentioned_account_ids is a JSON array of the members a user message
-- mentions, worked out from its <@accountId> tokens. Like reply_to_id there
-- is no foreign key: a mention of a deleted account stays in the text, and in
-- the list. The list is only read with its message; finding messages by
-- mention would need it moved to a table, as SQLite cannot index into it.
--
-- A deleted member's message keeps its place as kind 'deleted': its content,
-- mentions and reply are removed for good, and what remains says when it was
-- deleted and whether by someone other than its author. deleted_by is kept
-- for moderation records; clients only learn removed_by_moderator. Deleted
-- notices are still removed entirely.
CREATE TABLE message (
    id                    TEXT    PRIMARY KEY,
    channel_id            TEXT    NOT NULL REFERENCES channel (id) ON DELETE CASCADE,
    kind                  TEXT    NOT NULL CHECK (kind IN ('user', 'system', 'deleted')),
    author_id             TEXT    REFERENCES account (id) ON DELETE SET NULL,
    content               TEXT    CHECK (length(content) <= 4000),
    mentioned_account_ids TEXT    NOT NULL DEFAULT '[]' CHECK (json_valid(mentioned_account_ids)),
    system_type           TEXT    CHECK (length(system_type) <= 32),
    system_data           TEXT    CHECK (json_valid(system_data)),
    reply_to_id           TEXT,
    created_at            INTEGER NOT NULL,
    edited_at             INTEGER,
    deleted_at            INTEGER,
    deleted_by            TEXT    REFERENCES account (id) ON DELETE SET NULL,
    removed_by_moderator  INTEGER CHECK (removed_by_moderator IN (0, 1)),
    CONSTRAINT message_content_check CHECK ((kind = 'user') = (content IS NOT NULL)),
    CONSTRAINT message_notice_check CHECK ((kind = 'system') = (system_type IS NOT NULL)),
    CONSTRAINT message_deletion_check
        CHECK ((kind = 'deleted') = (deleted_at IS NOT NULL AND removed_by_moderator IS NOT NULL)),
    CONSTRAINT message_user_fields_check
        CHECK (kind = 'user' OR (reply_to_id IS NULL AND edited_at IS NULL AND mentioned_account_ids = '[]'))
) STRICT;

CREATE INDEX message_channel_idx ON message (channel_id, id);
-- Purging finds a member's messages by author and time.
CREATE INDEX message_author_idx ON message (author_id, created_at);

-- Banned accounts cannot log in and their sessions are refused. The ban
-- goes with the account; the moderator who banned is forgotten if their
-- account goes.
CREATE TABLE ban (
    account_id  TEXT    PRIMARY KEY REFERENCES account (id) ON DELETE CASCADE,
    reason      TEXT    CHECK (length(reason) <= 512),
    banned_by   TEXT    REFERENCES account (id) ON DELETE SET NULL,
    created_at  INTEGER NOT NULL
) STRICT;

-- How far each member has read each channel. A row is created the first time
-- the member can see the channel, at its newest message then, so what came
-- before counts as read. last_read_id is null when the channel was empty,
-- and has no foreign key because the message read last may be deleted.
CREATE TABLE read_state (
    account_id    TEXT NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    channel_id    TEXT NOT NULL REFERENCES channel (id) ON DELETE CASCADE,
    last_read_id  TEXT,
    PRIMARY KEY (account_id, channel_id)
) STRICT;

CREATE INDEX read_state_channel_idx ON read_state (channel_id);

-- Community-wide settings. Exactly one row exists; the CHECK keeps it that way.
--
-- The owner is the first account registered on a fresh server. The
-- registration policy, rate limits and the rest are changeable at runtime
-- by the owner. Rate limits are per client IP, as token buckets of "limit"
-- requests per "period" seconds.
CREATE TABLE server_settings (
    id                          INTEGER PRIMARY KEY CHECK (id = 1),
    name                        TEXT    NOT NULL CHECK (length(name) <= 100),
    description                 TEXT    CHECK (length(description) <= 1000),
    public_url                  TEXT    CHECK (length(public_url) <= 255),
    owner_account_id            TEXT    REFERENCES account (id) ON DELETE SET NULL,
    registration_mode           TEXT    NOT NULL DEFAULT 'invite_only'
        CHECK (registration_mode IN ('open', 'invite_only')),
    registration_challenge      INTEGER NOT NULL DEFAULT 1 CHECK (registration_challenge IN (0, 1)),
    -- Difficulty of the registration challenge: the largest number a client may have to find.
    challenge_max_number        INTEGER NOT NULL DEFAULT 100000
        CHECK (challenge_max_number BETWEEN 1000 AND 10000000),
    rate_limits_enabled         INTEGER NOT NULL DEFAULT 1 CHECK (rate_limits_enabled IN (0, 1)),
    rate_limit_login_limit      INTEGER NOT NULL DEFAULT 10   CHECK (rate_limit_login_limit > 0),
    rate_limit_login_period     INTEGER NOT NULL DEFAULT 60   CHECK (rate_limit_login_period > 0),
    rate_limit_register_limit   INTEGER NOT NULL DEFAULT 5    CHECK (rate_limit_register_limit > 0),
    rate_limit_register_period  INTEGER NOT NULL DEFAULT 3600 CHECK (rate_limit_register_period > 0),
    rate_limit_challenge_limit  INTEGER NOT NULL DEFAULT 30   CHECK (rate_limit_challenge_limit > 0),
    rate_limit_challenge_period INTEGER NOT NULL DEFAULT 60   CHECK (rate_limit_challenge_period > 0),
    rate_limit_invite_limit     INTEGER NOT NULL DEFAULT 30   CHECK (rate_limit_invite_limit > 0),
    rate_limit_invite_period    INTEGER NOT NULL DEFAULT 60   CHECK (rate_limit_invite_period > 0),
    -- How many messages one member may send: five at once, then one a
    -- second. Unlike the other rate limits this is counted per account.
    rate_limit_message_limit    INTEGER NOT NULL DEFAULT 5    CHECK (rate_limit_message_limit > 0),
    rate_limit_message_period   INTEGER NOT NULL DEFAULT 5    CHECK (rate_limit_message_period > 0),
    session_lifetime_days       INTEGER NOT NULL DEFAULT 30   CHECK (session_lifetime_days BETWEEN 1 AND 365),
    -- Bitrate of new voice channels in bits per second, within what Opus supports.
    voice_default_bitrate       INTEGER NOT NULL DEFAULT 64000
        CHECK (voice_default_bitrate BETWEEN 8000 AND 510000),
    -- Where server-wide notices go.
    system_channel_id           TEXT    REFERENCES channel (id) ON DELETE SET NULL,
    -- The role new members get when they register, or none so they can only
    -- browse. A role in use here cannot be deleted.
    new_member_role_id          TEXT    REFERENCES role (id) ON DELETE RESTRICT,
    created_at                  INTEGER NOT NULL,
    updated_at                  INTEGER NOT NULL
) STRICT;

-- A fresh server: the standard roles, one text channel that also receives
-- server-wide notices, and the settings row pointing at both.
--
-- The standard roles are ordinary roles that can be changed or deleted.
-- User: CREATE_INVITE, SEND_MESSAGES, CONNECT, SPEAK, STREAM (bits 6, 7, 9, 10, 11).
-- Moderator: User's plus TIMEOUT_MEMBERS, BAN_MEMBERS, MANAGE_MESSAGES,
--            MUTE_MEMBERS, MOVE_MEMBERS (bits 4, 5, 8, 12, 13).
-- Admin: everything except MANAGE_SERVER, which stays with the owner (bits 1 to 13).
INSERT INTO role (id, name, position, permissions, created_at, updated_at)
SELECT column1, column2, column3, column4, now, now
FROM (VALUES ('00000000-0000-7000-8000-000000000001', 'User', 0, 3776),
             ('00000000-0000-7000-8000-000000000003', 'Moderator', 1, 16368),
             ('00000000-0000-7000-8000-000000000002', 'Admin', 2, 16382)),
     (SELECT CAST(unixepoch('subsec') * 1000000 AS INTEGER) AS now);

INSERT INTO channel (id, type, name, name_key, position, created_at, updated_at)
SELECT '00000000-0000-7000-8000-000000000101', 'text', 'General', 'general', 0, now, now
FROM (SELECT CAST(unixepoch('subsec') * 1000000 AS INTEGER) AS now);

INSERT INTO server_settings (id, name, system_channel_id, new_member_role_id, created_at, updated_at)
SELECT 1, 'My Snatter server', '00000000-0000-7000-8000-000000000101', '00000000-0000-7000-8000-000000000001', now, now
FROM (SELECT CAST(unixepoch('subsec') * 1000000 AS INTEGER) AS now);
