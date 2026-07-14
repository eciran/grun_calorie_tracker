package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AdminAiRequestReviewDto;
import com.grun.calorietracker.dto.AdminAiMonitoringSummaryDto;
import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.service.AdminAiMealDraftService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/ai/requests")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin AI Operations", description = "Admin-only AI request monitoring across controlled AI features.")
public class AdminAiRequestController {

    private final AdminAiMealDraftService adminAiMealDraftService;

    @GetMapping("/summary")
    @Operation(
            summary = "Get aggregate AI operational metrics",
            description = "Returns privacy-safe request, status, token, cost, quota, provider, model, and prompt-version metrics for the selected time window."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "AI monitoring summary returned.",
                    content = @Content(schema = @Schema(implementation = AdminAiMonitoringSummaryDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<AdminAiMonitoringSummaryDto> getSummary(
            @Parameter(description = "Monitoring window in hours. Clamped between 1 and 744.", example = "24")
            @RequestParam(defaultValue = "24") int windowHours) {
        return ResponseEntity.ok(adminAiMealDraftService.getMonitoringSummary(windowHours));
    }
    @GetMapping
    @Operation(
            summary = "List AI requests for admin operations",
            description = "Returns AI request history across meal drafts, recipe generation, workout plans, and coaching insights. Use filters to isolate request type, status, or refundable rejected requests."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "AI requests returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<Page<AdminAiRequestReviewDto>> listRequests(
            @Parameter(description = "Optional AI request type filter.", example = "AI_DAILY_INSIGHT")
            @RequestParam(required = false) AiRequestType requestType,
            @Parameter(description = "Optional request status filter.", example = "REJECTED")
            @RequestParam(required = false) AiRequestStatus status,
            @Parameter(description = "When true, returns only rejected requests with remaining refundable quota.", example = "true")
            @RequestParam(defaultValue = "false") boolean refundableOnly,
            @Parameter(description = "Page number.", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size.", example = "25")
            @RequestParam(defaultValue = "25") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return ResponseEntity.ok(adminAiMealDraftService.listRequests(requestType, status, refundableOnly, PageRequest.of(safePage, safeSize)));
    }
}