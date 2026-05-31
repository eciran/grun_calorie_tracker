package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.exception.MailDeliveryException;
import com.grun.calorietracker.service.PasswordResetMailSender;
import com.grun.calorietracker.service.MailFailureAlertService;
import com.grun.calorietracker.service.MailDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoggingPasswordResetMailSender implements PasswordResetMailSender {

    private final MailDeliveryService mailDeliveryService;
    private final MailFailureAlertService mailFailureAlertService;

    @Override
    public void sendPasswordResetToken(String email, String rawToken, String resetLink) {
        log.info("Password reset email requested for email={}", email);
        try {
            mailDeliveryService.sendTransactionalEmail(
                    email,
                    "Reset your GRun password",
                    "Use this link to reset your GRun password: " + resetLink,
                    """
                            <p>Use this link to reset your GRun password:</p>
                            <p><a href="%s">Reset password</a></p>
                            <p>If you did not request this, you can ignore this email.</p>
                            """.formatted(resetLink)
            );
        } catch (MailDeliveryException ex) {
            mailFailureAlertService.notifyAdminForProviderFailure("PASSWORD_RESET", email, ex.getMessage());
            throw ex;
        }
    }
}
