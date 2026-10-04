-- A moderator's voice mute (MUTE_MEMBERS): while muted_at is set the member
-- holds no SPEAK. It stays with the account until lifted, so leaving and
-- joining voice does not end it.
ALTER TABLE account ADD COLUMN muted_at INTEGER;
