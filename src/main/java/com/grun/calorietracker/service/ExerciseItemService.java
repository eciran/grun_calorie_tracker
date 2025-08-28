package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.ExerciseItemDto;
import java.util.List;

public interface ExerciseItemService {

    List<ExerciseItemDto> getAllItems();
    ExerciseItemDto addItem(ExerciseItemDto dto);
    ExerciseItemDto updateItem(Long id, ExerciseItemDto dto);
    void deleteItem(Long id);
}
