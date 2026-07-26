package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AdminCatalogImportJobDto;
import com.grun.calorietracker.dto.AdminCatalogSummaryDto;
import com.grun.calorietracker.dto.CatalogReviewAssignmentRequestDto;
import com.grun.calorietracker.dto.ExerciseItemDto;
import com.grun.calorietracker.dto.ExerciseItemPageDto;
import com.grun.calorietracker.dto.ExerciseTechniqueReviewRequestDto;
import com.grun.calorietracker.enums.ExerciseDifficulty;
import com.grun.calorietracker.enums.ExerciseTechniqueReviewStatus;
import com.grun.calorietracker.service.AdminCatalogOperationsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/catalog")
@RequiredArgsConstructor
@Validated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Catalog Operations", description = "Admin-only catalog health, exercise moderation, source evidence, and review ownership.")
public class AdminCatalogOperationsController {
    private final AdminCatalogOperationsService service;

    @GetMapping("/summary")
    @Operation(summary = "Get catalog operations summary")
    public ResponseEntity<AdminCatalogSummaryDto> summary() {
        return ResponseEntity.ok(service.summary());
    }

    @GetMapping("/import-jobs")
    @Operation(summary = "List recent catalog import and validation jobs")
    public ResponseEntity<List<AdminCatalogImportJobDto>> importJobs() {
        return ResponseEntity.ok(service.recentImportJobs());
    }

    @GetMapping("/exercises")
    @Operation(summary = "Search the complete exercise moderation catalog")
    public ResponseEntity<ExerciseItemPageDto> exercises(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ExerciseDifficulty difficulty,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) ExerciseTechniqueReviewStatus reviewStatus,
            @RequestParam(required = false) String assignee,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(service.searchExercises(q, difficulty, active, reviewStatus, assignee, page, size));
    }

    @PostMapping("/exercises")
    @Operation(summary = "Create a pending-review exercise")
    public ResponseEntity<ExerciseItemDto> createExercise(
            @AuthenticationPrincipal UserDetails admin,
            @RequestBody @Valid ExerciseItemDto request) {
        return ResponseEntity.ok(service.createExercise(admin.getUsername(), request));
    }

    @PutMapping("/exercises/{id}")
    @Operation(summary = "Update exercise catalog metadata")
    public ResponseEntity<ExerciseItemDto> updateExercise(
            @AuthenticationPrincipal UserDetails admin,
            @PathVariable Long id,
            @RequestBody @Valid ExerciseItemDto request) {
        return ResponseEntity.ok(service.updateExercise(admin.getUsername(), id, request));
    }

    @PatchMapping("/exercises/{id}/review")
    @Operation(summary = "Review exercise technique and safety content")
    public ResponseEntity<ExerciseItemDto> reviewExercise(
            @AuthenticationPrincipal UserDetails admin,
            @PathVariable Long id,
            @RequestBody @Valid ExerciseTechniqueReviewRequestDto request) {
        return ResponseEntity.ok(service.reviewExercise(admin.getUsername(), id, request));
    }

    @PatchMapping("/review-items/{itemType}/{itemId}/assignment")
    @Operation(summary = "Assign a catalog review owner and SLA")
    public ResponseEntity<Void> assignReview(
            @AuthenticationPrincipal UserDetails admin,
            @PathVariable String itemType,
            @PathVariable Long itemId,
            @RequestBody @Valid CatalogReviewAssignmentRequestDto request) {
        service.assignReview(admin.getUsername(), itemType, itemId, request);
        return ResponseEntity.noContent().build();
    }
}
