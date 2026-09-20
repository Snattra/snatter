-- The server owner: the first account registered on a fresh server. Until
-- role-based access control exists, the owner is the only administrator.
ALTER TABLE server_settings
    ADD COLUMN owner_account_id UUID REFERENCES account (id) ON DELETE SET NULL;

-- Registration policy, changeable at runtime by the owner.
ALTER TABLE server_settings
    ADD COLUMN registration_mode      VARCHAR(16) NOT NULL DEFAULT 'invite_only'
        CHECK (registration_mode IN ('open', 'invite_only')),
    ADD COLUMN registration_challenge BOOLEAN     NOT NULL DEFAULT TRUE;

-- Per-client-IP rate limits for the unauthenticated endpoints, as token
-- buckets of "limit" requests per "period" seconds.
ALTER TABLE server_settings
    ADD COLUMN rate_limits_enabled          BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN rate_limit_login_limit       INTEGER NOT NULL DEFAULT 10   CHECK (rate_limit_login_limit > 0),
    ADD COLUMN rate_limit_login_period      INTEGER NOT NULL DEFAULT 60   CHECK (rate_limit_login_period > 0),
    ADD COLUMN rate_limit_register_limit    INTEGER NOT NULL DEFAULT 5    CHECK (rate_limit_register_limit > 0),
    ADD COLUMN rate_limit_register_period   INTEGER NOT NULL DEFAULT 3600 CHECK (rate_limit_register_period > 0),
    ADD COLUMN rate_limit_challenge_limit   INTEGER NOT NULL DEFAULT 30   CHECK (rate_limit_challenge_limit > 0),
    ADD COLUMN rate_limit_challenge_period  INTEGER NOT NULL DEFAULT 60   CHECK (rate_limit_challenge_period > 0);

-- Proof-of-work challenges that have been redeemed, so a solution cannot be
-- replayed. Rows are pruned once the challenge would have expired anyway.
CREATE TABLE used_challenge (
    challenge  CHAR(64)    PRIMARY KEY,
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX used_challenge_expires_idx ON used_challenge (expires_at);
