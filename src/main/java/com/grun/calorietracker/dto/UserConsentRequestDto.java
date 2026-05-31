package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.LegalConsentStatus;
import com.grun.calorietracker.enums.LegalConsentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserConsentRequestDto {

    @NotNull
    @Schema(description = "Legal consent category.", example = "PRIVACY_POLICY")
    private LegalConsentType consentType;

    @NotBlank
    @Schema(description = "Version of the legal text accepted or revoked.", example = "privacy-2026-05")
    private String version;

    @NotNull
    @Schema(description = "Consent decision.", example = "ACCEPTED")
    private LegalConsentStatus status;

    @Schema(description = "Client surface that collected the consent.", example = "MOBILE_ONBOARDING")
    private String source;
}
