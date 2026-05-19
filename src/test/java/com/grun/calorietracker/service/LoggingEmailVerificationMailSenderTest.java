package com.grun.calorietracker.service;

import com.grun.calorietracker.service.impl.LoggingEmailVerificationMailSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LoggingEmailVerificationMailSenderTest {

    @Mock
    private MailDeliveryService mailDeliveryService;

    @Test
    void sendEmailVerificationTokenDelegatesToMailDeliveryService() {
        LoggingEmailVerificationMailSender sender = new LoggingEmailVerificationMailSender(mailDeliveryService);

        sender.sendEmailVerificationToken(
                "user@example.com",
                "raw-token",
                "https://app.grun.local/verify?token=raw-token"
        );

        verify(mailDeliveryService).sendTransactionalEmail(
                "user@example.com",
                "Verify your GRun email",
                "Use this link to verify your GRun email: https://app.grun.local/verify?token=raw-token"
        );
    }
}
