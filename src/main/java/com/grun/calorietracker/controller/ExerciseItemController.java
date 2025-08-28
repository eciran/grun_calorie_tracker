package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.ExerciseItemDto;
import com.grun.calorietracker.service.ExerciseItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/exercise-items")
@RequiredArgsConstructor
public class ExerciseItemController {

    private final ExerciseItemService exerciseItemService;

    @GetMapping
    public ResponseEntity<List<ExerciseItemDto>> getAllItems() {
        return ResponseEntity.ok(exerciseItemService.getAllItems());
    }

    @PostMapping
    public ResponseEntity<ExerciseItemDto> addExerciseItem(@RequestBody @Valid ExerciseItemDto dto) {
        ExerciseItemDto created = exerciseItemService.addItem(dto);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ExerciseItemDto> updateExerciseItem(@PathVariable Long id,
                                                              @RequestBody @Valid ExerciseItemDto dto) {
        ExerciseItemDto updated = exerciseItemService.updateItem(id, dto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteExerciseItem(@PathVariable Long id) {
        exerciseItemService.deleteItem(id);
        return ResponseEntity.noContent().build();
    }
}
