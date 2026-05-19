package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.AuthResponse;
import com.grun.calorietracker.entity.UserEntity;

public interface RefreshTokenService {

    String createRefreshToken(UserEntity user);

    AuthResponse refreshAccessToken(String rawRefreshToken);

    void revokeRefreshToken(String rawRefreshToken);

    void revokeAllForUser(UserEntity user);
}
