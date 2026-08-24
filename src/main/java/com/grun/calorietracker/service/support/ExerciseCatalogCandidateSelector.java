package com.grun.calorietracker.service.support;

import com.grun.calorietracker.dto.AiWorkoutPlanDraftRequestDto;
import com.grun.calorietracker.entity.ExerciseItemEntity;
import com.grun.calorietracker.enums.ExerciseTechniqueReviewStatus;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class ExerciseCatalogCandidateSelector {
    private static final int LIMIT = 60;

    public List<ExerciseItemEntity> select(List<ExerciseItemEntity> catalog, AiWorkoutPlanDraftRequestDto request) {
        Set<Long> excluded = new HashSet<>(request.getExcludedExerciseItemIds() == null ? List.of() : request.getExcludedExerciseItemIds());
        List<String> equipment = terms(request.getEquipment());
        List<String> focus = terms(request.getFocusAreas());
        String level = text(request.getLevel());
        List<ExerciseItemEntity> eligible = catalog.stream()
                .filter(item -> Boolean.TRUE.equals(item.getActive()) && Boolean.TRUE.equals(item.getAiEligible()))
                .filter(item -> item.getTechniqueReviewStatus() == ExerciseTechniqueReviewStatus.APPROVED)
                .filter(item -> item.getId() == null || !excluded.contains(item.getId()))
                .filter(item -> equipmentCompatible(item, equipment))
                .sorted(Comparator.comparingInt((ExerciseItemEntity item) -> score(item, equipment, focus, level)).reversed()
                        .thenComparing(ExerciseItemEntity::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();

        Map<String, Deque<ExerciseItemEntity>> groups = eligible.stream().collect(Collectors.groupingBy(
                item -> text(item.getPrimaryMuscleGroup()).isBlank() ? "other" : text(item.getPrimaryMuscleGroup()),
                LinkedHashMap::new, Collectors.toCollection(ArrayDeque::new)));
        List<ExerciseItemEntity> selected = new ArrayList<>();
        while (selected.size() < LIMIT && groups.values().stream().anyMatch(queue -> !queue.isEmpty())) {
            groups.values().forEach(queue -> { if (selected.size() < LIMIT && !queue.isEmpty()) selected.add(queue.removeFirst()); });
        }
        return selected;
    }

    private boolean equipmentCompatible(ExerciseItemEntity item, List<String> available) {
        String required = text(item.getEquipment());
        if (required.isBlank() || required.equals("none") || required.equals("mat") || required.contains("optional")) return true;
        if (available.isEmpty()) return false;
        return available.stream().anyMatch(value -> required.contains(value) || value.contains(required)
                || (required.contains("dumbbell") && value.contains("dumbbell"))
                || (required.contains("barbell") && value.contains("barbell"))
                || (required.contains("cable") && value.contains("cable"))
                || (required.contains("bench") && value.contains("bench")));
    }

    private int score(ExerciseItemEntity item, List<String> equipment, List<String> focus, String level) {
        String required = text(item.getEquipment());
        String muscles = text(item.getPrimaryMuscleGroup()) + " " + text(item.getSecondaryMuscleGroups());
        int score = !required.isBlank()
                && equipment.stream().anyMatch(value -> required.contains(value) || value.contains(required)) ? 40 : 0;
        score += focus.stream().anyMatch(value -> muscles.contains(value) || value.contains(text(item.getPrimaryMuscleGroup()))) ? 60 : 0;
        if (item.getDifficulty() != null && text(item.getDifficulty().name()).equals(level)) score += 20;
        if (required.equals("none") || required.equals("mat")) score += 5;
        return score;
    }

    private List<String> terms(List<String> values) {
        return values == null ? List.of() : values.stream().filter(Objects::nonNull).map(this::text).filter(v -> !v.isBlank()).toList();
    }
    private String text(Object value) { return Objects.toString(value, "").trim().toLowerCase(Locale.ROOT); }
}
