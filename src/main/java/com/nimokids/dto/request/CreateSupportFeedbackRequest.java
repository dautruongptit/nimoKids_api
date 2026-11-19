package com.nimokids.dto.request;

import com.nimokids.entity.enums.SupportFeedbackCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** POST /api/v1/support-feedback: what a parent writes in the Help screen. */
public record CreateSupportFeedbackRequest(
        @NotNull(message = "must not be null") SupportFeedbackCategory category,
        @NotBlank(message = "must not be blank") @Size(min = 5, max = 2000, message = "must be 5 to 2000 characters") String message,
        @Size(max = 200, message = "must be at most 200 characters") String contact,
        @Size(max = 10, message = "must be at most 10 characters") String language) {
}
