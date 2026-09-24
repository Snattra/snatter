-- Channels form one ordered list: position 0 is the top and positions are
-- contiguous. Voice settings exist exactly for the channel types with voice;
-- a user limit of 0 means no limit.
CREATE TABLE channel (
    id          UUID          PRIMARY KEY,
    type        VARCHAR(16)   NOT NULL CHECK (type IN ('text', 'voice', 'voice_text')),
    name        VARCHAR(100)  NOT NULL,
    topic       VARCHAR(1024),
    position    INTEGER       NOT NULL CHECK (position >= 0),
    bitrate     INTEGER       CHECK (bitrate > 0),
    user_limit  INTEGER       CHECK (user_limit >= 0),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK ((type = 'text') = (bitrate IS NULL)),
    CHECK ((type = 'text') = (user_limit IS NULL))
);

-- Per-channel refinements of permissions (bitmasks, see the Permission enum)
-- for the members of a role or for one member. Deleting the channel, role or
-- account removes the overwrite.
CREATE TABLE channel_overwrite (
    channel_id  UUID    NOT NULL REFERENCES channel (id) ON DELETE CASCADE,
    role_id     UUID    REFERENCES role (id) ON DELETE CASCADE,
    account_id  UUID    REFERENCES account (id) ON DELETE CASCADE,
    allow       BIGINT  NOT NULL DEFAULT 0,
    deny        BIGINT  NOT NULL DEFAULT 0,
    CHECK ((role_id IS NULL) <> (account_id IS NULL)),
    CHECK (allow & deny = 0)
);

CREATE UNIQUE INDEX channel_overwrite_role_idx ON channel_overwrite (channel_id, role_id) WHERE role_id IS NOT NULL;
CREATE UNIQUE INDEX channel_overwrite_account_idx ON channel_overwrite (channel_id, account_id) WHERE account_id IS NOT NULL;
CREATE INDEX channel_overwrite_role_fk_idx ON channel_overwrite (role_id);
CREATE INDEX channel_overwrite_account_fk_idx ON channel_overwrite (account_id);

-- A fresh server starts with one text and one voice channel.
INSERT INTO channel (id, type, name, position, bitrate, user_limit)
VALUES ('00000000-0000-7000-8000-000000000101', 'text', 'general', 0, NULL, NULL),
       ('00000000-0000-7000-8000-000000000102', 'voice', 'General', 1, 64000, 0);

-- An Admin role, most senior of all roles, ready to assign. It carries only
-- ADMINISTRATOR (bit 15), which implies every permission except
-- MANAGE_SERVER and ignores channel overwrites.
INSERT INTO role (id, name, position, permissions)
SELECT '00000000-0000-7000-8000-000000000002', 'Admin', coalesce(max(position), 0) + 1, 32768
FROM role;
