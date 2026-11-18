-- Database permissions of the two application principals (audit DB-1, D-3). Idempotent.
--
-- Run in the application database, as a member of db_owner, with sqlcmd variables:
--   sqlcmd -S <server> -d <database> -E -C -b -I -v MigrationUser="procurement_migrate" RuntimeUser="procurement_app" -i grant-application-roles.sql
--
--   MigrationUser  used only by Flyway during a release (SPRING_FLYWAY_USER / SPRING_FLYWAY_PASSWORD).
--                  May create and alter schema objects and read/write data.
--   RuntimeUser    used by the running application (DB_USERNAME / DB_PASSWORD).
--                  Reads and writes data. Cannot alter the schema, so it cannot disable the audit triggers,
--                  and cannot change or delete audit events even if a trigger were missing.
--
-- Rollback: ALTER ROLE ... DROP MEMBER for the roles below, REVOKE the GRANT and DENY lines, or drop the users.

SET NOCOUNT ON;

IF DATABASE_PRINCIPAL_ID('$(MigrationUser)') IS NULL OR DATABASE_PRINCIPAL_ID('$(RuntimeUser)') IS NULL
    THROW 50001, 'Create both database users before granting (see create-application-logins.sql).', 1;

-- Migration principal: schema changes and data (repeatable demo migrations insert rows).
ALTER ROLE db_ddladmin   ADD MEMBER [$(MigrationUser)];
ALTER ROLE db_datareader ADD MEMBER [$(MigrationUser)];
ALTER ROLE db_datawriter ADD MEMBER [$(MigrationUser)];
-- Sequences are not tables: NEXT VALUE FOR needs UPDATE on them. REFERENCES lets it create foreign keys.
GRANT UPDATE, REFERENCES, EXECUTE ON SCHEMA::dbo TO [$(MigrationUser)];

-- Runtime principal: data only.
ALTER ROLE db_datareader ADD MEMBER [$(RuntimeUser)];
ALTER ROLE db_datawriter ADD MEMBER [$(RuntimeUser)];
-- Sequences (NEXT VALUE FOR).
GRANT UPDATE ON SCHEMA::dbo TO [$(RuntimeUser)];
-- No schema changes: this is what keeps the audit triggers in place (DISABLE TRIGGER needs ALTER).
-- Not DENY CONTROL: denying CONTROL denies every permission it covers, reading and writing included.
DENY ALTER, TAKE OWNERSHIP ON SCHEMA::dbo TO [$(RuntimeUser)];
DENY ALTER ANY DATABASE DDL TRIGGER, ALTER ANY SCHEMA, CREATE TABLE, CREATE PROCEDURE, CREATE FUNCTION, CREATE VIEW
    TO [$(RuntimeUser)];
-- Audit events are append-only for the application, with or without the trigger.
DENY UPDATE, DELETE ON OBJECT::dbo.audit_event TO [$(RuntimeUser)];
-- The migration history belongs to the migration principal.
IF OBJECT_ID('dbo.flyway_schema_history') IS NOT NULL
    DENY INSERT, UPDATE, DELETE ON OBJECT::dbo.flyway_schema_history TO [$(RuntimeUser)];

PRINT 'Permissions granted to $(MigrationUser) (migration) and $(RuntimeUser) (runtime).';
