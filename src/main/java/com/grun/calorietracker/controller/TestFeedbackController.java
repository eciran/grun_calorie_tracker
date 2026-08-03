package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.TestFeedbackCreateRequestDto;
import com.grun.calorietracker.dto.TestFeedbackSubmissionDto;
import com.grun.calorietracker.service.TestFeedbackSubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/test-feedback")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Test Feedback", description = "Preview/internal build feedback. This API is disabled for production builds.")
@RequiredArgsConstructor
public class TestFeedbackController {

    private final TestFeedbackSubmissionService service;

    @PostMapping
    @Operation(summary = "Submit page feedback", description = "Stores sanitized feedback and safe test-build context for the authenticated tester.")
    public ResponseEntity<TestFeedbackSubmissionDto> submit(
            @RequestHeader("X-App-Environment") String environment,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TestFeedbackCreateRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        TestFeedbackSubmissionDto response = service.submit(
                userDetails.getUsername(), environment, idempotencyKey, request);
        return ResponseEntity.status(response.duplicate() ? HttpStatus.OK : HttpStatus.CREATED).body(response);
    }
}
