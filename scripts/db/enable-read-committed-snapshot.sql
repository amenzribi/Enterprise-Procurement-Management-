-- Turns on READ_COMMITTED_SNAPSHOT for one database (audit DB-3). NOT applied in Sprint 1: it is a database-wide
-- change that waits for the owner's approval (gate G3-b in docs/sprints/sprint-01-plan.md).
--
-- What changes: under READ COMMITTED a reader no longer waits for an uncommitted writer; it reads the last
-- committed version of the row. Statements with explicit lock hints (UPDLOCK, sp_getapplock) behave as before.
-- Cost: 14 bytes per modified row and row versions kept in tempdb.
--
-- Procedure
--   1. Announce a maintenance window. Stop the application (the ALTER must be the only connection).
--   2. Back up:   BACKUP DATABASE [<database>] TO DISK = N'<file>' WITH COPY_ONLY, CHECKSUM, INIT;
--   3. Run this script:
--        sqlcmd -S <server> -d master -E -C -b -v DatabaseName="procurement_uat" -i enable-read-committed-snapshot.sql
--   4. Start the application. Run the full backend suite and the browser suite against a copy with the option on
--      BEFORE doing this on a database people use. The code to re-test is the code that reads, decides and writes
--      without a lock hint on unversioned tables (buyer_assignment, quotation_line, shipment_line).
--
-- Rollback (same window, application stopped):
--        ALTER DATABASE [<database>] SET READ_COMMITTED_SNAPSHOT OFF WITH ROLLBACK IMMEDIATE;
--      No data is converted either way; if anything else went wrong, restore the backup of step 2.

SET NOCOUNT ON;

IF DB_ID('$(DatabaseName)') IS NULL
    THROW 50010, 'Database $(DatabaseName) does not exist.', 1;

DECLARE @before BIT = (SELECT is_read_committed_snapshot_on FROM sys.databases WHERE name = '$(DatabaseName)');
PRINT CONCAT('READ_COMMITTED_SNAPSHOT before: ', @before);

IF @before = 0
BEGIN
    DECLARE @others INT = (SELECT COUNT(*) FROM sys.dm_exec_sessions
                           WHERE database_id = DB_ID('$(DatabaseName)') AND session_id <> @@SPID AND is_user_process = 1);
    PRINT CONCAT('Other sessions in the database (they will be disconnected): ', @others);
    ALTER DATABASE [$(DatabaseName)] SET READ_COMMITTED_SNAPSHOT ON WITH ROLLBACK IMMEDIATE;
END

SELECT name, is_read_committed_snapshot_on, snapshot_isolation_state_desc, recovery_model_desc, collation_name
FROM sys.databases WHERE name = '$(DatabaseName)';
