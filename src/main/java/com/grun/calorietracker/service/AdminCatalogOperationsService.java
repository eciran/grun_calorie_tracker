package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminCatalogImportJobDto;
import com.grun.calorietracker.dto.AdminCatalogSummaryDto;
import com.grun.calorietracker.dto.AdminCatalogQualityAnalyticsDto;
import com.grun.calorietracker.dto.CatalogReviewAssignmentRequestDto;
import com.grun.calorietracker.dto.ExerciseItemDto;
import com.grun.calorietracker.dto.ExerciseItemPageDto;
import com.grun.calorietracker.dto.ExerciseTechniqueReviewRequestDto;
import com.grun.calorietracker.enums.ExerciseDifficulty;
import com.grun.calorietracker.enums.ExerciseTechniqueReviewStatus;

import java.util.List;

public interface AdminCatalogOperationsService {
    record ExerciseCategory(String category, long total) {}
    record ExerciseOverview(java.util.List<ExerciseCategory> muscleGroups,
                            java.util.List<ExerciseCategory> bodyScopes,
                            long total, long pendingReview, long missingMedia,
                            long missingMeasurement, boolean filtered) {}
    record ExerciseFacets(java.util.List<String> primaryMuscleGroups,
                          java.util.List<String> secondaryMuscleGroups,
                          java.util.List<String> equipment) {}
    ExerciseFacets exerciseFacets();
    ExerciseOverview exerciseOverview(String query, ExerciseDifficulty difficulty, Boolean active,
            ExerciseTechniqueReviewStatus reviewStatus, String assignee, String category);
    ExerciseItemPageDto searchExercises(String query, ExerciseDifficulty difficulty, Boolean active,
            ExerciseTechniqueReviewStatus reviewStatus, String assignee, int page, int size, String category);
    AdminCatalogSummaryDto summary();

    AdminCatalogQualityAnalyticsDto qualityAnalytics(int windowDays);

    List<AdminCatalogImportJobDto> recentImportJobs();

    ExerciseItemPageDto searchExercises(String query,
                                        ExerciseDifficulty difficulty,
                                        Boolean active,
                                        ExerciseTechniqueReviewStatus reviewStatus,
                                        String assignee,
                                        int page,
                                        int size);

    ExerciseItemDto createExercise(String adminEmail, ExerciseItemDto request);

    ExerciseItemDto updateExercise(String adminEmail, Long id, ExerciseItemDto request);

    ExerciseItemDto reviewExercise(String adminEmail, Long id, ExerciseTechniqueReviewRequestDto request);

    void assignReview(String adminEmail,
                      String itemType,
                      Long itemId,
                      CatalogReviewAssignmentRequestDto request);
}
