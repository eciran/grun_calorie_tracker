package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AdvancedFastingReminderSettingsDto;
import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.service.AdvancedFastingReminderService;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/fasting/advanced/reminder-settings")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Advanced Fasting Reminders", description = "User-specific advanced fasting reminder preferences.")
public class AdvancedFastingReminderSettingsController {
    private final AdvancedFastingReminderService reminderService;

    @GetMapping
    @Operation(summary = "Get advanced fasting reminder settings")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reminder settings returned."),
            @ApiResponse(responseCode = "403", description = "FASTING_ADVANCED is unavailable.", content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    })
    public ResponseEntity<AdvancedFastingReminderSettingsDto> get(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(reminderService.getSettings(user.getUsername()));
    }

    @PutMapping
    @Operation(summary = "Update advanced fasting reminder settings")
    public ResponseEntity<AdvancedFastingReminderSettingsDto> update(
            @AuthenticationPrincipal UserDetails user,
            @RequestBody @Valid AdvancedFastingReminderSettingsDto request) {
        return ResponseEntity.ok(reminderService.updateSettings(user.getUsername(), request));
    }
}