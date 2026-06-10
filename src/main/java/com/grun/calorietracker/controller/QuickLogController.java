package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.dto.QuickLogSuggestionDto;
import com.grun.calorietracker.service.QuickLogSuggestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;

@RestController
@RequestMapping("/api/v1/quick-log")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Quick Log", description = "Aggregated shortcuts for fast food logging.")
public class QuickLogController {

    private final QuickLogSuggestionService quickLogSuggestionService;

    @GetMapping("/suggestions")
    @Operation(
            summary = "Get quick-log suggestions",
            description = "Returns recent meals, saved templates, recent products, and favorites in one response so the first log screen can show predictions instead of an empty search box."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Quick-log suggestions returned."),
            @ApiResponse(responseCode = "400", description = "Request validation failed.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<QuickLogSuggestionDto> getSuggestions(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Target diary date. Defaults to today.", example = "2026-06-10")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(description = "Client local time used to suggest meal type. Defaults to server time.", example = "08:30:00")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime localTime,
            @Parameter(description = "Maximum rows per suggestion group. Maximum 20.", example = "10")
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(quickLogSuggestionService.getSuggestions(userDetails.getUsername(), date, localTime, limit));
    }
}
