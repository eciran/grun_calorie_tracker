package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AccountLinkAuthorizationRequestDto;
import com.grun.calorietracker.dto.AccountLinkAuthorizationResponseDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.AccountLinkPurpose;
import com.grun.calorietracker.enums.AuthProvider;

public interface AccountLinkAuthorizationService {
    AccountLinkAuthorizationResponseDto createAuthorization(String userEmail, AccountLinkAuthorizationRequestDto request);

    void consumeAuthorization(UserEntity user, String rawToken, AccountLinkPurpose purpose, AuthProvider targetProvider);
}
