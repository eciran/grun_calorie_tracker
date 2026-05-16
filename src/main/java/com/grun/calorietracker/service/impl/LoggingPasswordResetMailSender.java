package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.service.PasswordResetMailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LoggingPasswordResetMailSender implements PasswordResetMailSender {

    @Override
    public void sendPasswordResetToken(String email, String rawToken, String resetLink) {
        log.info("Password reset requested for email={}, resetLink={}, token={}", email, resetLink, rawToken);
    }
}
