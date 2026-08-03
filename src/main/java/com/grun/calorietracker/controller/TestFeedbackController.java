package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.TestFeedbackCreateRequestDto;
import com.grun.calorietracker.dto.TestFeedbackSubmissionDto;
import com.grun.calorietracker.dto.TestFeedbackScreenshotUploadRequestDto;
import com.grun.calorietracker.dto.TestFeedbackScreenshotUploadDto;
import com.grun.calorietracker.service.TestFeedbackScreenshotService;
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
    private final TestFeedbackScreenshotService screenshotService;

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
    @PostMapping("/{id}/screenshot/upload-authorization")
    public TestFeedbackScreenshotUploadDto authorizeScreenshot(@PathVariable Long id, @RequestHeader("X-App-Environment") String environment, @Valid @RequestBody TestFeedbackScreenshotUploadRequestDto request, @AuthenticationPrincipal UserDetails user) {
        return screenshotService.authorize(user.getUsername(), environment, id, request);
    }

    @PostMapping("/{id}/screenshot/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void completeScreenshot(@PathVariable Long id, @RequestHeader("X-App-Environment") String environment, @AuthenticationPrincipal UserDetails user) {
        screenshotService.complete(user.getUsername(), environment, id);
    }
}
