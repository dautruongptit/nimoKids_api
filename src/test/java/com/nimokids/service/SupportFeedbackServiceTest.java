package com.nimokids.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nimokids.dto.request.CreateSupportFeedbackRequest;
import com.nimokids.dto.request.UpdateSupportFeedbackRequest;
import com.nimokids.entity.AnonymousPlayer;
import com.nimokids.entity.SupportFeedback;
import com.nimokids.entity.enums.SupportFeedbackCategory;
import com.nimokids.entity.enums.SupportFeedbackStatus;
import com.nimokids.exception.BusinessException;
import com.nimokids.exception.ErrorCode;
import com.nimokids.exception.ResourceNotFoundException;
import com.nimokids.repository.SupportFeedbackRepository;
import com.nimokids.service.auth.RateLimiter;
import com.nimokids.service.impl.SupportFeedbackServiceImpl;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SupportFeedbackServiceTest {

    private final SupportFeedbackRepository repository = mock(SupportFeedbackRepository.class);
    private final PlayerService playerService = mock(PlayerService.class);
    private final RateLimiter rateLimiter = mock(RateLimiter.class);
    private final SupportFeedbackServiceImpl service = new SupportFeedbackServiceImpl(repository, playerService, rateLimiter);

    private static CreateSupportFeedbackRequest request(String message, String contact) {
        return new CreateSupportFeedbackRequest(SupportFeedbackCategory.IDEA, message, contact, "vi");
    }

    @Test
    void storesTheMessageTrimmedWithTheOptionalContactNullWhenBlank() {
        UUID anonymousId = UUID.randomUUID();
        AnonymousPlayer player = new AnonymousPlayer();
        when(rateLimiter.tryAcquire(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        when(playerService.resolveOrCreate(anonymousId)).thenReturn(player);

        service.submit(anonymousId, request("  Thêm chủ đề Gia đình nhé  ", "   "));

        ArgumentCaptor<SupportFeedback> saved = ArgumentCaptor.forClass(SupportFeedback.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getMessage()).isEqualTo("Thêm chủ đề Gia đình nhé");
        assertThat(saved.getValue().getContact()).isNull();
        assertThat(saved.getValue().getPlayer()).isSameAs(player);
        assertThat(saved.getValue().getStatus()).isEqualTo(SupportFeedbackStatus.NEW);
    }

    @Test
    void refusesWhenThePlayerSentTooMany() {
        UUID anonymousId = UUID.randomUUID();
        when(rateLimiter.tryAcquire(eq("support-feedback:" + anonymousId), eq(5), eq(Duration.ofHours(1)))).thenReturn(false);

        assertThatThrownBy(() -> service.submit(anonymousId, request("Xin chào đội ngũ", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.RATE_LIMITED);
        verify(repository, never()).save(any());
    }

    @Test
    void updateMovesTheStatusAndKeepsTheNote() {
        UUID id = UUID.randomUUID();
        SupportFeedback existing = SupportFeedback.builder().category(SupportFeedbackCategory.BUG).message("Lỗi âm thanh").build();
        when(repository.findById(id)).thenReturn(Optional.of(existing));

        var response = service.update(id, new UpdateSupportFeedbackRequest(SupportFeedbackStatus.DONE, " Đã sửa "));

        assertThat(response.status()).isEqualTo("DONE");
        assertThat(existing.getAdminNote()).isEqualTo("Đã sửa");
    }

    @Test
    void updateOfAnUnknownMessageIsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, new UpdateSupportFeedbackRequest(SupportFeedbackStatus.SEEN, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
