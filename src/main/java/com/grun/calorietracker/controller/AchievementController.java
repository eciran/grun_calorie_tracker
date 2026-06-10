package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AchievementSummaryDto;
import com.grun.calorietracker.service.AchievementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/achievements")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Achievements", description = "Authenticated user achievement progress and unlock state.")
@RequiredArgsConstructor
public class AchievementController {

    private final AchievementService achievementService;

    @GetMapping("/me")
    @Operation(summary = "Get my achievements", description = "Evaluates and returns all active achievements for the authenticated user.")
    public ResponseEntity<AchievementSummaryDto> getMyAchievements(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(achievementService.getMyAchievements(userDetails.getUsername()));
    }

    @PostMapping("/me/evaluate")
    @Operation(summary = "Re-evaluate my achievements", description = "Forces recalculation and returns the updated achievement state.")
    public ResponseEntity<AchievementSummaryDto> evaluateMyAchievements(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(achievementService.evaluateMyAchievements(userDetails.getUsername()));
    }
}
