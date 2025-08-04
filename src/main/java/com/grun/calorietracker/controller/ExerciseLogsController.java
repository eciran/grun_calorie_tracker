package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.ExerciseLogsDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.service.ExerciseLogsService;
import com.grun.calorietracker.service.UserService;
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

    // Add new exercise log for authenticated user
    @PostMapping
    public ResponseEntity<ExerciseLogsDto> addExerciseLog(
            @RequestBody ExerciseLogsDto request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UserEntity user = userService.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
        ExerciseLogsDto response = exerciseLogsService.addExerciseLog(request, user);
        return ResponseEntity.ok(response);
    }

    // Get all exercise logs for authenticated user, optionally filter by date
    @GetMapping
    public ResponseEntity<List<ExerciseLogsDto>> getExerciseLogs(
            @RequestParam(required = false) String date,
            @AuthenticationPrincipal UserDetails userDetails) {
        UserEntity user = userService.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
        List<ExerciseLogsDto> logs = exerciseLogsService.getExerciseLogs(user, date);
        return ResponseEntity.ok(logs);
    }

    // Get specific exercise log by id for user
    @GetMapping("/{id}")
    public ResponseEntity<ExerciseLogsDto> getExerciseLogById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        UserEntity user = userService.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
        ExerciseLogsDto log = exerciseLogsService.getExerciseLogById(id, user);
        return ResponseEntity.ok(log);
    }

    // Delete exercise log by id for user
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteExerciseLog(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        UserEntity user = userService.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));
        exerciseLogsService.deleteExerciseLog(id, user);
        return ResponseEntity.noContent().build();
    }
}
