package com.nimokids.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimokids.service.impl.LogRetentionJob;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * P-01 on a REAL PostgreSQL: the batch DELETE statements of {@link LogRetentionJob} remove exactly the rows older than
 * the retention, and nothing else. Opt-in (DB_HOST). Rolled back.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DB_HOST", matches = ".+")
class LogRetentionIntegrationTest {

    @Autowired private JdbcTemplate jdbc;

    private final Instant now = Instant.now();

    private UUID apiLog(Duration age) {
        UUID requestId = UUID.randomUUID();
        Timestamp at = Timestamp.from(now.minus(age));
        jdbc.update("INSERT INTO api_logs (request_id, http_method, endpoint, status_code, created_at, updated_at) "
                + "VALUES (?, 'GET', '/api/it-retention', 200, ?, ?)", requestId, at, at);
        return requestId;
    }

    private UUID activityLog(Duration age) {
        UUID id = UUID.randomUUID();
        Timestamp at = Timestamp.from(now.minus(age));
        jdbc.update("INSERT INTO activity_logs (id, event_type, event_time, created_at, updated_at) "
                + "VALUES (?, 'GO_HOME', ?, ?, ?)", id, at, at, at);
        return id;
    }

    private boolean apiLogExists(UUID requestId) {
        return jdbc.queryForObject("SELECT count(*) FROM api_logs WHERE request_id = ?", Long.class, requestId) == 1;
    }

    private boolean activityLogExists(UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM activity_logs WHERE id = ?", Long.class, id) == 1;
    }

    @Test
    void onlyRowsOlderThanTheRetentionAreDeletedBatchByBatch() {
        UUID oldApi1 = apiLog(Duration.ofDays(31));
        UUID oldApi2 = apiLog(Duration.ofDays(400));
        UUID oldApi3 = apiLog(Duration.ofDays(45));
        UUID freshApi = apiLog(Duration.ofDays(29));
        UUID oldActivity = activityLog(Duration.ofDays(181));
        UUID freshActivity = activityLog(Duration.ofDays(179));

        // Batch size 2 forces several rounds; other rows of the shared database may also be old enough, which is fine.
        new LogRetentionJob(jdbc, Clock.fixed(now, ZoneOffset.UTC), 30, 180, 2, 10_000).applyRetention();

        assertThat(apiLogExists(oldApi1)).isFalse();
        assertThat(apiLogExists(oldApi2)).isFalse();
        assertThat(apiLogExists(oldApi3)).isFalse();
        assertThat(apiLogExists(freshApi)).isTrue();
        assertThat(activityLogExists(oldActivity)).isFalse();
        assertThat(activityLogExists(freshActivity)).isTrue();
    }

    @Test
    void zeroDaysKeepsEverything() {
        UUID oldApi = apiLog(Duration.ofDays(400));
        UUID oldActivity = activityLog(Duration.ofDays(400));

        new LogRetentionJob(jdbc, Clock.fixed(now, ZoneOffset.UTC), 0, 0, 2, 10).applyRetention();

        assertThat(apiLogExists(oldApi)).isTrue();
        assertThat(activityLogExists(oldActivity)).isTrue();
    }
}
