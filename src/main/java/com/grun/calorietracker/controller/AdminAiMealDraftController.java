package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AdminAiRequestReviewDto;
import com.grun.calorietracker.dto.AdminAiQuotaRefundRequestDto;
import com.grun.calorietracker.dto.AdminAiQuotaRefundResponseDto;
import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.enums.AdminAuditActionType;
import com.grun.calorietracker.enums.AdminAuditTargetType;
import com.grun.calorietracker.enums.AiRequestStatus;
import com.grun.calorietracker.service.AdminAiMealDraftService;
import com.grun.calorietracker.service.AdminAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/ai/meal-drafts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin AI Meal Drafts", description = "Admin review and recovery actions for AI meal draft quality.")
public class AdminAiMealDraftController {

    private final AdminAiMealDraftService adminAiMealDraftService;
    private final AdminAuditService adminAuditService;

    @GetMapping
    @Operation(
            summary = "List AI meal draft requests for admin review",
            description = "Returns AI meal draft requests with admin-only review metadata. Use refundableOnly=true to list rejected requests with remaining refundable quota."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "AI meal draft requests returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<Page<AdminAiRequestReviewDto>> listRequests(
            @Parameter(description = "Optional request status filter.", example = "REJECTED")
            @RequestParam(required = false) AiRequestStatus status,
            @Parameter(description = "When true, returns only rejected requests with remaining refundable quota.", example = "true")
            @RequestParam(defaultValue = "false") boolean refundableOnly,
            @Parameter(description = "Page number.", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size.", example = "25")
            @RequestParam(defaultValue = "25") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return ResponseEntity.ok(adminAiMealDraftService.listRequests(status, refundableOnly, PageRequest.of(safePage, safeSize)));
    }

    @PostMapping("/{requestId}/quota-refund")
    @Operation(
            summary = "Refund AI quota for a rejected AI draft",
            description = "Refunds admin-approved AI quota for one rejected draft. Refund amount must not exceed quota consumed by the AI request."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "AI quota refunded.",
                    content = @Content(schema = @Schema(implementation = AdminAiQuotaRefundResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Request is not refundable or amount exceeds request-level refundable quota.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<AdminAiQuotaRefundResponseDto> refundQuota(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "AI request id.", example = "10") @PathVariable Long requestId,
            @RequestBody @Valid AdminAiQuotaRefundRequestDto request,
            HttpServletRequest servletRequest) {
        AdminAiQuotaRefundResponseDto response = adminAiMealDraftService.refundQuota(
                userDetails.getUsername(),
                requestId,
                request
        );
        adminAuditService.record(
                userDetails.getUsername(),
                AdminAuditActionType.AI_QUOTA_REFUND,
                AdminAuditTargetType.AI_REQUEST,
                String.valueOf(requestId),
                null,
                response,
                (String) servletRequest.getAttribute("correlationId")
        );
        return ResponseEntity.ok(response);
    }
}
