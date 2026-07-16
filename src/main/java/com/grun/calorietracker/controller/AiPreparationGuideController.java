package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AiMealDraftRejectRequestDto;
import com.grun.calorietracker.dto.AiPreparationGuideGenerateRequestDto;
import com.grun.calorietracker.dto.AiPreparationGuideResponseDto;
import com.grun.calorietracker.service.AiPreparationGuideService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/meal-plans/{planId}/items/{itemId}/preparation-guide")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "AI Preparation Guides",
        description = "Optional, separately charged preparation guidance for meal-plan snapshots.")
public class AiPreparationGuideController {
    private final AiPreparationGuideService service;

    @PostMapping("/generate")
    @Operation(summary = "Generate the first preparation guide")
    public ResponseEntity<AiPreparationGuideResponseDto> generate(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails user,
            @PathVariable Long planId, @PathVariable Long itemId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody(required = false) @Valid AiPreparationGuideGenerateRequestDto request) {
        return ResponseEntity.ok(service.generate(
                user.getUsername(), planId, itemId, idempotencyKey, request));
    }

    @GetMapping
    @Operation(summary = "Reopen the latest guide without using AI quota")
    public ResponseEntity<AiPreparationGuideResponseDto> reopen(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails user,
            @PathVariable Long planId, @PathVariable Long itemId) {
        return ResponseEntity.ok(service.reopen(user.getUsername(), planId, itemId));
    }

    @PostMapping("/regenerate")
    @Operation(summary = "Generate a new charged guide version")
    public ResponseEntity<AiPreparationGuideResponseDto> regenerate(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails user,
            @PathVariable Long planId, @PathVariable Long itemId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody(required = false) @Valid AiPreparationGuideGenerateRequestDto request) {
        return ResponseEntity.ok(service.regenerate(
                user.getUsername(), planId, itemId, idempotencyKey, request));
    }

    @PostMapping("/{requestId}/reject")
    @Operation(summary = "Reject a generated guide with optional feedback")
    public ResponseEntity<Void> reject(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails user,
            @PathVariable Long planId, @PathVariable Long itemId,
            @PathVariable Long requestId,
            @RequestBody(required = false) @Valid AiMealDraftRejectRequestDto request) {
        service.reject(user.getUsername(), planId, itemId, requestId, request);
        return ResponseEntity.noContent().build();
    }
}
