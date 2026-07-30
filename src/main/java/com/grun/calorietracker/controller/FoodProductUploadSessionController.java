package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.FoodProductUploadFinalizeDto;
import com.grun.calorietracker.dto.FoodProductUploadSessionDto;
import com.grun.calorietracker.dto.FoodProductUploadSessionRequestDto;
import com.grun.calorietracker.dto.FoodProductReviewSubmitRequestDto;
import com.grun.calorietracker.dto.FoodProductReviewSubmitResponseDto;
import com.grun.calorietracker.service.FoodProductUploadSessionService;
import com.grun.calorietracker.service.FoodProductReviewSubmissionService;
import com.grun.calorietracker.service.support.FoodProductIntakeMetrics;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products/review-cases/upload-sessions")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Food Product Review Cases", description = "Private direct-upload intake for product review evidence.")
@ConditionalOnProperty(prefix = "grun.food-contribution-storage", name = "provider", havingValue = "S3")
public class FoodProductUploadSessionController {
    private final FoodProductUploadSessionService service;
    private final FoodProductReviewSubmissionService submissionService;
    private final FoodProductIntakeMetrics metrics;

    @PostMapping
    @Operation(summary = "Create private product evidence upload slots")
    public ResponseEntity<FoodProductUploadSessionDto> create(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody FoodProductUploadSessionRequestDto request
    ) {
        try {
            var response = service.create(userDetails.getUsername(), request);
            metrics.record("upload_session_create", "success");
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException failure) {
            metrics.record("upload_session_create", "failure");
            throw failure;
        }
    }

    @PostMapping("/{sessionId}/finalize")
    @Operation(summary = "Validate and finalize private product evidence uploads")
    public ResponseEntity<FoodProductUploadFinalizeDto> finalizeUpload(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String sessionId
    ) {
        try {
            var response = service.finalizeUpload(userDetails.getUsername(), sessionId);
            metrics.record("upload_finalize", "success");
            return ResponseEntity.ok(response);
        } catch (RuntimeException failure) {
            metrics.record("upload_finalize", "failure");
            throw failure;
        }
    }

    @PostMapping("/{sessionId}/submit")
    @Operation(summary = "Submit a finalized evidence session for admin review")
    public ResponseEntity<FoodProductReviewSubmitResponseDto> submit(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String sessionId,
            @Valid @RequestBody FoodProductReviewSubmitRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(submissionService.submit(userDetails.getUsername(), sessionId, request));
    }
}