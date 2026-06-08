package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AdminBrevoSenderDto;
import com.grun.calorietracker.dto.AdminBrevoSenderListDto;
import com.grun.calorietracker.dto.AdminBrevoSenderRequestDto;
import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.service.AdminBrevoSenderService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/mail/brevo/senders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin Brevo Senders", description = "Admin-only Brevo sender management proxy. Delete operations are intentionally not exposed.")
public class AdminBrevoSenderController {

    private final AdminBrevoSenderService adminBrevoSenderService;

    @GetMapping
    @Operation(
            summary = "Get Brevo senders",
            description = "Returns configured Brevo senders through a backend proxy. API keys are never returned."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Brevo senders returned.",
                    content = @Content(schema = @Schema(implementation = AdminBrevoSenderListDto.class))),
            @ApiResponse(responseCode = "401", description = "JWT token is missing or invalid.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
            @ApiResponse(responseCode = "403", description = "Authenticated user is not an admin.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<AdminBrevoSenderListDto> getSenders() {
        return ResponseEntity.ok(adminBrevoSenderService.getSenders());
    }

    @PostMapping
    @Operation(
            summary = "Create Brevo sender",
            description = "Creates a new Brevo sender. Brevo may send verification email to the sender address."
    )
    public ResponseEntity<AdminBrevoSenderDto> createSender(@Valid @RequestBody AdminBrevoSenderRequestDto request) {
        return ResponseEntity.ok(adminBrevoSenderService.createSender(request));
    }

    @PutMapping("/{senderId}")
    @Operation(
            summary = "Update Brevo sender",
            description = "Updates sender name/email. Delete sender is intentionally not supported in this API."
    )
    public ResponseEntity<AdminBrevoSenderDto> updateSender(
            @PathVariable Long senderId,
            @Valid @RequestBody AdminBrevoSenderRequestDto request) {
        return ResponseEntity.ok(adminBrevoSenderService.updateSender(senderId, request));
    }
}
