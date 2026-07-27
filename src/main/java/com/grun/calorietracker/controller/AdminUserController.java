package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AdminUserStatusUpdateRequestDto;
import com.grun.calorietracker.dto.AdminUserPageDto;
import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.dto.AdminUserDto;
import com.grun.calorietracker.enums.AdminAuditActionType;
import com.grun.calorietracker.enums.AdminAuditTargetType;
import com.grun.calorietracker.enums.AdminUserActivityFilter;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.SubscriptionPlan;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.security.CorrelationIdFilter;
import com.grun.calorietracker.service.AdminAuditService;
import com.grun.calorietracker.service.UserService;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Users", description = "Admin-only user management operations.")
public class AdminUserController {

    private final UserService userService;
    private final AdminAuditService adminAuditService;

    public AdminUserController(UserService userService, AdminAuditService adminAuditService) {
        this.userService = userService;
        this.adminAuditService = adminAuditService;
    }

    @GetMapping
    @Operation(
            summary = "List users with pagination",
            description = "Returns paginated user profiles for admin review. Supports optional role and account status filters."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User page returned.",
                    content = @Content(schema = @Schema(implementation = AdminUserPageDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public AdminUserPageDto listUsers(
            @Parameter(description = "Email, name, or exact numeric id search.") @RequestParam(required = false) String search,
            @Parameter(description = "Optional role filter.", example = "STANDARD") @RequestParam(required = false) UserRole role,
            @Parameter(description = "Optional enabled status filter.", example = "true") @RequestParam(required = false) Boolean accountEnabled,
            @Parameter(description = "Optional locked status filter.", example = "false") @RequestParam(required = false) Boolean accountLocked,
            @Parameter(description = "Optional subscription plan filter.") @RequestParam(required = false) SubscriptionPlan plan,
            @Parameter(description = "Optional market region filter.") @RequestParam(required = false) MarketRegion region,
            @Parameter(description = "Optional language filter.") @RequestParam(required = false) PreferredLanguage language,
            @Parameter(description = "Optional email verification filter.") @RequestParam(required = false) Boolean emailVerified,
            @Parameter(description = "Optional 30-day activity state filter.") @RequestParam(required = false) AdminUserActivityFilter activity,
            @Parameter(description = "Zero-based page number.", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size. Maximum 100.", example = "25") @RequestParam(defaultValue = "50") int size) {
        return userService.listUsersForAdmin(
                search, role, accountEnabled, accountLocked, plan, region, language,
                emailVerified, activity, page, size
        );
    }

    @PatchMapping("/{userId}/status")
    @Operation(
            summary = "Update user account status",
            description = "Enables/disables or locks/unlocks a user account. Admins cannot disable or lock their own account."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User account status updated.",
                    content = @Content(schema = @Schema(implementation = AdminUserDto.class))),
            @ApiResponse(responseCode = "400", description = "Request validation failed or admin attempted to lock their own account.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "User could not be found.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public AdminUserDto updateUserStatus(
            @Parameter(description = "User id.", example = "1") @PathVariable Long userId,
            @RequestBody @Valid AdminUserStatusUpdateRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {
        Optional<AdminUserDto> before = userService.getById(userId);
        AdminUserDto response = userService.updateUserStatus(userId, request, adminEmail(userDetails));
        adminAuditService.record(
                adminEmail(userDetails),
                AdminAuditActionType.USER_STATUS_UPDATE,
                AdminAuditTargetType.USER_ACCOUNT,
                userId.toString(),
                before.orElse(null),
                Map.of("user", response, "reason", request.getReason().trim()),
                correlationId(httpRequest)
        );
        return response;
    }

    private String adminEmail(UserDetails userDetails) {
        return userDetails == null ? "unknown-admin" : userDetails.getUsername();
    }

    private String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE);
        return value == null ? request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER) : value.toString();
    }
}
