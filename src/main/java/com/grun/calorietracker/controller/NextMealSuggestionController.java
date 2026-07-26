package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.dto.NextMealSuggestionDto;
import com.grun.calorietracker.service.NextMealSuggestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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
@RequestMapping("/api/v1/meal-coach")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Next Meal", description = "Deterministic home-screen meal targeting and recipe suggestions.")
public class NextMealSuggestionController {

    private final NextMealSuggestionService nextMealSuggestionService;

    @GetMapping("/next")
    @Operation(
            summary = "Get the next meal suggestion",
            description = "Uses today's trusted remaining calorie and macro targets. It does not generate an AI response or consume AI credit."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Next-meal state returned.",
                    content = @Content(schema = @Schema(implementation = NextMealSuggestionDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<NextMealSuggestionDto> getNextMeal(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(nextMealSuggestionService.getNextMeal(userDetails.getUsername()));
    }
}
