-- Index used by the log retention job (LogRetentionJob, docs/DATABASE_CODE_OPTIMIZATION_PLAN.md P-01).
-- The job deletes activity_logs rows older than the retention by created_at, in batches; api_logs already has
-- idx_api_logs_created_at (V1). Additive only. Not CONCURRENTLY: Flyway keeps its lock in an open transaction and a
-- concurrent build would wait for it forever (see V32).
--
-- Rollback (manual):
--   DROP INDEX IF EXISTS idx_activity_logs_created_at;
--   DELETE FROM flyway_schema_history WHERE version = '33';

CREATE INDEX IF NOT EXISTS idx_activity_logs_created_at ON activity_logs (created_at);
