-- Roles bundle permissions (a bitmask, see the Permission enum) and are
-- assigned to accounts. An account's permissions are the union of its roles;
-- an account without roles can only browse. Position is display order,
-- highest first.
CREATE TABLE role (
    id          UUID        PRIMARY KEY,
    name        VARCHAR(64) NOT NULL,
    color       CHAR(7)     CHECK (color ~ '^#[0-9A-Fa-f]{6}$'),
    position    INTEGER     NOT NULL CHECK (position >= 0),
    permissions BIGINT      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE account_role (
    account_id  UUID        NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    role_id     UUID        NOT NULL REFERENCES role (id) ON DELETE CASCADE,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, role_id)
);

CREATE INDEX account_role_role_idx ON account_role (role_id);

-- The standard roles. They are ordinary roles that can be changed or deleted.
-- User: CREATE_INVITE, SEND_MESSAGES, CONNECT, SPEAK, STREAM (bits 6, 7, 9, 10, 11).
-- Moderator: User's plus TIMEOUT_MEMBERS, BAN_MEMBERS, MANAGE_MESSAGES,
--            MUTE_MEMBERS, MOVE_MEMBERS (bits 4, 5, 8, 12, 13).
-- Admin: everything except MANAGE_SERVER, which stays with the owner (bits 1 to 13).
INSERT INTO role (id, name, position, permissions)
VALUES ('00000000-0000-7000-8000-000000000001', 'User', 0, 3776),
       ('00000000-0000-7000-8000-000000000003', 'Moderator', 1, 16368),
       ('00000000-0000-7000-8000-000000000002', 'Admin', 2, 16382);

-- The members_can_invite setting is replaced by the CREATE_INVITE permission
-- on the User role.
UPDATE role
SET permissions = permissions - 64
WHERE id = '00000000-0000-7000-8000-000000000001'
  AND NOT (SELECT members_can_invite FROM server_settings WHERE id = 1);

ALTER TABLE server_settings DROP COLUMN members_can_invite;

-- The role new members get when they register, or none so they can only
-- browse. A role in use here cannot be deleted.
ALTER TABLE server_settings
    ADD COLUMN new_member_role_id UUID REFERENCES role (id) ON DELETE RESTRICT;

UPDATE server_settings
SET new_member_role_id = '00000000-0000-7000-8000-000000000001'
WHERE id = 1;

-- Existing members keep what they could do.
INSERT INTO account_role (account_id, role_id)
SELECT id, '00000000-0000-7000-8000-000000000001' FROM account;
