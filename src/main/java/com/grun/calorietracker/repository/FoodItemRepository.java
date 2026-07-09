package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.ProductQualitySuggestionSource;
import com.grun.calorietracker.enums.VerificationStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FoodItemRepository extends JpaRepository<FoodItemEntity, Long>, JpaSpecificationExecutor<FoodItemEntity> {
    Optional<FoodItemEntity> findByBarcode(String barcode);
    Optional<FoodItemEntity> findByNormalizedBarcode(String normalizedBarcode);
    Optional<FoodItemEntity> findBySourceKey(String sourceKey);
    List<FoodItemEntity> findByNormalizedBarcodeIn(List<String> normalizedBarcodes, Sort sort);
    List<FoodItemEntity> findBySourceKeyIn(List<String> sourceKeys, Sort sort);
    List<FoodItemEntity> findByVerificationStatus(VerificationStatus verificationStatus);
    List<FoodItemEntity> findByVerificationStatus(VerificationStatus verificationStatus, Sort sort);
    List<FoodItemEntity> findByImageStatus(ImageStatus imageStatus);
    List<FoodItemEntity> findByImageStatus(ImageStatus imageStatus, Sort sort);
    List<FoodItemEntity> findByVerificationStatusAndImageStatus(VerificationStatus verificationStatus, ImageStatus imageStatus);
    List<FoodItemEntity> findByVerificationStatusAndImageStatus(VerificationStatus verificationStatus, ImageStatus imageStatus, Sort sort);
    long countByVerificationStatus(VerificationStatus verificationStatus);
    List<FoodItemEntity> findByCreatedByUserAndIsCustomTrueOrderByNameAsc(
            com.grun.calorietracker.entity.UserEntity user,
            Pageable pageable
    );
    long deleteByCreatedByUserAndIsCustomTrue(UserEntity user);

    List<FoodItemEntity> findAll(Specification<FoodItemEntity> spec, Sort sort);

    @Query("""
            SELECT COUNT(f)
            FROM FoodItemEntity f
            WHERE f.verificationStatus IN :verificationStatuses
            """)
    long countReviewQueueProducts(@Param("verificationStatuses") Collection<VerificationStatus> verificationStatuses);

    @Query("""
            SELECT f
            FROM FoodItemEntity f
            WHERE lower(f.name) LIKE lower(concat('%', :name, '%'))
              AND (f.verificationStatus IS NULL OR f.verificationStatus <> com.grun.calorietracker.enums.VerificationStatus.REJECTED)
              AND (
                    f.isCustom IS NULL
                    OR f.isCustom = false
                    OR f.createdByUser = :user
                  )
            ORDER BY
              CASE WHEN f.verificationStatus = com.grun.calorietracker.enums.VerificationStatus.VERIFIED THEN 0 ELSE 1 END,
              f.qualityScore DESC NULLS LAST,
              f.usageCount DESC NULLS LAST,
              f.name ASC
            """)
    List<FoodItemEntity> findVisibleAiMatchCandidates(@Param("name") String name,
                                                       @Param("user") UserEntity user,
                                                       Pageable pageable);

    @Query("""
            SELECT f
            FROM FoodItemEntity f
            WHERE f.qualityValidatedAt >= :startedAt
              AND f.qualityValidatedAt <= :completedAt
              AND (:source IS NULL OR f.qualityValidationSource = :source)
              AND (:marketRegion IS NULL OR f.marketRegion = :marketRegion)
            ORDER BY f.id ASC
            """)
    List<FoodItemEntity> findQualityValidatedDuringScan(@Param("startedAt") LocalDateTime startedAt,
                                                         @Param("completedAt") LocalDateTime completedAt,
                                                         @Param("source") ProductQualitySuggestionSource source,
                                                         @Param("marketRegion") MarketRegion marketRegion,
                                                         Pageable pageable);
    @Query(
            value = """
                    SELECT normalized_barcode
                    FROM food_items
                    WHERE normalized_barcode IS NOT NULL
                    GROUP BY normalized_barcode
                    HAVING COUNT(id) > 1
                    ORDER BY normalized_barcode
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM (
                        SELECT normalized_barcode
                        FROM food_items
                        WHERE normalized_barcode IS NOT NULL
                        GROUP BY normalized_barcode
                        HAVING COUNT(id) > 1
                    ) duplicate_groups
                    """,
            nativeQuery = true
    )
    Page<String> findDuplicateNormalizedBarcodes(Pageable pageable);
}
