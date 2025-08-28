package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.FoodLogDailyStatsDto;
import com.grun.calorietracker.dto.FoodLogsDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.service.FoodLogsService;
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

// Controller for managing food log operations
@RestController
@RequestMapping("/api/food-logs")
@RequiredArgsConstructor
public class FoodLogsController {

    private final FoodLogsService foodLogsService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<FoodLogsDto> addFoodLog(@AuthenticationPrincipal UserDetails userDetails,
                                                  @RequestBody @Valid FoodLogsDto dto) {
        FoodLogsDto created = foodLogsService.addFoodLog(dto, userDetails.getUsername());
        return ResponseEntity.ok(created);
    }

    @GetMapping
    public ResponseEntity<List<FoodLogsDto>> getFoodLogs(
            @RequestParam(required = false) String date,
            @AuthenticationPrincipal UserDetails userDetails) {
        List<FoodLogsDto> logs = foodLogsService.getFoodLogs(userDetails.getUsername(),date);
        return ResponseEntity.ok(logs);
    }

    // Get specific food log by id for user
    @GetMapping("/{id}")
    public ResponseEntity<FoodLogsDto> getFoodLogById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        FoodLogsDto log = foodLogsService.getFoodLogById(id, userDetails.getUsername());
        return ResponseEntity.ok(log);
    }

    // Delete food log by id for user
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFoodLog(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        foodLogsService.deleteFoodLog(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stats")
    public ResponseEntity<List<FoodLogDailyStatsDto>> getDailyStats(
            @RequestParam String start,
            @RequestParam String end,
            @AuthenticationPrincipal UserDetails userDetails) {

        UserEntity user = userService.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));

        LocalDateTime startDate = LocalDate.parse(start).atStartOfDay();
        LocalDateTime endDate = LocalDate.parse(end).plusDays(1).atStartOfDay().minusSeconds(1);

        List<FoodLogDailyStatsDto> stats = foodLogsService.getDailyStats(user.getEmail(), startDate, endDate);
        return ResponseEntity.ok(stats);
    }

}
