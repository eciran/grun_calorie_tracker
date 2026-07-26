package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.dto.OnboardingAnalyticsEventRequestDto;
import com.grun.calorietracker.dto.OnboardingCompleteRequestDto;
import com.grun.calorietracker.dto.OnboardingCompleteResponseDto;
import com.grun.calorietracker.dto.OnboardingPreviewResponseDto;
import com.grun.calorietracker.dto.OnboardingStateDto;
import com.grun.calorietracker.dto.OnboardingStepUpdateRequestDto;
import com.grun.calorietracker.enums.OnboardingStatus;
import com.grun.calorietracker.enums.OnboardingStep;
import com.grun.calorietracker.enums.ProductAnalyticsEventType;
import com.grun.calorietracker.service.OnboardingAnalyticsService;
import com.grun.calorietracker.service.OnboardingService;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/api/v1/onboarding")
@RequiredArgsConstructor
@Slf4j
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Onboarding", description = "Resumable mobile onboarding flow for profile, goal, nutrition, and fitness preferences.")
public class OnboardingController {

    private final OnboardingService onboardingService;
    private final OnboardingAnalyticsService onboardingAnalyticsService;

    @GetMapping("/state")
    @Operation(summary = "Get onboarding state", description = "Returns the saved draft, completed steps, and the next onboarding step.")
    public ResponseEntity<OnboardingStateDto> getState(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        OnboardingStateDto state = onboardingService.getState(userDetails.getUsername());
        if (state.getStatus() == OnboardingStatus.IN_PROGRESS) {
            ProductAnalyticsEventType eventType = state.getCompletedSteps().isEmpty()
                    ? ProductAnalyticsEventType.ONBOARDING_STARTED
                    : ProductAnalyticsEventType.ONBOARDING_RESUMED;
            recordSafely(userDetails.getUsername(), eventType, state.getCurrentStep());
        }
        return ResponseEntity.ok(state);
    }

    @PatchMapping("/steps/{step}")
    @Operation(
            summary = "Save an onboarding step",
            description = "Persists a partial or complete PROFILE, PREFERENCES, GOAL, NUTRITION, or FITNESS_PREFERENCE draft without changing the live profile or active goal."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Step saved."),
            @ApiResponse(responseCode = "400", description = "Step or payload is invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<OnboardingStateDto> updateStep(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String step,
            @RequestBody @Valid OnboardingStepUpdateRequestDto request
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        OnboardingStep parsedStep = OnboardingStep.valueOf(step.trim().toUpperCase(Locale.ROOT));
        try {
            OnboardingStateDto state = onboardingService.updateStep(parsedStep, request, userDetails.getUsername());
            if (state.getCompletedSteps().contains(parsedStep)) {
                recordSafely(userDetails.getUsername(), ProductAnalyticsEventType.ONBOARDING_STEP_COMPLETED, parsedStep);
            }
            return ResponseEntity.ok(state);
        } catch (RuntimeException ex) {
            recordSafely(userDetails.getUsername(), ProductAnalyticsEventType.ONBOARDING_STEP_FAILED, parsedStep);
            throw ex;
        }
    }

    @PostMapping("/preview")
    @Operation(
            summary = "Preview onboarding targets",
            description = "Calculates calorie and macro targets from the saved draft without modifying the live profile or goal."
    )
    public ResponseEntity<OnboardingPreviewResponseDto> preview(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            OnboardingPreviewResponseDto response = onboardingService.preview(userDetails.getUsername());
            recordSafely(userDetails.getUsername(), ProductAnalyticsEventType.ONBOARDING_PREVIEWED, OnboardingStep.REVIEW);
            return ResponseEntity.ok(response);
        } catch (RuntimeException ex) {
            recordSafely(userDetails.getUsername(), ProductAnalyticsEventType.ONBOARDING_STEP_FAILED, OnboardingStep.REVIEW);
            throw ex;
        }
    }

    @PostMapping("/complete")
    @Operation(
            summary = "Complete onboarding",
            description = "Completes the saved draft atomically. The legacy full request body remains optional for backward compatibility."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Onboarding completed."),
            @ApiResponse(responseCode = "400", description = "Draft is incomplete or request validation failed.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing, invalid, or user cannot be found.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<OnboardingCompleteResponseDto> completeOnboarding(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody(required = false) @Valid OnboardingCompleteRequestDto request
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            OnboardingCompleteResponseDto response = request == null
                    ? onboardingService.completeOnboarding(userDetails.getUsername())
                    : onboardingService.completeOnboarding(request, userDetails.getUsername());
            recordSafely(userDetails.getUsername(), ProductAnalyticsEventType.ONBOARDING_COMPLETED, OnboardingStep.COMPLETE);
            return ResponseEntity.ok(response);
        } catch (RuntimeException ex) {
            recordSafely(userDetails.getUsername(), ProductAnalyticsEventType.ONBOARDING_STEP_FAILED, OnboardingStep.REVIEW);
            throw ex;
        }
    }

    @PostMapping("/events")
    @Operation(
            summary = "Record a client onboarding event",
            description = "Records only step-viewed or abandoned events. Profile, health, diet, and allergen values are not accepted."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Onboarding event accepted."),
            @ApiResponse(responseCode = "400", description = "Event validation failed.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<Void> recordClientEvent(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid OnboardingAnalyticsEventRequestDto request
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        onboardingAnalyticsService.recordClientEvent(userDetails.getUsername(), request);
        return ResponseEntity.accepted().build();
    }

    private void recordSafely(String email, ProductAnalyticsEventType eventType, OnboardingStep step) {
        try {
            onboardingAnalyticsService.recordServerEvent(email, eventType, step);
        } catch (RuntimeException ex) {
            log.warn("Onboarding analytics recording failed eventType={} step={}", eventType, step, ex);
        }
    }
}
