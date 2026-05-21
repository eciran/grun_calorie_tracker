package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AppStartupDto;
import com.grun.calorietracker.service.AppStartupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/app")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "App Startup", description = "Mobile app startup and routing state endpoints.")
public class AppStartupController {

    private final AppStartupService appStartupService;

    @GetMapping("/startup")
    @Operation(
            summary = "Get mobile startup state",
            description = "Returns the authenticated user's profile, active goal state, onboarding state, and recommended next mobile step."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mobile startup state returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.")
    })
    public ResponseEntity<AppStartupDto> getStartupState(
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        return ResponseEntity.ok(appStartupService.getStartupState(userDetails.getUsername()));
    }
}
