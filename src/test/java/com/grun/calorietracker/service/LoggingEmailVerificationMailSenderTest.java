package com.grun.calorietracker.service;

import com.grun.calorietracker.exception.MailDeliveryException;
import com.grun.calorietracker.service.impl.LoggingEmailVerificationMailSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class LoggingEmailVerificationMailSenderTest {

    @Mock
    private MailDeliveryService mailDeliveryService;

    @Mock
    private MailFailureAlertService mailFailureAlertService;

    @Test
    void sendEmailVerificationTokenDelegatesToMailDeliveryService() {
        LoggingEmailVerificationMailSender sender = new LoggingEmailVerificationMailSender(mailDeliveryService, mailFailureAlertService);

        sender.sendEmailVerificationToken(
                "user@example.com",
                "raw-token",
                "https://app.grun.local/verify?token=raw-token"
        );

        verify(mailDeliveryService).sendTransactionalEmail(
                eq("user@example.com"),
                eq("Verify your GRun email"),
                eq("Use this link to verify your GRun email: https://app.grun.local/verify?token=raw-token"),
                contains("Verify email")
        );
    }

    @Test
    void sendEmailVerificationToken_whenProviderFails_sendsAdminAlertAndRethrows() {
        LoggingEmailVerificationMailSender sender = new LoggingEmailVerificationMailSender(mailDeliveryService, mailFailureAlertService);
        doThrow(new MailDeliveryException("Brevo transactional email request failed"))
                .when(mailDeliveryService)
                .sendTransactionalEmail(eq("user@example.com"), eq("Verify your GRun email"), eq("Use this link to verify your GRun email: https://app.grun.local/verify?token=raw-token"), contains("Verify email"));

        assertThatThrownBy(() -> sender.sendEmailVerificationToken(
                "user@example.com",
                "raw-token",
                "https://app.grun.local/verify?token=raw-token"
        )).isInstanceOf(MailDeliveryException.class);

        verify(mailFailureAlertService).notifyAdminForProviderFailure(
                eq("EMAIL_VERIFICATION"),
                eq("user@example.com"),
                eq("Brevo transactional email request failed")
        );
    }
}
