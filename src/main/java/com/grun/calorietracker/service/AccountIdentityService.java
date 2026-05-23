package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AccountPasswordRequestDto;
import com.grun.calorietracker.dto.AccountPasswordResponseDto;
import com.grun.calorietracker.dto.LinkedIdentityDto;
import com.grun.calorietracker.enums.AuthProvider;

import java.util.List;

public interface AccountIdentityService {
    List<LinkedIdentityDto> listLinkedIdentities(String userEmail);

    LinkedIdentityDto linkGoogle(String userEmail, String idToken);

    LinkedIdentityDto linkApple(String userEmail, String idToken, String nonce);

    void unlinkProvider(String userEmail, AuthProvider provider);

    AccountPasswordResponseDto updatePassword(String userEmail, AccountPasswordRequestDto request);
}
