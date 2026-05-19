package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.service.PasswordResetMailSender;
import com.grun.calorietracker.service.MailDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoggingPasswordResetMailSender implements PasswordResetMailSender {

    private final MailDeliveryService mailDeliveryService;

    @Override
    public void sendPasswordResetToken(String email, String rawToken, String resetLink) {
        log.info("Password reset requested for email={}, resetLink={}, token={}", email, resetLink, rawToken);
        mailDeliveryService.sendTransactionalEmail(
                email,
                "Reset your GRun password",
                "Use this link to reset your GRun password: " + resetLink
        );
    }
}
