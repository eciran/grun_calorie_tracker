package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.dto.FoodLogDailyStatsDto;
import com.grun.calorietracker.dto.FoodDiaryNoteDto;
import com.grun.calorietracker.dto.FoodDiaryNoteRequestDto;
import com.grun.calorietracker.dto.FoodLogCopyMealRequestDto;
import com.grun.calorietracker.dto.FoodLogMealSummaryDto;
import com.grun.calorietracker.dto.FoodLogRecentMealDto;
import com.grun.calorietracker.dto.FoodLogsDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.service.FoodDiaryNoteService;
import com.grun.calorietracker.service.FoodLogsService;
import com.grun.calorietracker.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
@RequestMapping("/api/v1/food-logs")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Food Logs", description = "Authenticated meal logging and daily nutrition statistics.")
public class FoodLogsController {

    private final FoodLogsService foodLogsService;
    private final FoodDiaryNoteService foodDiaryNoteService;
    private final UserService userService;

    @PostMapping
    @Operation(
            summary = "Create a food log",
            description = "Adds a food entry to the authenticated user's diary. For local demo data, login as demo.user@grun.local and use the seeded GRun Demo products returned from product search."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Food log created."),
            @ApiResponse(responseCode = "400", description = "Request validation failed.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<FoodLogsDto> addFoodLog(@Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
                                                  @RequestBody @Valid FoodLogsDto dto) {
        FoodLogsDto created = foodLogsService.addFoodLog(dto, userDetails.getUsername());
        return ResponseEntity.ok(created);
    }

    @PostMapping("/copy-meal")
    @Operation(
            summary = "Copy a meal to another day",
            description = "Copies all owned food logs in one source meal to a target day while keeping each log's portion and time of day."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Meal food logs copied."),
            @ApiResponse(responseCode = "400", description = "Request validation failed.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<List<FoodLogsDto>> copyMeal(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid FoodLogCopyMealRequestDto request) {
        return ResponseEntity.ok(foodLogsService.copyMeal(userDetails.getUsername(), request));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update a food log",
            description = "Replaces the owned food log product, portion, meal category, and log date. Use this when a diary entry was logged with the wrong product, quantity, or day."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Food log updated."),
            @ApiResponse(responseCode = "400", description = "Request validation failed.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Food log or product was not found.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<FoodLogsDto> updateFoodLog(
            @Parameter(description = "Food log id.", example = "1") @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid FoodLogsDto dto) {
        return ResponseEntity.ok(foodLogsService.updateFoodLog(id, dto, userDetails.getUsername()));
    }

    @GetMapping
    @Operation(
            summary = "List food logs",
            description = "Returns the authenticated user's food logs. Use date for one diary day. Without date, the endpoint returns a paged recent-history slice instead of the full diary history."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Food logs returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<List<FoodLogsDto>> getFoodLogs(
            @Parameter(description = "Optional log date in ISO format. Use today's date to inspect local demo seed logs.", example = "2026-05-16")
            @RequestParam(required = false) String date,
            @Parameter(description = "Recent-history page index when date is omitted.", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Recent-history page size when date is omitted. Maximum 100.", example = "50")
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        List<FoodLogsDto> logs = foodLogsService.getFoodLogs(userDetails.getUsername(), date, page, size);
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/history")
    @Operation(
            summary = "List food log history by date range",
            description = "Returns owned food diary entries between the supplied start and end dates. The end date is inclusive for client requests."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Food log history returned."),
            @ApiResponse(responseCode = "400", description = "Date format or date range is invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<List<FoodLogsDto>> getFoodLogHistory(
            @Parameter(description = "History start date in ISO format.", example = "2026-05-01") @RequestParam String start,
            @Parameter(description = "History end date in ISO format.", example = "2026-05-07") @RequestParam String end,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        LocalDate startDate = LocalDate.parse(start);
        LocalDate endDate = LocalDate.parse(end);
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("History end date must not be before start date.");
        }
        return ResponseEntity.ok(foodLogsService.getFoodLogsHistory(
                userDetails.getUsername(),
                startDate.atStartOfDay(),
                endDate.plusDays(1).atStartOfDay()
        ));
    }

    @GetMapping("/meals")
    @Operation(
            summary = "Get daily food logs grouped by meal",
            description = "Returns BREAKFAST, LUNCH, DINNER, and SNACK groups for one day with food logs and nutrition totals."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Meal summaries returned."),
            @ApiResponse(responseCode = "400", description = "Date format is invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<List<FoodLogMealSummaryDto>> getMealSummaries(
            @Parameter(description = "Diary date in ISO format.", example = "2026-05-21")
            @RequestParam String date,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        LocalDate targetDate = LocalDate.parse(date);
        return ResponseEntity.ok(foodLogsService.getMealSummaries(
                userDetails.getUsername(),
                targetDate.atStartOfDay(),
                targetDate.plusDays(1).atStartOfDay()
        ));
    }

    @GetMapping("/recent-meals")
    @Operation(
            summary = "List recent logged meals",
            description = "Returns recent meal occurrences from diary history. These are not saved meal templates; clients can copy one of them or explicitly save a template."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recent meals returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<List<FoodLogRecentMealDto>> getRecentMeals(
            @Parameter(description = "Maximum recent meal count. Maximum 30.", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) @Max(30) int limit,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(foodLogsService.getRecentMeals(userDetails.getUsername(), limit));
    }

    @PutMapping("/diary-note")
    @Operation(
            summary = "Create or update a daily food diary note",
            description = "Stores one free-text note for the authenticated user's food diary day. This note belongs to the day, not to a specific meal or progress log."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Food diary note saved."),
            @ApiResponse(responseCode = "400", description = "Request validation failed.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<FoodDiaryNoteDto> upsertDiaryNote(
            @Parameter(description = "Diary date in ISO format.", example = "2026-05-23") @RequestParam String date,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid FoodDiaryNoteRequestDto request) {
        return ResponseEntity.ok(foodDiaryNoteService.upsertNote(
                userDetails.getUsername(),
                LocalDate.parse(date),
                request
        ));
    }

    @GetMapping("/diary-note")
    @Operation(
            summary = "Get a daily food diary note",
            description = "Returns the note for one authenticated food diary day."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Food diary note returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Food diary note was not found.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<FoodDiaryNoteDto> getDiaryNote(
            @Parameter(description = "Diary date in ISO format.", example = "2026-05-23") @RequestParam String date,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(foodDiaryNoteService.getNote(userDetails.getUsername(), LocalDate.parse(date)));
    }

    @DeleteMapping("/diary-note")
    @Operation(
            summary = "Delete a daily food diary note",
            description = "Deletes the note for one authenticated food diary day."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Food diary note deleted.", content = @Content),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Food diary note was not found.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<Void> deleteDiaryNote(
            @Parameter(description = "Diary date in ISO format.", example = "2026-05-23") @RequestParam String date,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        foodDiaryNoteService.deleteNote(userDetails.getUsername(), LocalDate.parse(date));
        return ResponseEntity.noContent().build();
    }

    // Get specific food log by id for user
    @GetMapping("/{id}")
    @Operation(
            summary = "Get a food log by id",
            description = "Returns a single food log owned by the authenticated user."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Food log returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Food log was not found for the current user.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
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
            @ApiResponse(responseCode = "204", description = "Food log deleted.", content = @Content),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Food log was not found for the current user.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
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
            @ApiResponse(responseCode = "400", description = "Date format is invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing, invalid, or user cannot be found.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
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
