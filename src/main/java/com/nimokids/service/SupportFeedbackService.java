package com.nimokids.service;

import com.nimokids.dto.request.CreateSupportFeedbackRequest;
import com.nimokids.dto.request.UpdateSupportFeedbackRequest;
import com.nimokids.dto.response.SupportFeedbackPageResponse;
import com.nimokids.dto.response.SupportFeedbackResponse;
import com.nimokids.entity.enums.SupportFeedbackStatus;
import java.util.UUID;

public interface SupportFeedbackService {

    /** Stores a parent's message. At most a few per hour per player (RATE_LIMITED otherwise). */
    void submit(UUID anonymousId, CreateSupportFeedbackRequest request);

    /** Newest first; {@code status} null = every status. */
    SupportFeedbackPageResponse list(SupportFeedbackStatus status, int page, int size);

    SupportFeedbackResponse update(UUID id, UpdateSupportFeedbackRequest request);
}
