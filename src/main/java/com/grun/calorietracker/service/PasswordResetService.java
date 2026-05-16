package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.PasswordResetConfirmRequestDto;
import com.grun.calorietracker.dto.PasswordResetRequestDto;
import com.grun.calorietracker.dto.PasswordResetResponseDto;

public interface PasswordResetService {

    PasswordResetResponseDto requestPasswordReset(PasswordResetRequestDto request);

    PasswordResetResponseDto confirmPasswordReset(PasswordResetConfirmRequestDto request);
}
