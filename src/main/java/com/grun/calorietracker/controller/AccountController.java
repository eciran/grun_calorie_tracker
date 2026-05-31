package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AccountPasswordRequestDto;
import com.grun.calorietracker.dto.AccountPasswordResponseDto;
import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.dto.GdprDataExportDto;
import com.grun.calorietracker.dto.GdprDeleteRequestDto;
import com.grun.calorietracker.dto.GdprDeleteResponseDto;
import com.grun.calorietracker.dto.LinkAppleRequestDto;
import com.grun.calorietracker.dto.LinkGoogleRequestDto;
import com.grun.calorietracker.dto.LinkedIdentityDto;
import com.grun.calorietracker.enums.AuthProvider;
import com.grun.calorietracker.service.AccountGdprService;
import com.grun.calorietracker.service.AccountIdentityService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/account")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Account", description = "Authenticated account security, provider linking, and password setup operations.")
public class AccountController {

    private final AccountIdentityService accountIdentityService;
    private final AccountGdprService accountGdprService;

    @GetMapping("/linked-identities")
    @Operation(
            summary = "List linked login providers",
            description = "Returns Google, Apple, or other external identities linked to the authenticated account."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Linked providers returned."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<List<LinkedIdentityDto>> listLinkedIdentities(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(accountIdentityService.listLinkedIdentities(userDetails.getUsername()));
    }

    @PostMapping("/link/google")
    @Operation(
            summary = "Link Google login",
            description = "Verifies a Google ID token and links that Google subject to the authenticated GRun account."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Google identity linked."),
            @ApiResponse(responseCode = "400", description = "Request validation failed, provider login is not configured, or identity is already linked elsewhere.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or provider token is invalid.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<LinkedIdentityDto> linkGoogle(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid LinkGoogleRequestDto request) {
        return ResponseEntity.ok(accountIdentityService.linkGoogle(userDetails.getUsername(), request.getIdToken()));
    }

    @PostMapping("/link/apple")
    @Operation(
            summary = "Link Apple login",
            description = "Verifies an Apple identity token and nonce, then links that Apple subject to the authenticated GRun account."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Apple identity linked."),
            @ApiResponse(responseCode = "400", description = "Request validation failed, provider login is not configured, or identity is already linked elsewhere.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or provider token is invalid.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<LinkedIdentityDto> linkApple(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid LinkAppleRequestDto request) {
        return ResponseEntity.ok(accountIdentityService.linkApple(userDetails.getUsername(), request.getIdToken(), request.getNonce()));
    }

    @DeleteMapping("/linked-identities/{provider}")
    @Operation(
            summary = "Unlink login provider",
            description = "Removes a linked Google or Apple identity from the authenticated account. The last available sign-in method cannot be removed."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Provider identity unlinked."),
            @ApiResponse(responseCode = "400", description = "Provider is not linked or it is the last available sign-in method.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<Void> unlinkProvider(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable AuthProvider provider) {
        accountIdentityService.unlinkProvider(userDetails.getUsername(), provider);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/password")
    @Operation(
            summary = "Set or change account password",
            description = "Sets an initial password for provider-created accounts, or changes an existing password after validating the current password."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password updated successfully."),
            @ApiResponse(responseCode = "400", description = "Request validation failed.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or current password is invalid.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<AccountPasswordResponseDto> updatePassword(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid AccountPasswordRequestDto request) {
        return ResponseEntity.ok(accountIdentityService.updatePassword(userDetails.getUsername(), request));
    }

    @GetMapping("/gdpr/export")
    @Operation(
            summary = "Export my account data",
            description = "Returns a GDPR-friendly JSON export snapshot of profile and account-related data owned by the authenticated user."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Data export created."),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<GdprDataExportDto> exportMyData(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(accountGdprService.exportMyData(userDetails.getUsername()));
    }

    @DeleteMapping("/gdpr")
    @Operation(
            summary = "Anonymize and delete my account access",
            description = "Anonymizes the authenticated user account and removes account-linked data where possible. Requires confirmText=DELETE_MY_ACCOUNT."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account anonymized and access deleted."),
            @ApiResponse(responseCode = "400", description = "Request validation failed or confirm text is invalid.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<GdprDeleteResponseDto> anonymizeAndDelete(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid GdprDeleteRequestDto request) {
        accountGdprService.anonymizeAndDeleteAccount(
                userDetails.getUsername(),
                request.getConfirmText(),
                request.getCurrentPassword()
        );
        return ResponseEntity.ok(new GdprDeleteResponseDto("Account anonymized and deleted successfully."));
    }
}
