package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AiWorkoutPlanDraftRequestDto;
import com.grun.calorietracker.entity.ExerciseItemEntity;
import com.grun.calorietracker.enums.ExerciseDifficulty;
import com.grun.calorietracker.enums.ExerciseTechniqueReviewStatus;
import com.grun.calorietracker.service.support.ExerciseCatalogCandidateSelector;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ExerciseCatalogCandidateSelectorTest {
    private final ExerciseCatalogCandidateSelector selector = new ExerciseCatalogCandidateSelector();

    @Test
    void excludesUnavailableEquipmentAndExplicitIds() {
        ExerciseItemEntity bodyweight = item(1L, "Push-Up", "Chest", "None");
        ExerciseItemEntity cable = item(2L, "Cable Fly", "Chest", "Cable Machine");
        ExerciseItemEntity dumbbell = item(3L, "Dumbbell Press", "Chest", "Dumbbells");
        AiWorkoutPlanDraftRequestDto request = request(List.of("DUMBBELLS"), List.of("CHEST"));
        request.setExcludedExerciseItemIds(List.of(3L));

        List<ExerciseItemEntity> result = selector.select(List.of(bodyweight, cable, dumbbell), request);

        assertEquals(List.of(1L), result.stream().map(ExerciseItemEntity::getId).toList());
    }

    @Test
    void keepsMuscleGroupsBalancedInsteadOfReturningOneDominantGroup() {
        AiWorkoutPlanDraftRequestDto request = request(List.of(), List.of());
        List<ExerciseItemEntity> result = selector.select(List.of(
                item(1L, "Push-Up", "Chest", "None"), item(2L, "Incline Push-Up", "Chest", "None"),
                item(3L, "Squat", "Legs", "None"), item(4L, "Plank", "Core", "Mat")), request);

        assertEquals(4, result.size());
        assertEquals(3, result.subList(0, 3).stream().map(ExerciseItemEntity::getPrimaryMuscleGroup).distinct().count());
    }

    private AiWorkoutPlanDraftRequestDto request(List<String> equipment, List<String> focus) {
        AiWorkoutPlanDraftRequestDto request = new AiWorkoutPlanDraftRequestDto();
        request.setEquipment(equipment); request.setFocusAreas(focus); request.setLevel("BEGINNER");
        return request;
    }

    private ExerciseItemEntity item(Long id, String name, String muscle, String equipment) {
        ExerciseItemEntity item = new ExerciseItemEntity(); item.setId(id); item.setName(name);
        item.setPrimaryMuscleGroup(muscle); item.setEquipment(equipment); item.setDifficulty(ExerciseDifficulty.BEGINNER);
        item.setActive(true); item.setAiEligible(true); item.setTechniqueReviewStatus(ExerciseTechniqueReviewStatus.APPROVED);
        return item;
    }
}
