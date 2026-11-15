-- Web apps that may use the server from a browser besides its own pages
-- (ServerSettings.clientOrigins): the hosted app, and apps on localhost, as
-- in development. Lower case; a port of * stands for every port.
CREATE TABLE client_origin (
    origin TEXT PRIMARY KEY CHECK (length(origin) <= 255 AND origin = lower(origin))
) STRICT;

INSERT INTO client_origin (origin) VALUES
    ('https://web.snatter.app'),
    ('http://localhost:*');
