-- How far each member has read each channel. A row is created the first time
-- the member can see the channel, at its newest message then, so what came
-- before counts as read. last_read_id is null when the channel was empty,
-- and has no foreign key because the message read last may be deleted.
CREATE TABLE read_state (
    account_id    UUID  NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    channel_id    UUID  NOT NULL REFERENCES channel (id) ON DELETE CASCADE,
    last_read_id  UUID,
    PRIMARY KEY (account_id, channel_id)
);

CREATE INDEX read_state_channel_idx ON read_state (channel_id);
