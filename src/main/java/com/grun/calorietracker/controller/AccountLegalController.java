package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.dto.UserConsentDto;
import com.grun.calorietracker.dto.UserConsentRequestDto;
import com.grun.calorietracker.service.LegalConsentService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/account/legal")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Account Legal", description = "Authenticated user legal consent history endpoints.")
public class AccountLegalController {

    private final LegalConsentService legalConsentService;

    @GetMapping("/consents")
    @Operation(summary = "List my legal consents", description = "Returns the authenticated user's consent decision history.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Consent history returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<List<UserConsentDto>> listMyConsents(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(legalConsentService.listMyConsents(userDetails.getUsername()));
    }

    @PostMapping("/consents")
    @Operation(summary = "Record legal consent decision", description = "Records an immutable accept/revoke decision for a legal consent type and text version.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Consent decision recorded."),
            @ApiResponse(responseCode = "400", description = "Request validation failed.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<UserConsentDto> recordConsent(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid UserConsentRequestDto request,
            HttpServletRequest servletRequest) {
        return ResponseEntity.ok(legalConsentService.recordConsent(
                userDetails.getUsername(),
                request,
                resolveIp(servletRequest),
                servletRequest.getHeader("User-Agent")
        ));
    }

    private String resolveIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
