package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.dto.RevenueCatConfigStatusDto;
import com.grun.calorietracker.dto.RevenueCatMappingValidationRequestDto;
import com.grun.calorietracker.dto.RevenueCatMappingValidationResponseDto;
import com.grun.calorietracker.service.RevenueCatConfigurationService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/revenuecat")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin RevenueCat Configuration", description = "Admin-only endpoints for RevenueCat setup verification before production account/store wiring.")
public class AdminRevenueCatConfigController {

    private final RevenueCatConfigurationService revenueCatConfigurationService;

    @GetMapping("/config")
    @Operation(summary = "Get RevenueCat configuration status", description = "Returns safe RevenueCat backend configuration details without exposing webhook secrets.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "RevenueCat configuration returned.",
                    content = @Content(schema = @Schema(implementation = RevenueCatConfigStatusDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<RevenueCatConfigStatusDto> getConfigStatus() {
        return ResponseEntity.ok(revenueCatConfigurationService.getConfigStatus());
    }

    @PostMapping("/mapping/validate")
    @Operation(summary = "Validate RevenueCat product mapping", description = "Validates whether a RevenueCat event/product/entitlement combination will map to a subscription plan or AI add-on quota.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mapping validation completed.",
                    content = @Content(schema = @Schema(implementation = RevenueCatMappingValidationResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Request validation failed.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<RevenueCatMappingValidationResponseDto> validateMapping(
            @Valid @RequestBody RevenueCatMappingValidationRequestDto request) {
        return ResponseEntity.ok(revenueCatConfigurationService.validateMapping(request));
    }
}
