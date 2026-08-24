package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.TestFeedbackScreenshotEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TestFeedbackScreenshotEventRepository extends JpaRepository<TestFeedbackScreenshotEventEntity, Long> {
    List<TestFeedbackScreenshotEventEntity> findByFeedbackIdOrderByCreatedAtAscIdAsc(Long feedbackId);
}
