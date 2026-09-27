-- A fresh server starts with one text channel, General, which also receives
-- server-wide notices. Servers that already have accounts keep the channels
-- they have: nobody could have changed the seeded ones without an account,
-- so on a server without any they are still exactly as V7 created them.
DELETE FROM channel
WHERE id = '00000000-0000-7000-8000-000000000102'
  AND NOT EXISTS (SELECT 1 FROM account);

UPDATE channel
SET name = 'General'
WHERE id = '00000000-0000-7000-8000-000000000101'
  AND NOT EXISTS (SELECT 1 FROM account);
