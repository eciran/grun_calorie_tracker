package com.grun.calorietracker.entity;

import com.grun.calorietracker.enums.FoodProductAssetDeletionState;
import com.grun.calorietracker.enums.FoodProductAssetUploadState;
import com.grun.calorietracker.enums.FoodProductReviewAssetType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "food_product_review_case_assets")
@Data
@NoArgsConstructor
public class FoodProductReviewCaseAssetEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_case_id")
    private FoodProductReviewCaseEntity reviewCase;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "upload_session_id", nullable = false)
    private FoodProductUploadSessionEntity uploadSession;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 30)
    private FoodProductReviewAssetType assetType;

    @Column(name = "storage_key", nullable = false, length = 1024)
    private String storageKey;
    @Column(name = "content_type", nullable = false, length = 80)
    private String contentType;
    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;
    private Integer width;
    private Integer height;
    @Column(nullable = false, length = 64)
    private String sha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "upload_state", nullable = false, length = 20)
    private FoodProductAssetUploadState uploadState;
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "deletion_state", nullable = false, length = 20)
    private FoodProductAssetDeletionState deletionState;
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
    @Column(name = "deletion_attempt_count", nullable = false)
    private Integer deletionAttemptCount;
    @Column(name = "last_deletion_error", length = 1000)
    private String lastDeletionError;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Version
    private Long version;
}
