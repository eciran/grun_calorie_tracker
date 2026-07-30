package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.FoodNutritionBasis;
import com.grun.calorietracker.enums.FoodProductResolutionMode;
import com.grun.calorietracker.enums.FoodProductReviewCaseSource;
import com.grun.calorietracker.enums.FoodProductReviewCaseStatus;
import com.grun.calorietracker.enums.FoodProductReviewRiskLevel;
import com.grun.calorietracker.enums.MarketRegion;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "food_product_review_cases")
@Data
@NoArgsConstructor
public class FoodProductReviewCaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FoodProductReviewCaseSource source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by_user_id")
    private UserEntity submittedBy;

    @Column(name = "source_reference", length = 100)
    private String sourceReference;

    @Column(name = "normalized_barcode", length = 14)
    private String normalizedBarcode;

    @Column(name = "original_barcode", length = 64)
    private String originalBarcode;

    @Enumerated(EnumType.STRING)
    @Column(name = "market_region", nullable = false, length = 20)
    private MarketRegion marketRegion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_item_id")
    private FoodItemEntity foodItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_mode", nullable = false, length = 30)
    private FoodProductResolutionMode resolutionMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private FoodProductReviewCaseStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    private FoodProductReviewRiskLevel riskLevel;

    @Column(name = "schema_version", nullable = false)
    private Integer schemaVersion;

    @Column(name = "submitted_values_json", nullable = false, columnDefinition = "TEXT")
    private String submittedValuesJson;

    @Column(name = "field_confidence_json", columnDefinition = "TEXT")
    private String fieldConfidenceJson;

    @Column(name = "correction_summary_json", columnDefinition = "TEXT")
    private String correctionSummaryJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "nutrition_basis", length = 30)
    private FoodNutritionBasis nutritionBasis;

    @Column(name = "consent_version", length = 30)
    private String consentVersion;

    @Column(name = "temporary_evidence_allowed", nullable = false)
    private Boolean temporaryEvidenceAllowed;

    @Column(name = "public_media_allowed", nullable = false)
    private Boolean publicMediaAllowed;

    @Column(name = "review_note", length = 1000)
    private String reviewNote;

    @Column(name = "reviewed_by", length = 255)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) status = FoodProductReviewCaseStatus.SUBMITTED;
        if (riskLevel == null) riskLevel = FoodProductReviewRiskLevel.MEDIUM;
        if (schemaVersion == null) schemaVersion = 1;
        if (temporaryEvidenceAllowed == null) temporaryEvidenceAllowed = false;
        if (publicMediaAllowed == null) publicMediaAllowed = false;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
