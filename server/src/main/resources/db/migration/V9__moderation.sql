-- Banned accounts cannot log in and their sessions are refused. The ban
-- goes with the account; the moderator who banned is forgotten if their
-- account goes.
CREATE TABLE ban (
    account_id  UUID          PRIMARY KEY REFERENCES account (id) ON DELETE CASCADE,
    reason      VARCHAR(512),
    banned_by   UUID          REFERENCES account (id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- A member in a timeout keeps their roles but holds no permissions until
-- this instant passes; null or a past instant means no timeout.
ALTER TABLE account ADD COLUMN timed_out_until TIMESTAMPTZ;
