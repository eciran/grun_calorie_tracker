package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.AiProvider;
import com.grun.calorietracker.enums.AiDraftRejectReason;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_request_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiRequestHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiRequestType requestType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiProvider provider;

    @Column(nullable = false)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiRequestStatus status;

    @Column(columnDefinition = "TEXT")
    private String inputPayload;

    @Column(columnDefinition = "TEXT")
    private String outputPayload;

    @Column(columnDefinition = "TEXT")
    private String confirmationPayload;

    @Column(columnDefinition = "TEXT")
    private String correctionSummary;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    private Boolean quotaConsumed = false;

    @Column(nullable = false)
    private Integer quotaConsumedAmount = 0;

    @Column(nullable = false)
    private Integer quotaRefundedAmount = 0;

    @Column(columnDefinition = "TEXT")
    private String quotaRefundReason;

    private String quotaRefundedBy;

    private LocalDateTime quotaRefundedAt;

    private Long latencyMs;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    private Double estimatedCost;

    private String costCurrency;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime confirmedAt;

    @Enumerated(EnumType.STRING)
    private AiDraftRejectReason rejectionReason;

    @Column(columnDefinition = "TEXT")
    private String rejectionFeedback;

    private LocalDateTime rejectedAt;
}
