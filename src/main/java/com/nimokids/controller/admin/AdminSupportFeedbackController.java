package com.nimokids.controller.admin;

import com.nimokids.dto.request.UpdateSupportFeedbackRequest;
import com.nimokids.dto.response.ApiResponse;
import com.nimokids.dto.response.SupportFeedbackPageResponse;
import com.nimokids.dto.response.SupportFeedbackResponse;
import com.nimokids.entity.enums.SupportFeedbackStatus;
import com.nimokids.service.SupportFeedbackService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The team reads the feedback parents sent from the Help screen and moves each message along. */
@RestController
@RequestMapping("/api/v1/admin/support-feedback")
@RequiredArgsConstructor
public class AdminSupportFeedbackController {

    private final SupportFeedbackService feedbackService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ApiResponse<SupportFeedbackPageResponse> list(
            @RequestParam(required = false) SupportFeedbackStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(feedbackService.list(status, page, size));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ApiResponse<SupportFeedbackResponse> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateSupportFeedbackRequest request) {
        return ApiResponse.success(feedbackService.update(id, request));
    }
}
