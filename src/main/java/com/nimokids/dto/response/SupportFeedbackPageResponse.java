package com.nimokids.dto.response;

import java.util.List;

/** One page of feedback for the admin list. */
public record SupportFeedbackPageResponse(
        List<SupportFeedbackResponse> items, int page, int size, long totalItems, int totalPages) {
}
