package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.dto.UserNutritionPreferenceDto;
import com.grun.calorietracker.service.UserNutritionPreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users/me/nutrition-preferences")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Users",
        description = "Authenticated user's own profile and body composition operations.")
public class UserNutritionPreferenceController {

    private final UserNutritionPreferenceService service;

    @GetMapping
    @Operation(summary = "Get nutrition safety preferences",
            description = "Returns persistent allergens, excluded foods, and dietary preferences used by controlled AI nutrition features.")
    public ResponseEntity<UserNutritionPreferenceDto> get(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(service.get(user.getUsername()));
    }

    @PutMapping
    @Operation(summary = "Update nutrition safety preferences",
            description = "Replaces the authenticated user's persistent nutrition preferences. These values never create or modify global food catalog records.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Nutrition preferences updated."),
            @ApiResponse(responseCode = "400",
                    description = "Validation failed.",
                    content = @Content(schema = @Schema(
                            implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401",
                    description = "JWT token is missing or invalid.",
                    content = @Content(schema = @Schema(
                            implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<UserNutritionPreferenceDto> update(
            @AuthenticationPrincipal UserDetails user,
            @RequestBody @Valid UserNutritionPreferenceDto request) {
        return ResponseEntity.ok(
                service.update(user.getUsername(), request));
    }
}
