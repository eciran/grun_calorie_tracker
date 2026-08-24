package com.grun.calorietracker.service;

import com.grun.calorietracker.exception.MailDeliveryException;
import com.grun.calorietracker.service.impl.LoggingEmailVerificationMailSender;
import com.grun.calorietracker.config.MailProperties;
import com.grun.calorietracker.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyMap;

@ExtendWith(MockitoExtension.class)
class LoggingEmailVerificationMailSenderTest {

    @Mock
    private MailDeliveryService mailDeliveryService;

    @Mock
    private MailFailureAlertService mailFailureAlertService;
    @Mock private UserRepository userRepository;

    private MailProperties properties() {
        MailProperties properties = new MailProperties();
        properties.getBrevo().getTemplates().setEmailVerificationEn(101);
        return properties;
    }

    @Test
    void sendEmailVerificationTokenDelegatesToMailDeliveryService() {
        LoggingEmailVerificationMailSender sender = new LoggingEmailVerificationMailSender(mailDeliveryService, mailFailureAlertService, userRepository, properties());

        sender.sendEmailVerificationToken(
                "user@example.com",
                "raw-token",
                "https://app.grun.local/verify?token=raw-token"
        );

        verify(mailDeliveryService).sendTransactionalTemplate(
                eq("user@example.com"),
                eq(101L), anyMap(),
                eq("Verify your GRUN email"),
                eq("Use this link to verify your GRUN email: https://app.grun.local/verify?token=raw-token"),
                contains("Verify email")
        );
    }

    @Test
    void sendEmailVerificationToken_whenProviderFails_sendsAdminAlertAndRethrows() {
        LoggingEmailVerificationMailSender sender = new LoggingEmailVerificationMailSender(mailDeliveryService, mailFailureAlertService, userRepository, properties());
        doThrow(new MailDeliveryException("Brevo transactional email request failed"))
                .when(mailDeliveryService)
                .sendTransactionalTemplate(eq("user@example.com"), eq(101L), anyMap(), eq("Verify your GRUN email"), eq("Use this link to verify your GRUN email: https://app.grun.local/verify?token=raw-token"), contains("Verify email"));

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
