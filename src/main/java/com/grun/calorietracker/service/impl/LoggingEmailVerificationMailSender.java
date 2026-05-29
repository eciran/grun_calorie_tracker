package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.exception.MailDeliveryException;
import com.grun.calorietracker.service.EmailVerificationMailSender;
import com.grun.calorietracker.service.MailFailureAlertService;
import com.grun.calorietracker.service.MailDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoggingEmailVerificationMailSender implements EmailVerificationMailSender {

    private final MailDeliveryService mailDeliveryService;
    private final MailFailureAlertService mailFailureAlertService;

    @Override
    public void sendEmailVerificationToken(String email, String rawToken, String verificationLink) {
        log.info("Email verification email requested for email={}", email);
        try {
            mailDeliveryService.sendTransactionalEmail(
                    email,
                    "Verify your GRun email",
                    "Use this link to verify your GRun email: " + verificationLink,
                    """
                            <p>Use this link to verify your GRun email address:</p>
                            <p><a href="%s">Verify email</a></p>
                            <p>If you did not create a GRun account, you can ignore this email.</p>
                            """.formatted(verificationLink)
            );
        } catch (MailDeliveryException ex) {
            mailFailureAlertService.notifyAdminForProviderFailure("EMAIL_VERIFICATION", email, ex.getMessage());
            throw ex;
        }
    }
}
