package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.dto.ProgressLogDto;
import com.grun.calorietracker.service.ProgressLogService;
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
@RequestMapping("/api/v1/progress")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Progress", description = "Authenticated progress logs for body measurements and tracking history.")
public class ProgressLogController {

    private final ProgressLogService progressLogService;

    @PostMapping
    @Operation(
            summary = "Create a progress log",
            description = "Stores a body progress entry for the authenticated user. The log timestamp is assigned from the current server time."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Progress log created."),
            @ApiResponse(responseCode = "400", description = "Request validation failed.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<ProgressLogDto> createProgressLog(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid ProgressLogDto logDto) {
        logDto.setLogDate(LocalDateTime.now());
        return ResponseEntity.ok(progressLogService.saveLog(logDto, userDetails.getUsername()));
    }

    @GetMapping
    @Operation(
            summary = "List progress logs",
            description = "Returns the authenticated user's progress logs. Supply both start and end to filter an inclusive date range."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Progress logs returned."),
            @ApiResponse(responseCode = "400", description = "Date format or date range is invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<List<ProgressLogDto>> listProgressLogs(
            @Parameter(description = "Optional history start date in ISO format.", example = "2026-05-01")
            @RequestParam(required = false) String start,
            @Parameter(description = "Optional history end date in ISO format.", example = "2026-05-22")
            @RequestParam(required = false) String end,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        if (start == null && end == null) {
            return ResponseEntity.ok(progressLogService.getUserLogs(userDetails.getUsername()));
        }
        if (start == null || end == null) {
            throw new IllegalArgumentException("Progress log date filtering requires both start and end.");
        }

        LocalDate startDate = LocalDate.parse(start);
        LocalDate endDate = LocalDate.parse(end);
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Progress log end date must not be before start date.");
        }
        return ResponseEntity.ok(progressLogService.getUserLogs(
                userDetails.getUsername(),
                startDate.atStartOfDay(),
                endDate.plusDays(1).atStartOfDay()
        ));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get a progress log by id",
            description = "Returns one body progress entry owned by the authenticated user."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Progress log returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Progress log was not found for the current user.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<ProgressLogDto> getProgressLog(
            @Parameter(description = "Progress log id.", example = "1") @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(progressLogService.getLog(id, userDetails.getUsername()));
    }

    @PutMapping("/{id}")
    @Operation(
            summary = "Update a progress log",
            description = "Updates owned body progress values and note while keeping the original server-side log timestamp."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Progress log updated."),
            @ApiResponse(responseCode = "400", description = "Request validation failed.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Progress log was not found for the current user.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<ProgressLogDto> updateProgressLog(
            @Parameter(description = "Progress log id.", example = "1") @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid ProgressLogDto logDto) {
        return ResponseEntity.ok(progressLogService.updateLog(id, logDto, userDetails.getUsername()));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete a progress log",
            description = "Deletes one body progress entry owned by the authenticated user."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Progress log deleted.", content = @Content),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "Progress log was not found for the current user.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<Void> deleteProgressLog(
            @Parameter(description = "Progress log id.", example = "1") @PathVariable Long id,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        progressLogService.deleteLog(id, userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }
}
