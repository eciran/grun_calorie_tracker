package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.FoodLogDailyStatsDto;
import com.grun.calorietracker.dto.FoodLogsDto;
import com.grun.calorietracker.service.FoodLogsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequestMapping("/api/food-logs")
@RequiredArgsConstructor
@Tag(name = "Food Logs", description = "Food log management endpoints")
public class FoodLogsController {

    private final FoodLogsService foodLogsService;

    @PostMapping
    @Operation(summary = "Add a new food log")
    public ResponseEntity<FoodLogsDto> addFoodLog(
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid FoodLogsDto dto) {
        FoodLogsDto created = foodLogsService.addFoodLog(dto, userDetails.getUsername());
        return ResponseEntity.ok(created);
    }

    @GetMapping
    @Operation(summary = "Get food logs by date")
    public ResponseEntity<List<FoodLogsDto>> getFoodLogs(@RequestParam(required = false) String date,
                                                         @Parameter(hidden = true)
                                                         @AuthenticationPrincipal UserDetails userDetails) {
        List<FoodLogsDto> logs = foodLogsService.getFoodLogs(userDetails.getUsername(), date);
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FoodLogsDto> getFoodLogById(@PathVariable Long id,
                                                      @Parameter(hidden = true)
                                                      @AuthenticationPrincipal UserDetails userDetails) {
        FoodLogsDto log = foodLogsService.getFoodLogById(id, userDetails.getUsername());
        return ResponseEntity.ok(log);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFoodLog(@PathVariable Long id,
                                              @Parameter(hidden = true)
                                              @AuthenticationPrincipal UserDetails userDetails) {
        foodLogsService.deleteFoodLog(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stats")
    public ResponseEntity<List<FoodLogDailyStatsDto>> getDailyStats(@RequestParam String start,
                                                                    @RequestParam String end,
                                                                    @Parameter(hidden = true)
                                                                    @AuthenticationPrincipal UserDetails userDetails) {
        LocalDateTime startDate = LocalDate.parse(start).atStartOfDay();
        LocalDateTime endDate = LocalDate.parse(end).plusDays(1).atStartOfDay().minusSeconds(1);

        List<FoodLogDailyStatsDto> stats = foodLogsService.getDailyStats(
                userDetails.getUsername(),
                startDate,
                endDate
        );

        return ResponseEntity.ok(stats);
    }
}