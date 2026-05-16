package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.FoodLogDailyStatsDto;
import com.grun.calorietracker.dto.FoodLogsDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.service.FoodLogsService;
import com.grun.calorietracker.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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

// Controller for managing food log operations
@RestController
@RequestMapping("/api/food-logs")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Food Logs", description = "Authenticated meal logging and daily nutrition statistics.")
public class FoodLogsController {

    private final FoodLogsService foodLogsService;
    private final UserService userService;

    @PostMapping
    @Operation(
            summary = "Create a food log",
            description = "Adds a food entry to the authenticated user's diary. For local demo data, login as demo.user@grun.local and use the seeded GRun Demo products returned from product search."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Food log created."),
            @ApiResponse(responseCode = "400", description = "Request validation failed."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.")
    })
    public ResponseEntity<FoodLogsDto> addFoodLog(@Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
                                                  @RequestBody @Valid FoodLogsDto dto) {
        FoodLogsDto created = foodLogsService.addFoodLog(dto, userDetails.getUsername());
        return ResponseEntity.ok(created);
    }

    @GetMapping
    @Operation(
            summary = "List food logs",
            description = "Returns the authenticated user's food logs. A date filter can be supplied for a single day. Local demo seed creates today's BREAKFAST, SNACK, and LUNCH logs for demo.user@grun.local."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Food logs returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.")
    })
    public ResponseEntity<List<FoodLogsDto>> getFoodLogs(
            @Parameter(description = "Optional log date in ISO format. Use today's date to inspect local demo seed logs.", example = "2026-05-16")
            @RequestParam(required = false) String date,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        List<FoodLogsDto> logs = foodLogsService.getFoodLogs(userDetails.getUsername(),date);
        return ResponseEntity.ok(logs);
    }

    // Get specific food log by id for user
    @GetMapping("/{id}")
    @Operation(
            summary = "Get a food log by id",
            description = "Returns a single food log owned by the authenticated user."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Food log returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid."),
            @ApiResponse(responseCode = "404", description = "Food log was not found for the current user.")
    })
    public ResponseEntity<FoodLogsDto> getFoodLogById(
            @Parameter(description = "Food log id.", example = "1") @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        FoodLogsDto log = foodLogsService.getFoodLogById(id, userDetails.getUsername());
        return ResponseEntity.ok(log);
    }

    // Delete food log by id for user
    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete a food log",
            description = "Deletes a food log owned by the authenticated user."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Food log deleted."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid."),
            @ApiResponse(responseCode = "404", description = "Food log was not found for the current user.")
    })
    public ResponseEntity<Void> deleteFoodLog(
            @Parameter(description = "Food log id.", example = "1") @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        foodLogsService.deleteFoodLog(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stats")
    @Operation(
            summary = "Get daily nutrition statistics",
            description = "Aggregates calories, protein, carbohydrate, and fat totals per day for the authenticated user. Local demo seed provides today's food logs so this endpoint can be tested immediately after demo login."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Daily statistics returned."),
            @ApiResponse(responseCode = "400", description = "Date format is invalid."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing, invalid, or user cannot be found.")
    })
    public ResponseEntity<List<FoodLogDailyStatsDto>> getDailyStats(
            @Parameter(description = "Start date in ISO format.", example = "2026-05-16") @RequestParam String start,
            @Parameter(description = "End date in ISO format.", example = "2026-05-16") @RequestParam String end,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

        UserEntity user = userService.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));

        LocalDateTime startDate = LocalDate.parse(start).atStartOfDay();
        LocalDateTime endDate = LocalDate.parse(end).plusDays(1).atStartOfDay().minusSeconds(1);

        List<FoodLogDailyStatsDto> stats = foodLogsService.getDailyStats(user.getEmail(), startDate, endDate);
        return ResponseEntity.ok(stats);
    }
}
