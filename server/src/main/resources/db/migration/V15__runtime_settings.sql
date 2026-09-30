-- Settings that used to be configuration, changeable at runtime by the owner.
-- The defaults are what the configuration defaulted to.
ALTER TABLE server_settings
    ADD COLUMN session_lifetime_days SMALLINT NOT NULL DEFAULT 30
        CHECK (session_lifetime_days BETWEEN 1 AND 365),
    -- Difficulty of the registration challenge: the largest number a client may have to find.
    ADD COLUMN challenge_max_number  INTEGER  NOT NULL DEFAULT 100000
        CHECK (challenge_max_number BETWEEN 1000 AND 10000000),
    -- Bitrate of new voice channels in bits per second, within what Opus supports.
    ADD COLUMN voice_default_bitrate INTEGER  NOT NULL DEFAULT 64000
        CHECK (voice_default_bitrate BETWEEN 8000 AND 510000);
