-- Invite links. A code may be limited in time and in number of uses, and
-- revoked. Redeeming increments "uses" atomically.
CREATE TABLE invite (
    code       VARCHAR(16) PRIMARY KEY,
    created_by UUID        REFERENCES account (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ,
    max_uses   INTEGER     CHECK (max_uses > 0),
    uses       INTEGER     NOT NULL DEFAULT 0,
    revoked_at TIMESTAMPTZ
);

CREATE INDEX invite_created_by_idx ON invite (created_by);

-- Remember how an account got in.
ALTER TABLE account
    ADD COLUMN invite_code VARCHAR(16) REFERENCES invite (code) ON DELETE SET NULL,
    ADD COLUMN invited_by  UUID        REFERENCES account (id) ON DELETE SET NULL;

ALTER TABLE server_settings
    ADD COLUMN public_url                VARCHAR(255),
    ADD COLUMN members_can_invite        BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN rate_limit_invite_limit   INTEGER NOT NULL DEFAULT 30 CHECK (rate_limit_invite_limit > 0),
    ADD COLUMN rate_limit_invite_period  INTEGER NOT NULL DEFAULT 60 CHECK (rate_limit_invite_period > 0);
