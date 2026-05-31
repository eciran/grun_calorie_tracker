package com.grun.calorietracker.dto;

import com.grun.calorietracker.enums.LegalConsentStatus;
import com.grun.calorietracker.enums.LegalConsentType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserConsentDto {
    private Long id;
    private LegalConsentType consentType;
    private String version;
    private LegalConsentStatus status;
    private String source;
    private LocalDateTime createdAt;
}
