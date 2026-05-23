package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AuthResponse;

public interface FederatedAuthService {
    AuthResponse loginWithGoogle(String idToken);

    AuthResponse loginWithApple(String idToken, String nonce);
}
