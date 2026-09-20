-- Accounts are the users of this community. An account is reached through one
-- or more identities: (issuer, subject) pairs. Local accounts use the issuer
-- 'local' with the account id as subject; external OpenID Connect providers
-- use their issuer URL and the subject claim.

CREATE TABLE account (
    id           UUID        PRIMARY KEY,
    username     VARCHAR(32) NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Usernames are unique regardless of case.
CREATE UNIQUE INDEX account_username_lower_idx ON account (lower(username));

CREATE TABLE identity (
    issuer     VARCHAR(255) NOT NULL,
    subject    VARCHAR(255) NOT NULL,
    account_id UUID         NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (issuer, subject)
);

CREATE INDEX identity_account_idx ON identity (account_id);

-- Password for local accounts. Absent for accounts that only sign in through
-- an external provider. Hash is in PHC string format ($argon2id$...).
CREATE TABLE local_credential (
    account_id    UUID        PRIMARY KEY REFERENCES account (id) ON DELETE CASCADE,
    password_hash TEXT        NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Bearer sessions. Only the SHA-256 of the token is stored, so a database
-- leak does not leak usable tokens. Deleting a row revokes the session.
CREATE TABLE session (
    id           UUID         PRIMARY KEY,
    account_id   UUID         NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    token_hash   CHAR(64)     NOT NULL UNIQUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ  NOT NULL,
    last_seen_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_ip   INET,
    user_agent   VARCHAR(255)
);

CREATE INDEX session_account_idx ON session (account_id);
