package com.grun.calorietracker.dto;

public class AdminMfaEnrollmentAuthResponse extends AuthResponse {
    public AdminMfaEnrollmentAuthResponse(String token) {
        super(token, null, "Bearer", 300L, "Complete MFA enrollment before admin sign-in.");
    }

    public boolean isMfaEnrollmentRequired() {
        return true;
    }
}
