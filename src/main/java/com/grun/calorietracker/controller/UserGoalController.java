package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.GoalCalculationResponse;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserGoalEntity;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.exception.UserNotFoundException;
import com.grun.calorietracker.service.UserGoalService;
import com.grun.calorietracker.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/goals")
class UserGoalController {

    private final UserGoalService userGoalService;
    private final UserService userService;

    public UserGoalController(UserGoalService userGoalService, UserService userService) {
        this.userGoalService = userGoalService;
        this.userService = userService;
    }

    @PostMapping("/calculate")
    public ResponseEntity<GoalCalculationResponse> calculateGoal(
            @RequestBody UserGoalEntity goalData,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UserEntity user = userService.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        GoalCalculationResponse result = userGoalService.calculateGoal(goalData, user);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/save")
    public ResponseEntity<?> saveGoal(@AuthenticationPrincipal UserDetails userDetails,
                                      @RequestBody UserGoalEntity goalData) {

        UserEntity user = userService.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));

        if (user.getEmail() == null) {
            throw new InvalidCredentialsException("Invalid credential");
        }

        userGoalService.deleteGoalByUser(user);

        goalData.setUser(user);
        GoalCalculationResponse response = userGoalService.calculateGoal(goalData, user);
        userGoalService.saveUserGoal(goalData);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteGoal(@AuthenticationPrincipal UserDetails userDetails) {
        UserEntity user = userService.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credential"));

        if (user.getEmail() == null) {
            throw new InvalidCredentialsException("Invalid credential");
        }

        userGoalService.deleteGoalByUser(user);
        return ResponseEntity.ok("User goal deleted successfully");
    }
}