-- How many messages one member may send, as a token bucket: five at once,
-- then one a second. Unlike the other rate limits this is counted per account,
-- not per IP address.
ALTER TABLE server_settings
    ADD COLUMN rate_limit_message_limit   INTEGER NOT NULL DEFAULT 5 CHECK (rate_limit_message_limit > 0),
    ADD COLUMN rate_limit_message_period  INTEGER NOT NULL DEFAULT 5 CHECK (rate_limit_message_period > 0);
