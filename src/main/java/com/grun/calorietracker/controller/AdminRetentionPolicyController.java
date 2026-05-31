package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.dto.RetentionPolicyDto;
import com.grun.calorietracker.dto.RetentionPolicyUpdateRequestDto;
import com.grun.calorietracker.enums.RetentionPolicyKey;
import com.grun.calorietracker.service.RetentionPolicyService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/legal/retention-policies")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Legal Retention", description = "Admin-only retention policy management endpoints for legal/GDPR operations.")
public class AdminRetentionPolicyController {

    private final RetentionPolicyService retentionPolicyService;

    @GetMapping
    @Operation(summary = "List retention policies", description = "Returns configured data retention rules for admin/legal review.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Retention policies returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<List<RetentionPolicyDto>> listPolicies() {
        return ResponseEntity.ok(retentionPolicyService.listPolicies());
    }

    @PutMapping("/{policyKey}")
    @Operation(summary = "Create or update retention policy", description = "Creates or updates one retention rule and records the admin change in audit history.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Retention policy saved."),
            @ApiResponse(responseCode = "400", description = "Request validation failed.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<RetentionPolicyDto> upsertPolicy(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable RetentionPolicyKey policyKey,
            @RequestBody @Valid RetentionPolicyUpdateRequestDto request) {
        return ResponseEntity.ok(retentionPolicyService.upsertPolicy(userDetails.getUsername(), policyKey, request));
    }
}
