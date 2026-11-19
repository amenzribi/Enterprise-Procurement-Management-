-- Proves what the runtime principal can and cannot do (audit DB-1 verification). Changes nothing: every statement
-- runs in its own transaction, which is rolled back. Run as a member of db_owner (it impersonates the runtime user):
--
--   sqlcmd -S <server> -d <database> -E -C -b -I -v RuntimeUser="procurement_app" -i verify-runtime-permissions.sql
--
-- Each line prints PASS or FAIL; the script ends with an error if any check failed.
-- A "must be refused" check passes only on a permission error (229, 230, 262, 1088, 3701, 4902), so a failure for
-- another reason is not mistaken for a refusal.

SET NOCOUNT ON;
SET XACT_ABORT OFF;
DECLARE @failed INT = 0, @msg NVARCHAR(400);
DECLARE @checks TABLE (seq INT IDENTITY, expect_refused BIT, label NVARCHAR(100), stmt NVARCHAR(1000));

INSERT INTO @checks (expect_refused, label, stmt) VALUES
 (1, N'DISABLE TRIGGER on audit_event', N'DISABLE TRIGGER trg_audit_event_append_only ON dbo.audit_event'),
 (1, N'ALTER TABLE',                    N'ALTER TABLE dbo.audit_event ADD verify_probe INT NULL'),
 (1, N'CREATE TABLE',                   N'CREATE TABLE dbo.verify_probe (id INT)'),
 (1, N'DROP TABLE',                     N'DROP TABLE dbo.idempotency_record'),
 (1, N'UPDATE audit_event',             N'UPDATE dbo.audit_event SET action = action WHERE 1 = 0'),
 (1, N'DELETE audit_event',             N'DELETE FROM dbo.audit_event WHERE 1 = 0'),
 (1, N'write to flyway_schema_history', N'DELETE FROM dbo.flyway_schema_history WHERE 1 = 0'),
 (0, N'SELECT on audit_event',          N'DECLARE @n BIGINT = (SELECT COUNT(*) FROM dbo.audit_event)'),
 (0, N'NEXT VALUE FOR a sequence',      N'DECLARE @id BIGINT = NEXT VALUE FOR dbo.seq_attachment'),
 (0, N'INSERT, UPDATE, DELETE on a data table',
     N'INSERT INTO dbo.idempotency_record (actor_key, idempotency_key, fingerprint, http_status, response_json, created_at)
       VALUES (''verify'', ''verify-probe'', REPLICATE(''0'', 64), 200, NULL, SYSUTCDATETIME());
       UPDATE dbo.idempotency_record SET http_status = 201 WHERE actor_key = ''verify'' AND idempotency_key = ''verify-probe'';
       DELETE FROM dbo.idempotency_record WHERE actor_key = ''verify'' AND idempotency_key = ''verify-probe'''),
 (0, N'INSERT into audit_event is allowed by permissions (append-only)',
     N'DECLARE @n INT = (SELECT HAS_PERMS_BY_NAME(''dbo.audit_event'', ''OBJECT'', ''INSERT'')); IF @n <> 1 THROW 50004, ''no INSERT permission'', 1'),
 (0, N'sp_getapplock (application locks)',
     N'DECLARE @lock INT; EXEC @lock = sp_getapplock @Resource = ''verify-probe'', @LockMode = ''Exclusive'', @LockOwner = ''Transaction'', @LockTimeout = 0; IF @lock < 0 THROW 50002, ''application lock not granted'', 1');

DECLARE @seq INT = 1, @max INT = (SELECT MAX(seq) FROM @checks), @refused BIT, @label NVARCHAR(100), @stmt NVARCHAR(1000);

-- Error 3701 means "does not exist or no permission": prove each probed object exists before trusting a refusal.
IF OBJECT_ID('dbo.audit_event', 'U') IS NULL OR OBJECT_ID('dbo.idempotency_record', 'U') IS NULL
    OR OBJECT_ID('dbo.flyway_schema_history', 'U') IS NULL OR OBJECT_ID('dbo.trg_audit_event_append_only', 'TR') IS NULL
    OR OBJECT_ID('dbo.seq_attachment', 'SO') IS NULL
BEGIN
    SET @failed += 1;
    PRINT 'FAIL  a probed object is missing: run the checks on a migrated database';
END
IF DATABASE_PRINCIPAL_ID('$(RuntimeUser)') IS NULL
    THROW 50005, 'The runtime user does not exist in this database.', 1;

EXECUTE AS USER = '$(RuntimeUser)';
WHILE @seq <= @max
BEGIN
    SELECT @refused = expect_refused, @label = label, @stmt = stmt FROM @checks WHERE seq = @seq;
    BEGIN TRY
        BEGIN TRANSACTION;
        EXEC sp_executesql @stmt;
        ROLLBACK TRANSACTION;
        IF @refused = 1
        BEGIN
            SET @failed += 1; PRINT 'FAIL  allowed but must be refused: ' + @label;
        END
        ELSE PRINT 'PASS  allowed: ' + @label;
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
        IF @refused = 1 AND ERROR_NUMBER() IN (229, 230, 262, 1088, 3701, 4902)
            PRINT 'PASS  refused (' + CAST(ERROR_NUMBER() AS VARCHAR(10)) + '): ' + @label;
        ELSE
        BEGIN
            SET @failed += 1;
            PRINT 'FAIL  ' + @label + ': ' + CAST(ERROR_NUMBER() AS VARCHAR(10)) + ' ' + ERROR_MESSAGE();
        END
    END CATCH;
    SET @seq += 1;
END
REVERT;

IF @failed > 0
BEGIN
    SET @msg = CONCAT(@failed, ' permission check(s) failed for $(RuntimeUser).');
    THROW 50003, @msg, 1;
END
PRINT 'All permission checks passed for $(RuntimeUser).';
