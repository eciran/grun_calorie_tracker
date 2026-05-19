package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.GoalCalculationResponse;
import com.grun.calorietracker.dto.GoalCalculationRequestDto;
import com.grun.calorietracker.service.UserGoalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@RequestMapping({"/api/goals", "/api/v1/goals"})
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Goals", description = "Calorie and macro goal calculation, saving, and deletion for the authenticated user.")
class UserGoalController {

    private final UserGoalService userGoalService;

    @PostMapping("/calculate")
    @Operation(
            summary = "Calculate a user goal",
            description = "Calculates calorie and macro targets from the submitted goal data without persisting the goal."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Goal calculation returned."),
            @ApiResponse(responseCode = "400", description = "Request validation failed."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid."),
            @ApiResponse(responseCode = "404", description = "Authenticated user could not be found.")
    })
    public ResponseEntity<GoalCalculationResponse> calculateGoal(
            @RequestBody @Valid GoalCalculationRequestDto goalData,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        GoalCalculationResponse result = userGoalService.calculateGoal(goalData, userDetails.getUsername());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/save")
    @Operation(
            summary = "Save a user goal",
            description = "Calculates and stores the authenticated user's current calorie and macro goal."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Goal saved and calculation returned."),
            @ApiResponse(responseCode = "400", description = "Request validation failed."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.")
    })
    public ResponseEntity<GoalCalculationResponse> saveGoal(@Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
                                                            @RequestBody @Valid GoalCalculationRequestDto goalData) {

        GoalCalculationResponse response = userGoalService.calculateGoal(goalData, userDetails.getUsername());
        userGoalService.saveUserGoal(goalData, userDetails.getUsername());

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/delete")
    @Operation(
            summary = "Delete current user goal",
            description = "Deletes the saved goal for the authenticated user."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Goal deleted."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.")
    })
    public ResponseEntity<String> deleteGoal(@Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        userGoalService.deleteGoalByUser(userDetails.getUsername());
        return ResponseEntity.ok("User goal deleted successfully");
    }
}
