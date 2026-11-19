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
 * Keeps user_sessions small: a login session that ended (expired, logged out, revoked, evicted) is history, and is deleted
 * once it is older than the configured number of days. ACTIVE sessions are never touched unless they are past their
 * absolute lifetime (a session nobody ever marked as expired).
 *
 * Safe by construction: refresh_tokens of the session go with it (ON DELETE CASCADE, they are hashed and already dead);
 * login_history and security_events keep a plain session_id without a foreign key, so the audit trail stays complete.
 * Deleted in small batches. 0 days switches the retention off.
 */
@Slf4j
@Component
public class SessionRetentionJob {

    public static final String DELETE_ENDED_SESSIONS =
            "DELETE FROM user_sessions WHERE id IN (SELECT id FROM user_sessions WHERE "
                    + "(status <> 'ACTIVE' AND COALESCE(ended_at, updated_at) < ?) OR absolute_expires_at < ? LIMIT ?)";

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final int days;
    private final int batchSize;
    private final int maxBatchesPerRun;

    public SessionRetentionJob(JdbcTemplate jdbc, Clock clock,
                               @Value("${app.retention.user-sessions-days:90}") int days,
                               @Value("${app.retention.batch-size:5000}") int batchSize,
                               @Value("${app.retention.max-batches-per-run:100}") int maxBatchesPerRun) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.days = days;
        this.batchSize = batchSize;
        this.maxBatchesPerRun = maxBatchesPerRun;
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT20M")
    public void applyRetention() {
        purge();
    }

    /** Deletes the sessions that ended more than {@code days} ago; returns how many were deleted. */
    long purge() {
        if (days <= 0) {
            return 0;
        }
        OffsetDateTime cutoff = OffsetDateTime.ofInstant(clock.instant().minus(Duration.ofDays(days)), ZoneOffset.UTC);
        long total = 0;
        try {
            for (int batch = 0; batch < maxBatchesPerRun; batch++) {
                int deleted = jdbc.update(DELETE_ENDED_SESSIONS, cutoff, cutoff, batchSize);
                total += deleted;
                if (deleted < batchSize) {
                    break;
                }
            }
            if (total > 0) {
                log.info("Session retention: {} ended login session(s) older than {} day(s) deleted", total, days);
            }
        } catch (RuntimeException ex) {
            log.warn("Session retention failed after {} row(s): {}: {}", total, ex.getClass().getSimpleName(), ex.getMessage());
        }
        return total;
    }
}
