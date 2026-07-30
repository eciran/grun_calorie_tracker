package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodProductReviewCaseEntity;
import com.grun.calorietracker.enums.FoodProductReviewCaseSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FoodProductReviewCaseRepository extends JpaRepository<FoodProductReviewCaseEntity, Long>, JpaSpecificationExecutor<FoodProductReviewCaseEntity> {
    Optional<FoodProductReviewCaseEntity> findByIdempotencyKey(String idempotencyKey);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select reviewCase from FoodProductReviewCaseEntity reviewCase where reviewCase.id = :id")
    Optional<FoodProductReviewCaseEntity> findByIdForAssignment(@Param("id") Long id);
    List<FoodProductReviewCaseEntity> findAllBySubmittedByIdOrderByCreatedAtDesc(Long userId);
    Optional<FoodProductReviewCaseEntity> findBySourceAndSourceReference(FoodProductReviewCaseSource source, String sourceReference);

    @Modifying
    @Query("update FoodProductReviewCaseEntity reviewCase set reviewCase.submittedBy = null where reviewCase.submittedBy.id = :userId")
    int anonymizeSubmittedByUserId(@Param("userId") Long userId);
}
