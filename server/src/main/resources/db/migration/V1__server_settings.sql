-- Community-wide settings. Exactly one row exists; the CHECK keeps it that way.
CREATE TABLE server_settings (
    id          SMALLINT     PRIMARY KEY CHECK (id = 1),
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(1000),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO server_settings (id, name, description)
VALUES (1, 'My Snatter server', NULL);
