-- Channel names are unique regardless of case, so a channel can be named in
-- a message. Where names already clash, the channel further down the list
-- gets a number: "General" becomes "General 2".
DO $$
DECLARE
    clash RECORD;
    candidate VARCHAR(100);
    n INTEGER;
BEGIN
    FOR clash IN
        SELECT c.id, c.name
        FROM channel c
        WHERE EXISTS (SELECT 1 FROM channel o WHERE lower(o.name) = lower(c.name) AND o.position < c.position)
        ORDER BY c.position
    LOOP
        n := 2;
        LOOP
            candidate := left(clash.name, 94) || ' ' || n;
            EXIT WHEN NOT EXISTS (SELECT 1 FROM channel WHERE lower(name) = lower(candidate));
            n := n + 1;
        END LOOP;
        UPDATE channel SET name = candidate, updated_at = now() WHERE id = clash.id;
    END LOOP;
END $$;

CREATE UNIQUE INDEX channel_name_lower_idx ON channel (lower(name));

-- The members a user message mentions, worked out from its <@accountId>
-- tokens. Like reply_to_id there is no foreign key: a mention of a deleted
-- account stays in the text, and in the list.
ALTER TABLE message
    ADD COLUMN mentioned_account_ids UUID[] NOT NULL DEFAULT '{}';

CREATE INDEX message_mentioned_idx ON message USING GIN (mentioned_account_ids);
