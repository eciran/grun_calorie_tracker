package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.ExerciseLogsDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.service.ExerciseLogsService;
import com.grun.calorietracker.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/exercise-logs")
@RequiredArgsConstructor
public class ExerciseLogsController {

    private final ExerciseLogsService exerciseLogsService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<ExerciseLogsDto> addExerciseLog(@AuthenticationPrincipal UserDetails userDetails,
                                                          @RequestBody @Valid ExerciseLogsDto dto) {
        ExerciseLogsDto created = exerciseLogsService.addExerciseLog(dto, userDetails.getUsername());
        return ResponseEntity.ok(created);
    }


    @GetMapping
    public ResponseEntity<List<ExerciseLogsDto>> getAllExerciseLogs(@AuthenticationPrincipal UserDetails userDetails,
                                                                    @RequestParam(required = false) String date) {
        List<ExerciseLogsDto> logs = exerciseLogsService.getExerciseLogs(userDetails.getUsername(),date);
        return ResponseEntity.ok(logs);
    }

    // Get specific exercise log by id for user
    @GetMapping("/{id}")
    public ResponseEntity<ExerciseLogsDto> getExerciseLogById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        ExerciseLogsDto log = exerciseLogsService.getExerciseLogById(id, userDetails.getUsername());
        return ResponseEntity.ok(log);
    }

    // Delete exercise log by id for user
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteExerciseLog(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        exerciseLogsService.deleteExerciseLog(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}
