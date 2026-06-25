package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.ExerciseItemDto;
import com.grun.calorietracker.dto.ExerciseItemPageDto;
import com.grun.calorietracker.enums.ExerciseDifficulty;
import java.util.List;

public interface ExerciseItemService {

    List<ExerciseItemDto> getAllItems();
    ExerciseItemPageDto searchItems(String query,
                                    String primaryMuscleGroup,
                                    String equipment,
                                    ExerciseDifficulty difficulty,
                                    Boolean active,
                                    int page,
                                    int size);
    ExerciseItemDto getItem(Long id);
    ExerciseItemDto addItem(ExerciseItemDto dto);
    ExerciseItemDto updateItem(Long id, ExerciseItemDto dto);
    void deleteItem(Long id);
}
