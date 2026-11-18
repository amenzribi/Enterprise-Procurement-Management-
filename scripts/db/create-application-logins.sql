-- Creates the migration and runtime logins and their database users (audit DB-1, D-3), then grants their roles.
-- NOT run by the application or by Flyway. A database administrator runs it once per environment.
--
-- Needs SQL Server authentication enabled on the server (mixed mode); the development laptop is Windows-only.
-- Passwords come from the command line or the environment, never from a file in the repository:
--
--   sqlcmd -S <server> -d <database> -E -C -b -I ^
--     -v MigrationUser="procurement_migrate" MigrationPassword="%MIGRATION_PASSWORD%" ^
--        RuntimeUser="procurement_app" RuntimePassword="%RUNTIME_PASSWORD%" ^
--     -i create-application-logins.sql
--
-- Then configure the application:
--   DB_USERNAME / DB_PASSWORD                      = runtime login
--   SPRING_FLYWAY_USER / SPRING_FLYWAY_PASSWORD    = migration login (release step only)
-- and check with verify-runtime-permissions.sql.
--
-- Rollback: point the application back to the previous login, then
--   DROP USER [<user>];  (in the database)   DROP LOGIN [<login>];  (on the server)

SET NOCOUNT ON;

IF SUSER_ID('$(MigrationUser)') IS NULL
    CREATE LOGIN [$(MigrationUser)] WITH PASSWORD = N'$(MigrationPassword)', CHECK_POLICY = ON, CHECK_EXPIRATION = OFF;
IF SUSER_ID('$(RuntimeUser)') IS NULL
    CREATE LOGIN [$(RuntimeUser)] WITH PASSWORD = N'$(RuntimePassword)', CHECK_POLICY = ON, CHECK_EXPIRATION = OFF;

IF DATABASE_PRINCIPAL_ID('$(MigrationUser)') IS NULL
    CREATE USER [$(MigrationUser)] FOR LOGIN [$(MigrationUser)] WITH DEFAULT_SCHEMA = dbo;
IF DATABASE_PRINCIPAL_ID('$(RuntimeUser)') IS NULL
    CREATE USER [$(RuntimeUser)] FOR LOGIN [$(RuntimeUser)] WITH DEFAULT_SCHEMA = dbo;
GO
:r grant-application-roles.sql
