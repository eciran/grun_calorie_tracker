package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AiCreditPreviewDto;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.service.AiCreditPreviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "AI UX Contract", description = "Shared AI credit and lifecycle metadata for mobile clients.")
public class AiCreditPreviewController {

    private final AiCreditPreviewService aiCreditPreviewService;

    @GetMapping("/credit-preview")
    @Operation(summary = "Preview fixed AI credit cost without consuming credit")
    public ResponseEntity<AiCreditPreviewDto> preview(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam AiRequestType requestType) {
        return ResponseEntity.ok(aiCreditPreviewService.preview(
                userDetails.getUsername(), requestType));
    }
}