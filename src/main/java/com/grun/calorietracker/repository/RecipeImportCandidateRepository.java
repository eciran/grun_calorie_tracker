package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.RecipeImportCandidateEntity;
import com.grun.calorietracker.enums.RecipeImportCandidateStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipeImportCandidateRepository extends JpaRepository<RecipeImportCandidateEntity, Long> {
    long countByStatus(RecipeImportCandidateStatus status);

    boolean existsByBatchIdAndSourceKey(String batchId, String sourceKey);

    Page<RecipeImportCandidateEntity> findByStatus(RecipeImportCandidateStatus status, Pageable pageable);

    Page<RecipeImportCandidateEntity> findByBatchIdContainingIgnoreCase(String batchId, Pageable pageable);

    Page<RecipeImportCandidateEntity> findByStatusAndBatchIdContainingIgnoreCase(RecipeImportCandidateStatus status, String batchId, Pageable pageable);
}
