package com.nimokids.service.impl;

import com.nimokids.dto.request.CreateSupportFeedbackRequest;
import com.nimokids.dto.request.UpdateSupportFeedbackRequest;
import com.nimokids.dto.response.SupportFeedbackPageResponse;
import com.nimokids.dto.response.SupportFeedbackResponse;
import com.nimokids.entity.SupportFeedback;
import com.nimokids.entity.enums.SupportFeedbackStatus;
import com.nimokids.exception.BusinessException;
import com.nimokids.exception.ErrorCode;
import com.nimokids.exception.ResourceNotFoundException;
import com.nimokids.repository.SupportFeedbackRepository;
import com.nimokids.service.PlayerService;
import com.nimokids.service.SupportFeedbackService;
import com.nimokids.service.auth.RateLimiter;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SupportFeedbackServiceImpl implements SupportFeedbackService {

    static final int MAX_PER_HOUR = 5;
    static final int MAX_PAGE_SIZE = 100;

    private final SupportFeedbackRepository repository;
    private final PlayerService playerService;
    private final RateLimiter rateLimiter;

    @Override
    @Transactional
    public void submit(UUID anonymousId, CreateSupportFeedbackRequest request) {
        if (!rateLimiter.tryAcquire("support-feedback:" + anonymousId, MAX_PER_HOUR, Duration.ofHours(1))) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, ErrorCode.RATE_LIMITED.getDefaultMessage(),
                    Map.of("retryAfterSeconds", 3600));
        }
        repository.save(SupportFeedback.builder()
                .player(playerService.resolveOrCreate(anonymousId))
                .category(request.category())
                .message(request.message().trim())
                .contact(blankToNull(request.contact()))
                .language(blankToNull(request.language()))
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public SupportFeedbackPageResponse list(SupportFeedbackStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<SupportFeedback> result = status == null ? repository.findAll(pageable) : repository.findByStatus(status, pageable);
        return new SupportFeedbackPageResponse(result.map(SupportFeedbackResponse::from).getContent(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Override
    @Transactional
    public SupportFeedbackResponse update(UUID id, UpdateSupportFeedbackRequest request) {
        SupportFeedback entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Feedback", id));
        entity.setStatus(request.status());
        entity.setAdminNote(blankToNull(request.adminNote()));
        return SupportFeedbackResponse.from(entity);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
