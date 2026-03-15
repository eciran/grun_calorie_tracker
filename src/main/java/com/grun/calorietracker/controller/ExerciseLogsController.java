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

import java.time.LocalDate;
import java.time.LocalDateTime;
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


    @GetMapping("/report")
    public ResponseEntity<List<ExerciseLogsDto>> getExerciseLogsByDateAndUser(@AuthenticationPrincipal UserDetails userDetails,
                                                                    @RequestParam String startDate,
                                                                    @RequestParam String endDate,
                                                                    @RequestParam(defaultValue = "day") String range) {
        LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
        LocalDateTime end = LocalDate.parse(endDate).atTime(23, 59, 59);

        List<ExerciseLogsDto> stats = exerciseLogsService.getExerciseLogsByDateAndUser(userDetails.getUsername(), start, end, range);
        return ResponseEntity.ok(stats);
    }

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
