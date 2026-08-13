package com.grun.calorietracker.exception;

public class AdminMfaLoginException extends RuntimeException {
    private final String code;

    private AdminMfaLoginException(String code, String message) {
        super(message);
        this.code = code;
    }

    public static AdminMfaLoginException required() {
        return new AdminMfaLoginException(
                "ADMIN_MFA_REQUIRED",
                "Authenticator or recovery code is required."
        );
    }

    public static AdminMfaLoginException invalid() {
        return new AdminMfaLoginException(
                "ADMIN_MFA_INVALID",
                "Authenticator or recovery code is invalid."
        );
    }

    public String getCode() {
        return code;
    }
}
