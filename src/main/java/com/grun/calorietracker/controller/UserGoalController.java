package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.GoalCalculationResponse;
import com.grun.calorietracker.dto.UserGoalDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.exception.UserNotFoundException;
import com.grun.calorietracker.service.UserGoalService;
import com.grun.calorietracker.service.UserService;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
class UserGoalController {

    private final UserGoalService userGoalService;
    private final UserService userService;

    @PostMapping("/calculate")
    public ResponseEntity<GoalCalculationResponse> calculateGoal(
            @RequestBody @Valid UserGoalDto goalData,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UserEntity user = userService.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        GoalCalculationResponse result = userGoalService.calculateGoal(goalData, userDetails.getUsername());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/save")
    public ResponseEntity<GoalCalculationResponse> saveGoal( @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
                                                            @RequestBody @Valid UserGoalDto goalRequest) {
        GoalCalculationResponse response = userGoalService.calculateGoal(goalRequest, userDetails.getUsername());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/delete")
    public ResponseEntity<String> deleteGoal( @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        userGoalService.deleteGoalByUser(userDetails.getUsername());
        return ResponseEntity.ok("User goal deleted successfully");
    }
}
