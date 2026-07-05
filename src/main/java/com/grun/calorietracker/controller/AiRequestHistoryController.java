package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AiRequestHistoryDetailDto;
import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.service.AiRequestHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/ai/requests")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "AI Request History", description = "Authenticated user history for controlled AI requests and generated outputs.")
public class AiRequestHistoryController {

    private final AiRequestHistoryService aiRequestHistoryService;

    @GetMapping("/history")
    @Operation(
            summary = "List my AI request history",
            description = "Returns authenticated user's AI request history, including stored output payloads for successful requests. Raw provider error details are not exposed."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "AI request history returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<List<AiRequestHistoryDetailDto>> listHistory(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Optional AI request type filter.", example = "AI_DAILY_INSIGHT")
            @RequestParam(required = false) AiRequestType requestType,
            @Parameter(description = "Optional AI request status filter.", example = "DRAFT_CREATED")
            @RequestParam(required = false) AiRequestStatus status,
            @Parameter(description = "Maximum history size.", example = "20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return ResponseEntity.ok(aiRequestHistoryService.listHistory(userDetails.getUsername(), requestType, status, limit));
    }

    @GetMapping("/history/{requestId}")
    @Operation(
            summary = "Get my AI request history detail",
            description = "Returns one authenticated user-owned AI request history item with stored output payload when available."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "AI request history item returned."),
            @ApiResponse(responseCode = "400", description = "AI request history item was not found.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<AiRequestHistoryDetailDto> getHistoryItem(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "AI request id.", example = "10") @PathVariable Long requestId) {
        return ResponseEntity.ok(aiRequestHistoryService.getHistoryItem(userDetails.getUsername(), requestId));
    }
}
