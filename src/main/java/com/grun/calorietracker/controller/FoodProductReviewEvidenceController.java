package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.FoodProductEvidenceResubmitRequestDto;
import com.grun.calorietracker.dto.FoodProductReviewSubmitResponseDto;
import com.grun.calorietracker.service.FoodProductReviewSubmissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products/review-cases")
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "grun.food-contribution-storage",
        name = "provider",
        havingValue = "S3"
)
public class FoodProductReviewEvidenceController {

    private final FoodProductReviewSubmissionService submissionService;

    @PostMapping("/{id}/evidence")
    public ResponseEntity<FoodProductReviewSubmitResponseDto> resubmitEvidence(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody FoodProductEvidenceResubmitRequestDto request
    ) {
        return ResponseEntity.ok(submissionService.resubmitEvidence(
                userDetails.getUsername(),
                id,
                request.uploadSessionId()
        ));
    }
}