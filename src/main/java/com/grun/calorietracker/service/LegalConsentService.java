package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.UserConsentDto;
import com.grun.calorietracker.dto.UserConsentRequestDto;
import com.grun.calorietracker.enums.LegalConsentType;

import java.util.List;

public interface LegalConsentService {
    UserConsentDto recordConsent(String userEmail, UserConsentRequestDto request, String ipAddress, String userAgent);

    List<UserConsentDto> listMyConsents(String userEmail);

    boolean hasActiveConsent(String userEmail, LegalConsentType consentType);
}
