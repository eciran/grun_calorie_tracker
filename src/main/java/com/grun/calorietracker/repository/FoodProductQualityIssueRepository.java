package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodProductQualityIssueEntity;
import com.grun.calorietracker.enums.FoodProductQualityIssue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FoodProductQualityIssueRepository extends JpaRepository<FoodProductQualityIssueEntity, Long> {

    List<FoodProductQualityIssueEntity> findByFoodItemIdAndResolvedFalse(Long foodItemId);

    List<FoodProductQualityIssueEntity> findByIssueTypeAndResolvedFalse(
            FoodProductQualityIssue issueType,
            org.springframework.data.domain.Pageable pageable
    );

    List<FoodProductQualityIssueEntity> findByFoodItemIdInAndResolvedFalse(Collection<Long> foodItemIds);

    List<FoodProductQualityIssueEntity> findByFoodItemIdOrderByResolvedAscLastDetectedAtDesc(Long foodItemId);

    Optional<FoodProductQualityIssueEntity> findByFoodItemIdAndIssueTypeAndResolvedFalse(
            Long foodItemId,
            FoodProductQualityIssue issueType
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = """
            UPDATE food_product_quality_issues issue
               SET resolved = true,
                   resolved_at = current_timestamp,
                   resolved_by = :actor
             WHERE issue.issue_type = 'MISSING_CANONICAL_CATEGORY'
               AND issue.resolved = false
               AND NOT EXISTS (
                   SELECT 1
                   FROM food_items item
                   WHERE item.id = issue.food_item_id
                     AND COALESCE(item.is_custom, false) = false
                     AND EXISTS (
                         SELECT 1
                         FROM food_item_source_categories source
                         JOIN food_category_source_mappings mapping
                           ON mapping.data_source = item.data_source
                          AND mapping.normalized_source_tag = lower(trim(source.category_tag))
                          AND (mapping.market_region IS NULL OR mapping.market_region = item.market_region)
                          AND mapping.status IN ('ACTIVE', 'REVIEW_REQUIRED')
                         WHERE source.food_item_id = item.id
                     )
                     AND NOT EXISTS (
                         SELECT 1
                         FROM food_item_categories assignment
                         WHERE assignment.food_item_id = item.id
                           AND assignment.primary_category = true
                     )
               )
            """, nativeQuery = true)
    int resolveOutOfScopeCanonicalCategoryIssues(@Param("actor") String actor);
}
