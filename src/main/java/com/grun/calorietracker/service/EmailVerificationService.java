package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.EmailVerificationConfirmRequestDto;
import com.grun.calorietracker.dto.EmailVerificationRequestDto;
import com.grun.calorietracker.dto.EmailVerificationResponseDto;
import com.grun.calorietracker.entity.UserEntity;

public interface EmailVerificationService {

    void createVerificationTokenForUser(UserEntity user);

    EmailVerificationResponseDto resendVerification(EmailVerificationRequestDto request);

    EmailVerificationResponseDto confirmVerification(EmailVerificationConfirmRequestDto request);
}
