package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodProductReviewCaseEntity;
import com.grun.calorietracker.enums.FoodProductReviewCaseSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FoodProductReviewCaseRepository extends JpaRepository<FoodProductReviewCaseEntity, Long> {
    Optional<FoodProductReviewCaseEntity> findByIdempotencyKey(String idempotencyKey);
    List<FoodProductReviewCaseEntity> findAllBySubmittedByIdOrderByCreatedAtDesc(Long userId);
    Optional<FoodProductReviewCaseEntity> findBySourceAndSourceReference(FoodProductReviewCaseSource source, String sourceReference);

    @Modifying
    @Query("update FoodProductReviewCaseEntity reviewCase set reviewCase.submittedBy = null where reviewCase.submittedBy.id = :userId")
    int anonymizeSubmittedByUserId(@Param("userId") Long userId);
}
