package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.FoodProductContributionStatus;
import com.grun.calorietracker.enums.MarketRegion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Entity
@Table(name = "food_product_contributions")
@Data
@NoArgsConstructor
public class FoodProductContributionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submitted_by_user_id", nullable = false)
    private UserEntity submittedBy;

    @Column(nullable = false, length = 14)
    private String barcode;

    @Column(name = "normalized_barcode", nullable = false, length = 14)
    private String normalizedBarcode;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(nullable = false, length = 160)
    private String brand;

    @Enumerated(EnumType.STRING)
    @Column(name = "market_region", nullable = false, length = 20)
    private MarketRegion marketRegion;

    @Column(nullable = false)
    private Double calories;

    @Column(nullable = false)
    private Double protein;

    @Column(nullable = false)
    private Double fat;

    @Column(nullable = false)
    private Double carbs;

    private Double fiber;
    private Double sugar;
    private Double sodium;

    @Column(name = "serving_size_grams")
    private Double servingSizeGrams;

    @Column(name = "serving_unit", length = 40)
    private String servingUnit;

    @Column(name = "evidence_url", length = 2048)
    private String evidenceUrl;

    @Column(name = "evidence_storage_key", length = 1024)
    private String evidenceStorageKey;

    @Column(name = "evidence_content_type", length = 80)
    private String evidenceContentType;

    @Column(name = "evidence_size_bytes")
    private Long evidenceSizeBytes;

    @Column(name = "evidence_checksum", nullable = false, length = 64)
    private String evidenceChecksum;

    @Column(name = "evidence_retrieved_at", nullable = false)
    private OffsetDateTime evidenceRetrievedAt;

    @Column(name = "commercial_use_allowed", nullable = false)
    private Boolean commercialUseAllowed;

    @Column(name = "persistent_storage_allowed", nullable = false)
    private Boolean persistentStorageAllowed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FoodProductContributionStatus status;

    @Column(name = "reviewer_identity", length = 255)
    private String reviewerIdentity;

    @Column(name = "review_note", length = 1000)
    private String reviewNote;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_case_id", unique = true)
    private FoodProductReviewCaseEntity reviewCase;

    @Version
    private Long version;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = FoodProductContributionStatus.PENDING_REVIEW;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
