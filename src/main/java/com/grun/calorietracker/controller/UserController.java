package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.BodyFatRequestDto;
import com.grun.calorietracker.dto.BodyFatResultDto;
import com.grun.calorietracker.dto.UserProfileDto;
import com.grun.calorietracker.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Users", description = "Authenticated user's own profile and body composition operations.")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    @Operation(
            summary = "Get current user profile",
            description = "Returns the profile of the authenticated user identified by the JWT token."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current user profile returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing, invalid, or user cannot be found.")
    })
    public ResponseEntity<UserProfileDto> getCurrentUser(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        try {
            UserProfileDto profile = userService.getCurrentUser(userDetails.getUsername());
            return ResponseEntity.ok(profile);
        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PutMapping("/me")
    @Operation(
            summary = "Update current user profile",
            description = "Updates profile information for the authenticated user."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile updated successfully."),
            @ApiResponse(responseCode = "400", description = "Request validation failed."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing, invalid, or user cannot be found.")
    })
    public ResponseEntity<UserProfileDto> updateCurrentUser(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid UserProfileDto updatedUserDto
    ) {
        String email = userDetails.getUsername();
        try {
            UserProfileDto updatedProfile = userService.updateCurrentUser(updatedUserDto, email);
            return ResponseEntity.ok(updatedProfile);
        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @PostMapping("/calculate-body-fat-or-bmi")
    @Operation(
            summary = "Calculate body fat and BMI",
            description = "Calculates body fat percentage and BMI for the authenticated user, then stores the latest values on the user profile."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Body composition values calculated and stored."),
            @ApiResponse(responseCode = "400", description = "Request validation failed."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing, invalid, or user cannot be found.")
    })
    public ResponseEntity<BodyFatResultDto> calculateBodyFatOrBmi(
            @RequestBody @Valid BodyFatRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {

        return userService.findByEmail(userDetails.getUsername())
                .map(user -> {
                    BodyFatResultDto result = userService.calculateBodyFatAndBmi(request, user);

                    user.setBodyFatPercentage(result.getBodyFat());
                    user.setBmi(result.getBmi());
                    UserProfileDto updatedUserDto = UserProfileDto.builder()
                            .bmi(result.getBmi())
                            .bodyFat(result.getBodyFat())
                            .build();

                    userService.updateCurrentUser(updatedUserDto, user.getEmail());

                    return ResponseEntity.ok(result);
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

}
