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

-- Channels a member may see: a channel without rows here is public, one with
-- rows is visible only to members holding at least one of the roles (and to
-- the owner). A role that channels require cannot be deleted, so a private
-- channel never turns public by accident.
CREATE TABLE channel_required_role (
    channel_id  UUID    NOT NULL REFERENCES channel (id) ON DELETE CASCADE,
    role_id     UUID    NOT NULL REFERENCES role (id) ON DELETE RESTRICT,
    PRIMARY KEY (channel_id, role_id)
);

CREATE INDEX channel_required_role_role_idx ON channel_required_role (role_id);

-- A fresh server starts with one text and one voice channel.
INSERT INTO channel (id, type, name, position, bitrate, user_limit)
VALUES ('00000000-0000-7000-8000-000000000101', 'text', 'general', 0, NULL, NULL),
       ('00000000-0000-7000-8000-000000000102', 'voice', 'General', 1, 64000, 0);
