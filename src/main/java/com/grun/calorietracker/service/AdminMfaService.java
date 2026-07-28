package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.*;
import com.grun.calorietracker.entity.UserEntity;

public interface AdminMfaService {
    AdminMfaStatusDto status(String email);
    AdminMfaEnrollmentDto beginEnrollment(String email, String currentPassword, String correlationId);
    AdminMfaVerificationDto verifyEnrollment(String email, String code, String correlationId);
    AdminMfaStatusDto disable(String email, String code, String correlationId);
    AdminReauthenticationDto reauthenticate(String email, String code, String correlationId);
    void verifyLogin(UserEntity user, String code);
}