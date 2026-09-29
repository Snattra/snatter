-- A deleted member's message keeps its place as kind 'deleted': its content,
-- mentions and reply are removed for good, and what remains says when it was
-- deleted and whether by someone other than its author. deleted_by is kept
-- for moderation records; clients only learn removed_by_moderator. Deleted
-- notices are still removed entirely.
ALTER TABLE message
    DROP CONSTRAINT message_kind_check,
    DROP CONSTRAINT message_check,
    DROP CONSTRAINT message_check1,
    DROP CONSTRAINT message_check2,
    ADD COLUMN deleted_at            TIMESTAMPTZ,
    ADD COLUMN deleted_by            UUID REFERENCES account (id) ON DELETE SET NULL,
    ADD COLUMN removed_by_moderator  BOOLEAN,
    ADD CONSTRAINT message_kind_check CHECK (kind IN ('user', 'system', 'deleted')),
    ADD CONSTRAINT message_content_check CHECK ((kind = 'user') = (content IS NOT NULL)),
    ADD CONSTRAINT message_notice_check CHECK ((kind = 'system') = (system_type IS NOT NULL)),
    ADD CONSTRAINT message_deletion_check
        CHECK ((kind = 'deleted') = (deleted_at IS NOT NULL AND removed_by_moderator IS NOT NULL)),
    ADD CONSTRAINT message_user_fields_check
        CHECK (kind = 'user' OR (reply_to_id IS NULL AND edited_at IS NULL AND mentioned_account_ids = '{}'));

-- Purging finds a member's messages by author and time.
DROP INDEX message_author_idx;
CREATE INDEX message_author_idx ON message (author_id, created_at);
