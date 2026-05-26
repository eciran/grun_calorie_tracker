package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.dto.RevenueCatWebhookResponseDto;
import com.grun.calorietracker.dto.SubscriptionProviderEventDetailDto;
import com.grun.calorietracker.dto.SubscriptionProviderEventPageDto;
import com.grun.calorietracker.enums.SubscriptionProviderEventStatus;
import com.grun.calorietracker.service.SubscriptionProviderEventAdminService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/subscription-events")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Subscription Events", description = "Admin-only monitoring and recovery endpoints for RevenueCat and payment provider events.")
public class AdminSubscriptionProviderEventController {

    private final SubscriptionProviderEventAdminService eventAdminService;

    @GetMapping
    @Operation(summary = "List subscription provider events", description = "Lists RevenueCat/payment provider events with optional status, event type, product id, and user id filters.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Provider events returned.",
                    content = @Content(schema = @Schema(implementation = SubscriptionProviderEventPageDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<SubscriptionProviderEventPageDto> getEvents(
            @RequestParam(required = false) SubscriptionProviderEventStatus status,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String productId,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(eventAdminService.getEvents(status, eventType, productId, userId, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get subscription provider event detail", description = "Returns a single provider event including raw webhook payload for audit/debugging.")
    public ResponseEntity<SubscriptionProviderEventDetailDto> getEvent(
            @Parameter(description = "Provider event database id.", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(eventAdminService.getEvent(id));
    }

    @GetMapping("/users/{userId}/history")
    @Operation(summary = "Get user's subscription event history", description = "Returns provider-backed subscription history for a user ordered by newest event first.")
    public ResponseEntity<SubscriptionProviderEventPageDto> getUserHistory(
            @Parameter(description = "User id.", example = "1") @PathVariable Long userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(eventAdminService.getUserHistory(userId, page, size));
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "Retry failed subscription provider event", description = "Reprocesses a failed RevenueCat webhook event from its stored raw payload. Processed or ignored events are not replayed.")
    public ResponseEntity<RevenueCatWebhookResponseDto> retryEvent(
            @Parameter(description = "Provider event database id.", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(eventAdminService.retryEvent(id));
    }
}
