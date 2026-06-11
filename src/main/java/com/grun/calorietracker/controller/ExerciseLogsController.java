package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.dto.ExerciseLogsDto;
import com.grun.calorietracker.service.ExerciseLogsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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

@RestController
@RequestMapping("/api/v1/exercise-logs")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Exercise Logs", description = "Authenticated exercise logging and date range reporting.")
public class ExerciseLogsController {

    private static final long MAX_EXERCISE_HISTORY_RANGE_DAYS = 366;

    private final ExerciseLogsService exerciseLogsService;

    @PostMapping
    @Operation(
            summary = "Create an exercise log",
            description = "Adds an exercise entry to the authenticated user's diary. Local demo seed creates one running exercise log for demo.user@grun.local."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Exercise log created."),
            @ApiResponse(responseCode = "400", description = "Request validation failed.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<ExerciseLogsDto> addExerciseLog(@Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
                                                          @RequestBody @Valid ExerciseLogsDto dto) {
        ExerciseLogsDto created = exerciseLogsService.addExerciseLog(dto, userDetails.getUsername());
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update an exercise log",
            description = "Updates an owned manual exercise diary entry when the exercise, duration, calories, or logged time was entered incorrectly."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Exercise log updated."),
            @ApiResponse(responseCode = "400", description = "Request validation failed.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Exercise log or exercise item was not found.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<ExerciseLogsDto> updateExerciseLog(
            @Parameter(description = "Exercise log id.", example = "1") @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid ExerciseLogsDto dto) {
        return ResponseEntity.ok(exerciseLogsService.updateExerciseLog(id, dto, userDetails.getUsername()));
    }

    @PostMapping("/external")
    @Operation(
            summary = "Create an external exercise log",
            description = "Adds an exercise log imported from an external source such as Apple Health or Google Fit. The combination of user, source, and externalId must be unique. Local demo seed uses source LOCAL_DEMO and an external id based on today's date."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "External exercise log created."),
            @ApiResponse(responseCode = "400", description = "Request validation failed or source/externalId is missing.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "409", description = "An exercise log with the same source and externalId already exists for the user.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<ExerciseLogsDto> addExternalExerciseLog(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid ExerciseLogsDto dto) {
        ExerciseLogsDto created = exerciseLogsService.addExerciseLogFromExternal(dto, userDetails.getUsername());
        return ResponseEntity.ok(created);
    }


    @GetMapping("/report")
    @Operation(
            summary = "Get exercise logs report",
            description = "Returns exercise logs for the authenticated user over a date range. The range parameter controls the reporting bucket. Use today's date with demo.user@grun.local to inspect the seeded local demo exercise log."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Exercise report returned."),
            @ApiResponse(responseCode = "400", description = "Date format or range value is invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<List<ExerciseLogsDto>> getExerciseLogsByDateAndUser(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Report start date in ISO format.", example = "2026-05-16") @RequestParam String startDate,
            @Parameter(description = "Report end date in ISO format.", example = "2026-05-16") @RequestParam String endDate,
            @Parameter(description = "Report bucket. Supported value currently used by the service defaults to day.", example = "day")
            @RequestParam(defaultValue = "day") String range) {
        LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
        LocalDateTime end = LocalDate.parse(endDate).atTime(23, 59, 59);
        validateDateRange(start.toLocalDate(), end.toLocalDate());

        List<ExerciseLogsDto> stats = exerciseLogsService.getExerciseLogsByDateAndUser(userDetails.getUsername(), start, end, range);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/history")
    @Operation(
            summary = "List exercise log history by date range",
            description = "Returns actual owned exercise diary entries between the supplied start and end dates. Use report for aggregated totals."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Exercise log history returned."),
            @ApiResponse(responseCode = "400", description = "Date format or date range is invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<List<ExerciseLogsDto>> getExerciseLogHistory(
            @Parameter(description = "History start date in ISO format.", example = "2026-05-01") @RequestParam String start,
            @Parameter(description = "History end date in ISO format.", example = "2026-05-07") @RequestParam String end,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        LocalDate startDate = LocalDate.parse(start);
        LocalDate endDate = LocalDate.parse(end);
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("History end date must not be before start date.");
        }
        validateDateRange(startDate, endDate);
        return ResponseEntity.ok(exerciseLogsService.getExerciseLogsHistory(
                userDetails.getUsername(),
                startDate.atStartOfDay(),
                endDate.plusDays(1).atStartOfDay()
        ));
    }

    @GetMapping("/source/{source}")
    @Operation(
            summary = "List exercise logs by source",
            description = "Returns exercise logs for the authenticated user filtered by source, for example MANUAL, GOOGLE_FIT, APPLE_HEALTH, or LOCAL_DEMO."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Exercise logs returned."),
            @ApiResponse(responseCode = "400", description = "Source is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<List<ExerciseLogsDto>> getExerciseLogsBySource(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Exercise log source.", example = "LOCAL_DEMO") @PathVariable String source,
            @Parameter(description = "Zero-based page number.", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size. Maximum 100.", example = "50") @RequestParam(defaultValue = "50") int size) {
        List<ExerciseLogsDto> logs = exerciseLogsService.getExerciseLogsBySource(userDetails.getUsername(), source, page, size);
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get an exercise log by id",
            description = "Returns a single exercise log owned by the authenticated user."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Exercise log returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Exercise log was not found for the current user.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<ExerciseLogsDto> getExerciseLogById(
            @Parameter(description = "Exercise log id.", example = "1") @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        ExerciseLogsDto log = exerciseLogsService.getExerciseLogById(id, userDetails.getUsername());
        return ResponseEntity.ok(log);
    }

    // Delete exercise log by id for user
    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete an exercise log",
            description = "Deletes an exercise log owned by the authenticated user."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Exercise log deleted.", content = @Content),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Exercise log was not found for the current user.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<Void> deleteExerciseLog(
            @Parameter(description = "Exercise log id.", example = "1") @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        exerciseLogsService.deleteExerciseLog(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) > MAX_EXERCISE_HISTORY_RANGE_DAYS) {
            throw new IllegalArgumentException("Exercise date range must not exceed 366 days.");
        }
    }
}
