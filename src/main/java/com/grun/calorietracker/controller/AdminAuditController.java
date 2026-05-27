package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AdminActionAuditPageDto;
import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.enums.AdminAuditActionType;
import com.grun.calorietracker.enums.AdminAuditTargetType;
import com.grun.calorietracker.service.AdminAuditService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audits")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Audits", description = "Admin-only audit trail for critical configuration and account management changes.")
public class AdminAuditController {

    private final AdminAuditService adminAuditService;

    @GetMapping
    @Operation(
            summary = "List admin action audits",
            description = "Returns a paginated audit trail of critical admin actions such as subscription updates, quota changes, and plan feature matrix updates."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Admin audit entries returned.",
                    content = @Content(schema = @Schema(implementation = AdminActionAuditPageDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<AdminActionAuditPageDto> listAudits(
            @Parameter(description = "Optional action type filter.", example = "SUBSCRIPTION_FEATURE_UPDATE")
            @RequestParam(required = false) AdminAuditActionType actionType,
            @Parameter(description = "Optional target type filter.", example = "SUBSCRIPTION_FEATURE")
            @RequestParam(required = false) AdminAuditTargetType targetType,
            @Parameter(description = "Zero-based page number.", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, capped at 100.", example = "25")
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(adminAuditService.list(actionType, targetType, page, size));
    }
}
