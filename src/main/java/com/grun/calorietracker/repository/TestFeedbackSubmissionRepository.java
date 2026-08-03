package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.TestFeedbackSubmissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface TestFeedbackSubmissionRepository extends JpaRepository<TestFeedbackSubmissionEntity, Long>,
        JpaSpecificationExecutor<TestFeedbackSubmissionEntity> {
    Optional<TestFeedbackSubmissionEntity> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);
}
