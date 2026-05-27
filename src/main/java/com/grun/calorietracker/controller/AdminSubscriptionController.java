package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.dto.AdminAiQuotaGrantRequestDto;
import com.grun.calorietracker.dto.AdminSubscriptionPlanFeatureUpdateRequestDto;
import com.grun.calorietracker.dto.AdminSubscriptionUpdateRequestDto;
import com.grun.calorietracker.dto.SubscriptionDto;
import com.grun.calorietracker.dto.SubscriptionPlanFeatureDto;
import com.grun.calorietracker.enums.AdminAuditActionType;
import com.grun.calorietracker.enums.AdminAuditTargetType;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.enums.SubscriptionPlan;
import com.grun.calorietracker.security.CorrelationIdFilter;
import com.grun.calorietracker.service.AdminAuditService;
import com.grun.calorietracker.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/subscriptions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Subscriptions", description = "Admin-only subscription state management until payment provider webhooks are connected.")
public class AdminSubscriptionController {

    private final SubscriptionService subscriptionService;
    private final AdminAuditService adminAuditService;

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
            @RequestBody @Valid AdminSubscriptionUpdateRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {
        SubscriptionDto before = subscriptionService.getUserSubscriptionForAdmin(userId);
        SubscriptionDto response = subscriptionService.updateUserSubscription(userId, request);
        adminAuditService.record(
                adminEmail(userDetails),
                AdminAuditActionType.SUBSCRIPTION_UPDATE,
                AdminAuditTargetType.USER_SUBSCRIPTION,
                userId.toString(),
                before,
                response,
                correlationId(httpRequest)
        );
        return ResponseEntity.ok(response);
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
            @Parameter(description = "User id.", example = "1") @PathVariable Long userId,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {
        SubscriptionDto before = subscriptionService.getUserSubscriptionForAdmin(userId);
        SubscriptionDto response = subscriptionService.resetUserAiQuota(userId);
        adminAuditService.record(
                adminEmail(userDetails),
                AdminAuditActionType.AI_QUOTA_RESET,
                AdminAuditTargetType.USER_SUBSCRIPTION,
                userId.toString(),
                before,
                response,
                correlationId(httpRequest)
        );
        return ResponseEntity.ok(response);
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
            @RequestBody @Valid AdminAiQuotaGrantRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {
        SubscriptionDto before = subscriptionService.getUserSubscriptionForAdmin(userId);
        SubscriptionDto response = subscriptionService.grantAiAddonQuota(userId, request.getAmount(), request.getValidityDays());
        adminAuditService.record(
                adminEmail(userDetails),
                AdminAuditActionType.AI_QUOTA_ADDON_GRANT,
                AdminAuditTargetType.USER_SUBSCRIPTION,
                userId.toString(),
                before,
                response,
                correlationId(httpRequest)
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/features")
    @Operation(
            summary = "List plan feature matrix",
            description = "Returns the admin-managed feature matrix used for new purchases and renewals. Existing user entitlement snapshots remain valid until their subscription period ends."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Plan feature matrix returned.",
                    content = @Content(schema = @Schema(implementation = SubscriptionPlanFeatureDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<List<SubscriptionPlanFeatureDto>> listPlanFeatures() {
        return ResponseEntity.ok(subscriptionService.listPlanFeatures());
    }

    @PutMapping("/features/{planType}/{feature}")
    @Operation(
            summary = "Update a plan feature",
            description = "Changes whether a feature is included in a plan for future purchases and renewals. This does not remove already-granted user entitlements from the current billing period."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Plan feature updated.",
                    content = @Content(schema = @Schema(implementation = SubscriptionPlanFeatureDto.class))),
            @ApiResponse(responseCode = "400", description = "Request validation failed.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<SubscriptionPlanFeatureDto> updatePlanFeature(
            @Parameter(description = "Subscription plan.", example = "PLUS") @PathVariable SubscriptionPlan planType,
            @Parameter(description = "Feature key.", example = "HEALTH_INTEGRATION") @PathVariable SubscriptionFeature feature,
            @RequestBody @Valid AdminSubscriptionPlanFeatureUpdateRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {
        SubscriptionPlanFeatureDto before = subscriptionService.listPlanFeatures().stream()
                .filter(item -> item.getPlanType() == planType && item.getFeature() == feature)
                .findFirst()
                .orElse(null);
        SubscriptionPlanFeatureDto response = subscriptionService.updatePlanFeature(
                planType,
                feature,
                request.getEnabled(),
                request.getEffectiveFrom()
        );
        adminAuditService.record(
                adminEmail(userDetails),
                AdminAuditActionType.SUBSCRIPTION_FEATURE_UPDATE,
                AdminAuditTargetType.SUBSCRIPTION_FEATURE,
                planType.name() + ":" + feature.name(),
                before,
                response,
                correlationId(httpRequest)
        );
        return ResponseEntity.ok(response);
    }

    private String adminEmail(UserDetails userDetails) {
        return userDetails == null ? "unknown-admin" : userDetails.getUsername();
    }

    private String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE);
        return value == null ? request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER) : value.toString();
    }
}
