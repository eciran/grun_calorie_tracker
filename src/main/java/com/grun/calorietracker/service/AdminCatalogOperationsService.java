package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AdminCatalogImportJobDto;
import com.grun.calorietracker.dto.AdminCatalogSummaryDto;
import com.grun.calorietracker.dto.CatalogReviewAssignmentRequestDto;
import com.grun.calorietracker.dto.ExerciseItemDto;
import com.grun.calorietracker.dto.ExerciseItemPageDto;
import com.grun.calorietracker.dto.ExerciseTechniqueReviewRequestDto;
import com.grun.calorietracker.enums.ExerciseDifficulty;
import com.grun.calorietracker.enums.ExerciseTechniqueReviewStatus;

import java.util.List;

public interface AdminCatalogOperationsService {
    AdminCatalogSummaryDto summary();

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
