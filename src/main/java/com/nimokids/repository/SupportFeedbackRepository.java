package com.nimokids.repository;

import com.nimokids.entity.SupportFeedback;
import com.nimokids.entity.enums.SupportFeedbackStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportFeedbackRepository extends JpaRepository<SupportFeedback, UUID> {

    /** Newest first; the pageable carries the sort. */
    Page<SupportFeedback> findByStatus(SupportFeedbackStatus status, Pageable pageable);
}
