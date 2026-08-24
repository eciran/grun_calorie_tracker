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
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FoodItemRepository extends JpaRepository<FoodItemEntity, Long>, JpaSpecificationExecutor<FoodItemEntity> {
    @Modifying
    @Query("update FoodItemEntity f set f.searchSelectionCount = coalesce(f.searchSelectionCount, 0) + 1 where f.id = :id")
    int incrementSearchSelectionCount(@Param("id") Long id);

    @Query("""
            select f
            from FoodItemEntity f
            where f.dataSource in :sources
              and (f.lastExternalSyncAt is null or f.lastExternalSyncAt < :cutoff)
            order by f.lastExternalSyncAt asc nulls first, f.id asc
            """)
    List<FoodItemEntity> findStaleExternalProducts(
            @Param("sources") Collection<com.grun.calorietracker.enums.FoodDataSource> sources,
            @Param("cutoff") LocalDateTime cutoff,
            Pageable pageable
    );

    Optional<FoodItemEntity> findByBarcode(String barcode);
    Optional<FoodItemEntity> findByNormalizedBarcode(String normalizedBarcode);
    Optional<FoodItemEntity> findBySourceKey(String sourceKey);
    @EntityGraph(attributePaths = "marketRegions")
    List<FoodItemEntity> findByNormalizedBarcodeIn(List<String> normalizedBarcodes, Sort sort);
    @EntityGraph(attributePaths = "marketRegions")
    List<FoodItemEntity> findBySourceKeyIn(List<String> sourceKeys, Sort sort);
    List<FoodItemEntity> findByCanonicalFoodKeyIn(List<String> canonicalFoodKeys, Sort sort);
    List<FoodItemEntity> findByVerificationStatus(VerificationStatus verificationStatus);
    List<FoodItemEntity> findByVerificationStatus(VerificationStatus verificationStatus, Sort sort);
    List<FoodItemEntity> findByImageStatus(ImageStatus imageStatus);
    List<FoodItemEntity> findByImageStatus(ImageStatus imageStatus, Sort sort);
    List<FoodItemEntity> findByVerificationStatusAndImageStatus(VerificationStatus verificationStatus, ImageStatus imageStatus);
    List<FoodItemEntity> findByVerificationStatusAndImageStatus(VerificationStatus verificationStatus, ImageStatus imageStatus, Sort sort);
    long countByVerificationStatus(VerificationStatus verificationStatus);

    @Query("""
            SELECT f.verificationStatus, COUNT(f)
            FROM FoodItemEntity f
            GROUP BY f.verificationStatus
            ORDER BY COUNT(f) DESC
            """)
    List<Object[]> summarizeVerificationStatuses();

    @Query("""
            SELECT COUNT(f)
            FROM FoodItemEntity f
            WHERE f.qualityValidatedAt IS NOT NULL
            """)
    long countQualityValidatedProducts();

    @Query("""
            SELECT AVG(f.qualityScore)
            FROM FoodItemEntity f
            WHERE f.qualityScore IS NOT NULL
            """)
    Double averageQualityScore();
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
                    f.publicationStatus = com.grun.calorietracker.enums.CatalogPublicationStatus.PUBLISHED
                    OR (
                        f.publicationStatus = com.grun.calorietracker.enums.CatalogPublicationStatus.PRIVATE_USER
                        AND f.createdByUser = :user
                    )
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

    @Query(
            value = """
                    SELECT canonical_food_key
                    FROM food_items
                    WHERE canonical_food_key IS NOT NULL
                      AND catalog_type = 'GENERIC_INGREDIENT'
                    GROUP BY canonical_food_key
                    HAVING COUNT(id) > 1
                    ORDER BY canonical_food_key
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM (
                        SELECT canonical_food_key
                        FROM food_items
                        WHERE canonical_food_key IS NOT NULL
                          AND catalog_type = 'GENERIC_INGREDIENT'
                        GROUP BY canonical_food_key
                        HAVING COUNT(id) > 1
                    ) canonical_duplicate_groups
                    """,
            nativeQuery = true
    )
    Page<String> findDuplicateCanonicalFoodKeys(Pageable pageable);
    @Query(
            value = """
                    SELECT fi.canonical_food_key
                    FROM food_items fi
                    WHERE fi.canonical_food_key IS NOT NULL
                      AND fi.catalog_type = 'GENERIC_INGREDIENT'
                      AND EXISTS (
                          SELECT 1
                          FROM food_canonical_resolutions resolution
                          WHERE resolution.canonical_food_key = fi.canonical_food_key
                      )
                    GROUP BY fi.canonical_food_key
                    HAVING COUNT(fi.id) > 1
                    ORDER BY fi.canonical_food_key
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM (
                        SELECT fi.canonical_food_key
                        FROM food_items fi
                        WHERE fi.canonical_food_key IS NOT NULL
                          AND fi.catalog_type = 'GENERIC_INGREDIENT'
                          AND EXISTS (
                              SELECT 1
                              FROM food_canonical_resolutions resolution
                              WHERE resolution.canonical_food_key = fi.canonical_food_key
                          )
                        GROUP BY fi.canonical_food_key
                        HAVING COUNT(fi.id) > 1
                    ) canonical_duplicate_groups
                    """,
            nativeQuery = true
    )
    Page<String> findResolvedDuplicateCanonicalFoodKeys(Pageable pageable);

    @Query(
            value = """
                    SELECT fi.canonical_food_key
                    FROM food_items fi
                    WHERE fi.canonical_food_key IS NOT NULL
                      AND fi.catalog_type = 'GENERIC_INGREDIENT'
                      AND NOT EXISTS (
                          SELECT 1
                          FROM food_canonical_resolutions resolution
                          WHERE resolution.canonical_food_key = fi.canonical_food_key
                      )
                    GROUP BY fi.canonical_food_key
                    HAVING COUNT(fi.id) > 1
                    ORDER BY fi.canonical_food_key
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM (
                        SELECT fi.canonical_food_key
                        FROM food_items fi
                        WHERE fi.canonical_food_key IS NOT NULL
                          AND fi.catalog_type = 'GENERIC_INGREDIENT'
                          AND NOT EXISTS (
                              SELECT 1
                              FROM food_canonical_resolutions resolution
                              WHERE resolution.canonical_food_key = fi.canonical_food_key
                          )
                        GROUP BY fi.canonical_food_key
                        HAVING COUNT(fi.id) > 1
                    ) canonical_duplicate_groups
                    """,
            nativeQuery = true
    )
    Page<String> findUnresolvedDuplicateCanonicalFoodKeys(Pageable pageable);

    @Query("select count(f) from FoodItemEntity f where f.imageStatus is null or f.imageStatus <> com.grun.calorietracker.enums.ImageStatus.APPROVED")
    long countMissingApprovedMedia();

    @Query("select count(f) from FoodItemEntity f where f.dataSource <> com.grun.calorietracker.enums.FoodDataSource.MANUAL and (f.lastExternalSyncAt is null or f.lastExternalSyncAt < :cutoff)")
    long countStaleCatalogItems(@Param("cutoff") LocalDateTime cutoff);

    long countByReviewDueAtBefore(LocalDateTime cutoff);

    @Query(value = """
            SELECT COALESCE(data_source, 'UNKNOWN') AS source,
                   COUNT(*) AS item_count,
                   COUNT(*) FILTER (WHERE data_source <> 'MANUAL' AND (last_external_sync_at IS NULL OR last_external_sync_at < :cutoff)) AS stale_count
            FROM food_items
            GROUP BY COALESCE(data_source, 'UNKNOWN')
            ORDER BY item_count DESC
            """, nativeQuery = true)
    List<Object[]> summarizeSources(@Param("cutoff") LocalDateTime cutoff);
}
