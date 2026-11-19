package com.nimokids.entity;

import com.nimokids.entity.enums.SupportFeedbackCategory;
import com.nimokids.entity.enums.SupportFeedbackStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A message a parent sent to the product team from the Help screen. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "support_feedback")
public class SupportFeedback extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id")
    private AnonymousPlayer player;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private SupportFeedbackCategory category;

    @Column(name = "message", nullable = false, length = 2000)
    private String message;

    @Column(name = "contact", length = 200)
    private String contact;

    @Column(name = "language", length = 10)
    private String language;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SupportFeedbackStatus status = SupportFeedbackStatus.NEW;

    @Column(name = "admin_note", length = 1000)
    private String adminNote;
}
