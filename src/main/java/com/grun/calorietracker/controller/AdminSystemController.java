package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AdminSystemHealthDto;
import com.grun.calorietracker.dto.AiProviderSmokeResponseDto;
import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.enums.AiRequestType;
import com.grun.calorietracker.service.AiProviderSmokeService;
import com.grun.calorietracker.service.AdminSystemHealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/system")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin System", description = "Admin-only production observability endpoints.")
public class AdminSystemController {

    private final AdminSystemHealthService adminSystemHealthService;
    private final AiProviderSmokeService aiProviderSmokeService;

    @GetMapping("/health")
    @Operation(
            summary = "Get admin system health summary",
            description = "Returns safe application and database health details for admin monitoring. Secrets and credentials are never returned."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "System health summary returned.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminSystemHealthDto.class))
            ),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<AdminSystemHealthDto> getHealth() {
        return ResponseEntity.ok(adminSystemHealthService.getHealth());
    }

    @PostMapping("/ai-provider/smoke")
    @Operation(
            summary = "Smoke test configured AI provider",
            description = "Admin-only synthetic smoke test for the configured AI provider. This does not consume user quota and does not write a user draft."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "AI provider smoke result returned.", content = @Content(schema = @Schema(implementation = AiProviderSmokeResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<AiProviderSmokeResponseDto> smokeAiProvider(
            @RequestParam(defaultValue = "VOICE_FOOD_LOG") AiRequestType requestType) {
        return ResponseEntity.ok(aiProviderSmokeService.smoke(requestType));
    }
}
