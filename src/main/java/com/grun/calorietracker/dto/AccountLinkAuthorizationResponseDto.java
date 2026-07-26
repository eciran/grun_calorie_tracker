package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.AccountLinkPurpose;
import com.grun.calorietracker.enums.AuthProvider;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Short-lived, purpose-bound, one-time account authorization.")
public record AccountLinkAuthorizationResponseDto(
        String authorizationToken,
        AccountLinkPurpose purpose,
        AuthProvider targetProvider,
        LocalDateTime expiresAt
) {
}
