package com.nimokids.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Retention of the two log tables, which otherwise grow without limit (every request under /api writes an api_logs row).
 * Rows older than the configured number of days are deleted in small batches, each batch its own statement, so no
 * delete holds locks for long. 0 days switches the retention of that table off.
 *
 * Neither table is referenced by any other table, and scoring never reads them (activity_logs is analytics only).
 */
@Slf4j
@Component
public class LogRetentionJob {

    static final String DELETE_API_LOGS =
            "DELETE FROM api_logs WHERE id IN (SELECT id FROM api_logs WHERE created_at < ? LIMIT ?)";
    static final String DELETE_ACTIVITY_LOGS =
            "DELETE FROM activity_logs WHERE id IN (SELECT id FROM activity_logs WHERE created_at < ? LIMIT ?)";

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final int apiLogDays;
    private final int activityLogDays;
    private final int batchSize;
    private final int maxBatchesPerRun;

    public LogRetentionJob(JdbcTemplate jdbc, Clock clock,
                           @Value("${app.retention.api-logs-days:30}") int apiLogDays,
                           @Value("${app.retention.activity-logs-days:180}") int activityLogDays,
                           @Value("${app.retention.batch-size:5000}") int batchSize,
                           @Value("${app.retention.max-batches-per-run:100}") int maxBatchesPerRun) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.apiLogDays = apiLogDays;
        this.activityLogDays = activityLogDays;
        this.batchSize = batchSize;
        this.maxBatchesPerRun = maxBatchesPerRun;
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT15M")
    public void applyRetention() {
        purge("api_logs", DELETE_API_LOGS, apiLogDays);
        purge("activity_logs", DELETE_ACTIVITY_LOGS, activityLogDays);
    }

    /** Deletes the rows of one table older than {@code days}; returns how many were deleted. */
    long purge(String table, String sql, int days) {
        if (days <= 0) {
            return 0;
        }
        OffsetDateTime cutoff = OffsetDateTime.ofInstant(clock.instant().minus(Duration.ofDays(days)), ZoneOffset.UTC);
        long total = 0;
        try {
            for (int batch = 0; batch < maxBatchesPerRun; batch++) {
                int deleted = jdbc.update(sql, cutoff, batchSize);
                total += deleted;
                if (deleted < batchSize) {
                    break;
                }
            }
            if (total > 0) {
                log.info("Log retention: {} {} row(s) older than {} day(s) deleted", total, table, days);
            }
        } catch (RuntimeException ex) {
            log.warn("Log retention of {} failed after {} row(s): {}: {}", table, total, ex.getClass().getSimpleName(),
                    ex.getMessage());
        }
        return total;
    }
}
