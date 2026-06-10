package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AiRecipeDraftConfirmRequestDto;
import com.grun.calorietracker.dto.AiRecipeDraftRequestDto;
import com.grun.calorietracker.dto.AiRecipeDraftResponseDto;
import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.dto.RecipeDto;
import com.grun.calorietracker.service.AiRecipeDraftService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/recipes")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "AI Recipes", description = "AI-generated recipe drafts. Drafts require user review before a recipe is persisted.")
public class AiRecipeDraftController {

    private final AiRecipeDraftService aiRecipeDraftService;

    @PostMapping("/generate")
    @Operation(
            summary = "Generate an AI recipe draft",
            description = "Creates an AI recipe draft from user preferences. The draft is not saved as a recipe until the user confirms a final reviewed payload."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "AI recipe draft created.",
                    content = @Content(schema = @Schema(implementation = AiRecipeDraftResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "AI provider disabled, quota unavailable, or request invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<AiRecipeDraftResponseDto> generateRecipeDraft(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid AiRecipeDraftRequestDto request) {
        return ResponseEntity.ok(aiRecipeDraftService.createRecipeDraft(userDetails.getUsername(), request));
    }

    @PostMapping("/{requestId}/confirm")
    @Operation(
            summary = "Confirm an AI recipe draft",
            description = "Persists a user-reviewed AI recipe draft as a private recipe. The final recipe payload must include real foodItemId ingredient mappings."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recipe created from reviewed AI draft.",
                    content = @Content(schema = @Schema(implementation = RecipeDto.class))),
            @ApiResponse(responseCode = "400", description = "Draft is missing, already closed, or final recipe validation failed.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<RecipeDto> confirmRecipeDraft(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "AI recipe request id returned by generation.", example = "10") @PathVariable Long requestId,
            @RequestBody @Valid AiRecipeDraftConfirmRequestDto request) {
        return ResponseEntity.ok(aiRecipeDraftService.confirmRecipeDraft(userDetails.getUsername(), requestId, request));
    }
}
