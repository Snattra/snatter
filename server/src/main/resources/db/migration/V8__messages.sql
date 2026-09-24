-- Messages in channels. User messages carry content; system messages carry a
-- notice type and its data (see SystemNotice). Ids are UUID version 7 with a
-- per-process counter, so they order the messages of a channel.
--
-- reply_to_id has no foreign key on purpose: a reply keeps pointing at a
-- deleted message so clients can show that it was a reply.
CREATE TABLE message (
    id           UUID          PRIMARY KEY,
    channel_id   UUID          NOT NULL REFERENCES channel (id) ON DELETE CASCADE,
    kind         VARCHAR(8)    NOT NULL CHECK (kind IN ('user', 'system')),
    author_id    UUID          REFERENCES account (id) ON DELETE SET NULL,
    content      VARCHAR(4000),
    system_type  VARCHAR(32),
    system_data  JSONB,
    reply_to_id  UUID,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    edited_at    TIMESTAMPTZ,
    CHECK ((kind = 'user') = (content IS NOT NULL)),
    CHECK ((kind = 'system') = (system_type IS NOT NULL)),
    CHECK (kind = 'user' OR (reply_to_id IS NULL AND edited_at IS NULL))
);

CREATE INDEX message_channel_idx ON message (channel_id, id);
CREATE INDEX message_author_idx ON message (author_id);

-- Where server-wide notices go. New servers use the general channel.
ALTER TABLE server_settings
    ADD COLUMN system_channel_id UUID REFERENCES channel (id) ON DELETE SET NULL;

UPDATE server_settings
SET system_channel_id = '00000000-0000-7000-8000-000000000101'
WHERE EXISTS (SELECT 1 FROM channel WHERE id = '00000000-0000-7000-8000-000000000101' AND type <> 'voice');
