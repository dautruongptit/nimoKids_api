-- Indexes for foreign keys that are hit when rows are deleted (docs/DATABASE_CODE_OPTIMIZATION_REPORT.md, I1-I3).
-- Additive only: no table, column or row is changed.
--
--   I1 refresh_tokens.replaced_by_id is a self-reference ON DELETE SET NULL. The hourly cleanup deletes expired tokens
--      (RefreshTokenRepository.deleteExpiredBefore) and ended login sessions (cascade to their tokens); for EVERY
--      deleted token PostgreSQL looks for rows pointing at it, which without an index is a scan of the whole table.
--   I2 deleteExpiredBefore filters on expires_at for every status, so the partial index idx_refresh_tokens_expiry
--      (status = 'ACTIVE' only) cannot serve it.
--   I3 activity_logs.question_id / topic_id are ON DELETE SET NULL: deleting a question or a topic (as V27 and V31
--      did) scans activity_logs once per deleted row.
--
-- Deliberately NOT "CREATE INDEX CONCURRENTLY": Flyway holds its PostgreSQL lock in an open transaction, and a
-- concurrent build waits for every open transaction to finish, so the migration (and the application start) would
-- hang forever. This was reproduced on a scratch database. Both tables are small (refresh_tokens: tens of rows,
-- activity_logs: thousands), so a plain build inside the migration transaction takes milliseconds.
-- If activity_logs ever grows to millions of rows before this runs, build these indexes by hand with CONCURRENTLY
-- first: IF NOT EXISTS then makes this migration a no-op.
--
-- Rollback (manual):
--   DROP INDEX IF EXISTS idx_activity_logs_topic_id;
--   DROP INDEX IF EXISTS idx_activity_logs_question_id;
--   DROP INDEX IF EXISTS idx_refresh_tokens_expires_at;
--   DROP INDEX IF EXISTS idx_refresh_tokens_replaced_by;
--   DELETE FROM flyway_schema_history WHERE version = '32';

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_replaced_by
    ON refresh_tokens (replaced_by_id) WHERE replaced_by_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires_at
    ON refresh_tokens (expires_at);

CREATE INDEX IF NOT EXISTS idx_activity_logs_question_id
    ON activity_logs (question_id) WHERE question_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_activity_logs_topic_id
    ON activity_logs (topic_id) WHERE topic_id IS NOT NULL;
