package com.grun.calorietracker.service;

public interface EmailVerificationMailSender {

    void sendEmailVerificationToken(String email, String rawToken, String verificationLink);
}
