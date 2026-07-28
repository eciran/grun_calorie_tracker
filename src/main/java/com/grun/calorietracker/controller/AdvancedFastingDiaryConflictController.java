package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.service.AdvancedFastingDiaryConflictService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/fasting/advanced/diary-conflicts")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Advanced Fasting Diary Conflicts")
public class AdvancedFastingDiaryConflictController {
    private final AdvancedFastingDiaryConflictService service;

    @PostMapping("/evaluate")
    @Operation(summary = "Evaluate whether a food timestamp conflicts with a planned or active fast")
    public ResponseEntity<FastingDiaryConflictDto> evaluate(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody FastingDiaryConflictEvaluateRequestDto request) {
        return ResponseEntity.ok(service.evaluate(user.getUsername(), request));
    }

    @PostMapping("/resolve")
    @Operation(summary = "Resolve a fasting conflict and atomically create the food log")
    public ResponseEntity<FastingDiaryConflictResolutionDto> resolve(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody FastingDiaryConflictResolveRequestDto request) {
        return ResponseEntity.ok(service.resolve(user.getUsername(), request));
    }
}