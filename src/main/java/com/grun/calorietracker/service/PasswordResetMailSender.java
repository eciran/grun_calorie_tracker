package com.grun.calorietracker.service;

public interface PasswordResetMailSender {

    void sendPasswordResetToken(String email, String rawToken, String resetLink);
}
