package com.nimokids.dto.request;

import com.nimokids.entity.enums.SupportFeedbackStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** PATCH /api/v1/admin/support-feedback/{id}: the team moves a message along and may leave a note. */
public record UpdateSupportFeedbackRequest(
        @NotNull(message = "must not be null") SupportFeedbackStatus status,
        @Size(max = 1000, message = "must be at most 1000 characters") String adminNote) {
}
