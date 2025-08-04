package com.grun.calorietracker.controller;

import com.grun.calorietracker.entity.ExerciseItemEntity;
import com.grun.calorietracker.repository.ExerciseItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/exercise-items")
@RequiredArgsConstructor
public class ExerciseItemController {

    private final ExerciseItemRepository exerciseItemRepository;

    @GetMapping
    public ResponseEntity<List<ExerciseItemEntity>> getAllExerciseItems() {
        return ResponseEntity.ok(exerciseItemRepository.findAll());
    }
}
