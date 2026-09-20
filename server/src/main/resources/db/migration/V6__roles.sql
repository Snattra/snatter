-- Roles bundle permissions (a bitmask, see the Permission enum) and are
-- assigned to accounts. Exactly one role is the default role that every
-- member has implicitly. Position orders roles: higher is more senior, the
-- default role is always 0.
CREATE TABLE role (
    id          UUID        PRIMARY KEY,
    name        VARCHAR(64) NOT NULL,
    color       CHAR(7)     CHECK (color ~ '^#[0-9A-Fa-f]{6}$'),
    position    INTEGER     NOT NULL CHECK (position >= 0),
    permissions BIGINT      NOT NULL DEFAULT 0,
    is_default  BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX role_single_default_idx ON role (is_default) WHERE is_default;

CREATE TABLE account_role (
    account_id  UUID        NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    role_id     UUID        NOT NULL REFERENCES role (id) ON DELETE CASCADE,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, role_id)
);

CREATE INDEX account_role_role_idx ON account_role (role_id);

-- The default role: CREATE_INVITE, VIEW_CHANNELS, SEND_MESSAGES, CONNECT,
-- SPEAK and STREAM (bits 6, 7, 8, 10, 11, 12).
INSERT INTO role (id, name, position, permissions, is_default)
VALUES ('00000000-0000-7000-8000-000000000001', 'everyone', 0, 7616, TRUE);

-- The members_can_invite setting is replaced by the CREATE_INVITE permission
-- on the default role.
UPDATE role
SET permissions = permissions - 64
WHERE is_default
  AND NOT (SELECT members_can_invite FROM server_settings WHERE id = 1);

ALTER TABLE server_settings DROP COLUMN members_can_invite;
