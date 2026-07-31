package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodProductReviewCaseExtractionEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FoodProductReviewCaseExtractionRepository
        extends JpaRepository<FoodProductReviewCaseExtractionEntity, Long> {
    Optional<FoodProductReviewCaseExtractionEntity> findByReviewCaseId(Long reviewCaseId);

    @Query(value = """
            select extraction.*
            from food_product_review_case_extractions extraction
            where extraction.raw_payload_deleted_at is null
              and extraction.raw_payload_expires_at <= :now
            order by extraction.id
            for update skip locked
            """, nativeQuery = true)
    List<FoodProductReviewCaseExtractionEntity> lockExpiredRawPayloads(
            @Param("now") LocalDateTime now, Pageable pageable);

    @Modifying
    @Query("""
            update FoodProductReviewCaseExtractionEntity extraction
               set extraction.recognizedLinesJson = '[]', extraction.rawPayloadDeletedAt = :deletedAt
             where extraction.id = :id and extraction.rawPayloadDeletedAt is null
            """)
    int redactRawPayload(@Param("id") Long id, @Param("deletedAt") LocalDateTime deletedAt);
}