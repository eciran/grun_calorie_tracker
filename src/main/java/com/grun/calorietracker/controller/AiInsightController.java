package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AiInsightRequestDto;
import com.grun.calorietracker.dto.AiInsightResponseDto;
import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.service.AiInsightService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/insights")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "AI Insights", description = "Controlled app-scoped AI coaching insights for daily and weekly review screens.")
public class AiInsightController {

    private final AiInsightService aiInsightService;

    @PostMapping("/daily")
    @Operation(summary = "Generate daily AI insight", description = "Creates an app-scoped coaching insight from backend daily summary data.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Daily insight created.", content = @Content(schema = @Schema(implementation = AiInsightResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "AI provider disabled, quota unavailable, or request invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<AiInsightResponseDto> daily(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody(required = false) @Valid AiInsightRequestDto request) {
        return ResponseEntity.ok(aiInsightService.createDailyInsight(
                userDetails.getUsername(), idempotencyKey, request));
    }

    @PostMapping("/weekly")
    @Operation(summary = "Generate weekly AI insight", description = "Creates an app-scoped coaching insight from backend daily summaries over a bounded range.")
    public ResponseEntity<AiInsightResponseDto> weekly(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody(required = false) @Valid AiInsightRequestDto request) {
        return ResponseEntity.ok(aiInsightService.createWeeklyInsight(
                userDetails.getUsername(), idempotencyKey, request));
    }
}
