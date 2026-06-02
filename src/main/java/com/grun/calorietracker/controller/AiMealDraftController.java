package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AiMealDraftResponseDto;
import com.grun.calorietracker.dto.AiMealDraftConfirmRequestDto;
import com.grun.calorietracker.dto.AiMealDraftConfirmResponseDto;
import com.grun.calorietracker.dto.AiMealDraftRejectRequestDto;
import com.grun.calorietracker.dto.AiPhotoMealDraftRequestDto;
import com.grun.calorietracker.dto.AiPhotoReferenceDto;
import com.grun.calorietracker.dto.AiRequestHistoryDto;
import com.grun.calorietracker.dto.AiVoiceFoodDraftRequestDto;
import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.service.AiMealDraftService;
import com.grun.calorietracker.service.AiPhotoReferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ai/meal-drafts")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "AI Meal Drafts", description = "Provider-agnostic AI draft creation for voice and photo meal logging.")
public class AiMealDraftController {

    private final AiMealDraftService aiMealDraftService;
    private final AiPhotoReferenceService aiPhotoReferenceService;

    @PostMapping("/voice")
    @Operation(
            summary = "Create meal draft from voice transcript",
            description = "Creates an AI-generated meal draft from mobile speech-to-text transcript. The result is not written to the food diary until the user confirms it."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "AI meal draft created.",
                    content = @Content(schema = @Schema(implementation = AiMealDraftResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "AI provider disabled, quota unavailable, or request invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<AiMealDraftResponseDto> createVoiceDraft(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid AiVoiceFoodDraftRequestDto request) {
        return ResponseEntity.ok(aiMealDraftService.createVoiceFoodDraft(userDetails.getUsername(), request));
    }

    @PostMapping("/photo")
    @Operation(
            summary = "Create meal draft from photo reference",
            description = "Creates an AI-generated meal draft from an image reference. Image upload/storage remains a mobile or media-service concern."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "AI meal draft created.",
                    content = @Content(schema = @Schema(implementation = AiMealDraftResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "AI provider disabled, quota unavailable, or request invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<AiMealDraftResponseDto> createPhotoDraft(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid AiPhotoMealDraftRequestDto request) {
        return ResponseEntity.ok(aiMealDraftService.createPhotoMealDraft(userDetails.getUsername(), request));
    }

    @PostMapping(value = "/photo-references", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload photo for AI meal draft",
            description = "Stores a meal photo and returns a short-lived image reference URL that can be used by the photo draft endpoint."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Photo reference created.",
                    content = @Content(schema = @Schema(implementation = AiPhotoReferenceDto.class))),
            @ApiResponse(responseCode = "400", description = "Photo is missing, too large, or has an unsupported content type.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<AiPhotoReferenceDto> uploadPhotoReference(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Meal photo file. Allowed content types are configured by backend.")
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(aiPhotoReferenceService.createReference(userDetails.getUsername(), file));
    }

    @GetMapping("/photo-references/{token}")
    @Operation(
            summary = "Load AI meal photo reference",
            description = "Serves a short-lived uploaded photo reference for AI provider access. The token is unguessable and expires according to backend config."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Photo binary returned."),
            @ApiResponse(responseCode = "400", description = "Reference is invalid or expired.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<Resource> loadPhotoReference(
            @Parameter(description = "Short-lived photo reference token.") @PathVariable String token) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(aiPhotoReferenceService.loadReference(token));
    }

    @PostMapping("/{requestId}/confirm")
    @Operation(
            summary = "Confirm an AI meal draft into food logs",
            description = "Writes user-reviewed AI draft items to the food diary. The user must supply final foodItemId, amount, unit, meal type, and log date for every item."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "AI draft confirmed and food logs created.",
                    content = @Content(schema = @Schema(implementation = AiMealDraftConfirmResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Draft is missing, already closed, or request validation failed.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<AiMealDraftConfirmResponseDto> confirmDraft(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "AI request id returned by draft creation.", example = "10") @PathVariable Long requestId,
            @RequestBody @Valid AiMealDraftConfirmRequestDto request) {
        return ResponseEntity.ok(aiMealDraftService.confirmDraft(userDetails.getUsername(), requestId, request));
    }

    @PostMapping("/{requestId}/reject")
    @Operation(
            summary = "Reject an AI meal draft",
            description = "Closes an AI draft without creating food logs. Optional reject reason and feedback can be submitted to improve AI quality monitoring."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "AI draft rejected."),
            @ApiResponse(responseCode = "400", description = "Draft is missing or already closed.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<AiRequestHistoryDto> rejectDraft(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "AI request id returned by draft creation.", example = "10") @PathVariable Long requestId,
            @RequestBody(required = false) @Valid AiMealDraftRejectRequestDto request) {
        return ResponseEntity.ok(aiMealDraftService.rejectDraft(userDetails.getUsername(), requestId, request));
    }

    @GetMapping("/history")
    @Operation(
            summary = "List AI meal draft history",
            description = "Returns recent AI draft requests for the authenticated user without exposing full prompt or image payloads."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "AI request history returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<List<AiRequestHistoryDto>> listHistory(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "Maximum history size. Maximum is controlled by backend config.", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        return ResponseEntity.ok(aiMealDraftService.listHistory(userDetails.getUsername(), limit));
    }
}
