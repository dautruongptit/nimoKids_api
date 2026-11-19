package com.nimokids.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nimokids.service.impl.SessionRetentionJob;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Retention of ended login sessions (user_sessions), batch by batch. */
class SessionRetentionJobTest {

    private static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private SessionRetentionJob job(int days, int batch, int maxBatches) {
        return new SessionRetentionJob(jdbc, clock, days, batch, maxBatches);
    }

    @Test
    void deletesInBatchesUntilABatchIsNotFull() {
        when(jdbc.update(anyString(), (Object) any(), (Object) any(), (Object) any())).thenReturn(100, 100, 7);

        job(90, 100, 50).applyRetention();

        OffsetDateTime cutoff = OffsetDateTime.parse("2026-08-21T10:00:00Z");
        verify(jdbc, times(3)).update(eq(SessionRetentionJobSql.DELETE), eq(cutoff), eq(cutoff), eq(100));
    }

    @Test
    void onlyEndedOrExpiredSessionsAreSelected() {
        assert SessionRetentionJobSql.DELETE.contains("status <> 'ACTIVE'");
        assert SessionRetentionJobSql.DELETE.contains("absolute_expires_at < ?");
    }

    @Test
    void zeroDaysSwitchesTheRetentionOff() {
        job(0, 100, 50).applyRetention();

        verify(jdbc, never()).update(anyString(), (Object) any(), (Object) any(), (Object) any());
    }

    @Test
    void oneRunStopsAfterTheMaximumNumberOfBatches() {
        when(jdbc.update(anyString(), (Object) any(), (Object) any(), (Object) any())).thenReturn(100);

        job(90, 100, 3).applyRetention();

        verify(jdbc, times(3)).update(anyString(), (Object) any(), (Object) any(), (Object) any());
    }

    @Test
    void aDatabaseErrorIsLoggedAndDoesNotEscape() {
        when(jdbc.update(anyString(), (Object) any(), (Object) any(), (Object) any()))
                .thenThrow(new DataAccessResourceFailureException("down"));

        job(90, 100, 50).applyRetention();
    }

    /** The statement is package-private in the job; the test repeats it so a change to it is a conscious one. */
    private static final class SessionRetentionJobSql {
        static final String DELETE = "DELETE FROM user_sessions WHERE id IN (SELECT id FROM user_sessions WHERE "
                + "(status <> 'ACTIVE' AND COALESCE(ended_at, updated_at) < ?) OR absolute_expires_at < ? LIMIT ?)";
    }
}
