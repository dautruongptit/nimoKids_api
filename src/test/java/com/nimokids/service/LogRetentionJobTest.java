package com.nimokids.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

/** P-01: retention of api_logs / activity_logs, batch by batch. */
class LogRetentionJobTest {

    private static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private com.nimokids.service.impl.LogRetentionJob job(int apiDays, int activityDays, int batch, int maxBatches) {
        return new com.nimokids.service.impl.LogRetentionJob(jdbc, clock, apiDays, activityDays, batch, maxBatches);
    }

    @Test
    void deletesInBatchesUntilABatchIsNotFull() {
        when(jdbc.update(anyString(), (Object) any(), (Object) any())).thenReturn(100, 100, 37, 0);

        job(30, 180, 100, 50).applyRetention();

        OffsetDateTime apiCutoff = OffsetDateTime.parse("2026-10-20T10:00:00Z");
        OffsetDateTime activityCutoff = OffsetDateTime.parse("2026-05-23T10:00:00Z");
        verify(jdbc, times(3)).update(eq("DELETE FROM api_logs WHERE id IN (SELECT id FROM api_logs WHERE created_at < ? LIMIT ?)"),
                eq(apiCutoff), eq(100));
        verify(jdbc, times(1)).update(
                eq("DELETE FROM activity_logs WHERE id IN (SELECT id FROM activity_logs WHERE created_at < ? LIMIT ?)"),
                eq(activityCutoff), eq(100));
    }

    @Test
    void zeroDaysSwitchesTheRetentionOfThatTableOff() {
        when(jdbc.update(anyString(), (Object) any(), (Object) any())).thenReturn(0);

        job(0, 0, 100, 50).applyRetention();

        verify(jdbc, never()).update(anyString(), (Object) any(), (Object) any());
    }

    @Test
    void oneRunStopsAfterTheMaximumNumberOfBatches() {
        when(jdbc.update(anyString(), (Object) any(), (Object) any())).thenReturn(100);

        job(30, 0, 100, 3).applyRetention();

        verify(jdbc, times(3)).update(anyString(), (Object) any(), (Object) any());
    }

    @Test
    void aDatabaseErrorIsLoggedAndDoesNotStopTheOtherTable() {
        when(jdbc.update(anyString(), (Object) any(), (Object) any()))
                .thenThrow(new DataAccessResourceFailureException("down"))
                .thenReturn(5);

        job(30, 180, 100, 50).applyRetention();

        verify(jdbc, times(2)).update(anyString(), (Object) any(), (Object) any());
    }
}
