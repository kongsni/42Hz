-- Test-only: both changes must roll back together. Never packaged in the production jar.
CREATE TABLE migration_rollback_probe (id integer PRIMARY KEY);
INSERT INTO migration_rollback_probe (id) VALUES (1);
INSERT INTO migration_rollback_probe (id) VALUES (1);
