-- Binary content: avatars now, message attachments later. Row holds the
-- metadata; the bytes live in the blob store (filesystem by default) under
-- the blob id. Ids are unguessable, and blobs are immutable, so clients may
-- cache them forever.
CREATE TABLE blob (
    id               UUID         PRIMARY KEY,
    content_type     VARCHAR(100) NOT NULL,
    size_bytes       BIGINT       NOT NULL,
    sha256           CHAR(64)     NOT NULL,
    owner_account_id UUID         REFERENCES account (id) ON DELETE SET NULL,
    purpose          VARCHAR(32)  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX blob_owner_idx ON blob (owner_account_id);

ALTER TABLE account
    ADD COLUMN avatar_blob_id UUID REFERENCES blob (id) ON DELETE SET NULL;
