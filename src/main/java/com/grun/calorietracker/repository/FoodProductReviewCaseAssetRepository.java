package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodProductReviewCaseAssetEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface FoodProductReviewCaseAssetRepository extends JpaRepository<FoodProductReviewCaseAssetEntity, Long> {
    List<FoodProductReviewCaseAssetEntity> findAllByUploadSessionIdOrderByAssetTypeAsc(String uploadSessionId);
    List<FoodProductReviewCaseAssetEntity> findAllByUploadSessionCreatedById(Long userId);
    List<FoodProductReviewCaseAssetEntity> findAllByReviewCaseIdOrderByAssetTypeAsc(Long reviewCaseId);

    @Query(value = """
            select asset.*
            from food_product_review_case_assets asset
            join food_product_upload_sessions upload_session on upload_session.id = asset.upload_session_id
            left join food_product_review_cases review_case on review_case.id = asset.review_case_id
            where asset.deletion_state in ('ACTIVE', 'FAILED')
              and (asset.expires_at <= :now
                   or review_case.status in ('WITHDRAWN', 'EXPIRED')
                   or (asset.review_case_id is null
                       and upload_session.status <> 'FINALIZED'
                       and upload_session.expires_at <= :now))
            order by asset.id
            for update of asset skip locked
            """, nativeQuery = true)
    List<FoodProductReviewCaseAssetEntity> lockCleanupBatch(@Param("now") LocalDateTime now, Pageable pageable);

    @Modifying
    @Query("""
            update FoodProductReviewCaseAssetEntity asset set asset.expiresAt = :now
             where asset.reviewCase.id = :caseId
               and asset.deletionState in (com.grun.calorietracker.enums.FoodProductAssetDeletionState.ACTIVE,
                                           com.grun.calorietracker.enums.FoodProductAssetDeletionState.FAILED)
            """)
    int expireReviewCaseAssets(@Param("caseId") Long caseId, @Param("now") LocalDateTime now);
}
