package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.dto.AdminAiQuotaGrantRequestDto;
import com.grun.calorietracker.dto.AdminSubscriptionUpdateRequestDto;
import com.grun.calorietracker.dto.SubscriptionDto;
import com.grun.calorietracker.service.SubscriptionService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/subscriptions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Subscriptions", description = "Admin-only subscription state management until payment provider webhooks are connected.")
public class AdminSubscriptionController {

    private final SubscriptionService subscriptionService;

    @PatchMapping("/users/{userId}")
    @Operation(
            summary = "Update a user's subscription",
            description = "Creates or updates a user's subscription state. This is a controlled admin bridge before App Store / Google Play / Stripe webhook integration."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Subscription state updated.",
                    content = @Content(schema = @Schema(implementation = SubscriptionDto.class))),
            @ApiResponse(responseCode = "400", description = "Request validation failed.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "User could not be found.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<SubscriptionDto> updateUserSubscription(
            @Parameter(description = "User id.", example = "1") @PathVariable Long userId,
            @RequestBody @Valid AdminSubscriptionUpdateRequestDto request) {
        return ResponseEntity.ok(subscriptionService.updateUserSubscription(userId, request));
    }

    @PostMapping("/users/{userId}/ai-quota/reset")
    @Operation(
            summary = "Reset a user's AI quota",
            description = "Resets the user's current AI usage to zero without changing the plan. This is an admin recovery tool until provider webhooks and scheduled quota jobs are connected."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "AI quota reset.",
                    content = @Content(schema = @Schema(implementation = SubscriptionDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "User could not be found.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<SubscriptionDto> resetUserAiQuota(
            @Parameter(description = "User id.", example = "1") @PathVariable Long userId) {
        return ResponseEntity.ok(subscriptionService.resetUserAiQuota(userId));
    }

    @PostMapping("/users/{userId}/ai-quota/addon")
    @Operation(
            summary = "Grant add-on AI quota",
            description = "Adds extra AI quota to the user's current quota period without changing the base subscription plan. This is the admin/payment bridge for future one-off add-on purchases."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Add-on AI quota granted.",
                    content = @Content(schema = @Schema(implementation = SubscriptionDto.class))),
            @ApiResponse(responseCode = "400", description = "Request validation failed.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "User could not be found.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<SubscriptionDto> grantAiAddonQuota(
            @Parameter(description = "User id.", example = "1") @PathVariable Long userId,
            @RequestBody @Valid AdminAiQuotaGrantRequestDto request) {
        return ResponseEntity.ok(subscriptionService.grantAiAddonQuota(userId, request.getAmount(), request.getValidityDays()));
    }
}
