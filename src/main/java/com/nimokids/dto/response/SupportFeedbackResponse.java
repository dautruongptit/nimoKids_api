package com.nimokids.dto.response;

import com.nimokids.entity.SupportFeedback;
import java.time.Instant;
import java.util.UUID;

/** A feedback message as the admin screen sees it. */
public record SupportFeedbackResponse(
        UUID id, String category, String message, String contact, String language, String status, String adminNote,
        Instant createdAt) {

    public static SupportFeedbackResponse from(SupportFeedback entity) {
        return new SupportFeedbackResponse(entity.getId(), entity.getCategory().name(), entity.getMessage(),
                entity.getContact(), entity.getLanguage(), entity.getStatus().name(), entity.getAdminNote(),
                entity.getCreatedAt());
    }
}
