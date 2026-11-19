package com.nimokids.controller;

import com.nimokids.dto.request.CreateSupportFeedbackRequest;
import com.nimokids.dto.response.ApiResponse;
import com.nimokids.service.SupportFeedbackService;
import com.nimokids.validation.AnonymousId;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Help screen: a parent sends feedback to the product team (no login needed). */
@RestController
@RequestMapping("/api/v1/support-feedback")
@RequiredArgsConstructor
public class SupportFeedbackController {

    private final SupportFeedbackService feedbackService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> send(@AnonymousId UUID anonymousId, @Valid @RequestBody CreateSupportFeedbackRequest request) {
        feedbackService.submit(anonymousId, request);
        return ApiResponse.success("Feedback received", null);
    }
}
