package com.grun.calorietracker.service;

import com.grun.calorietracker.service.impl.LoggingPasswordResetMailSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class LoggingPasswordResetMailSenderTest {

    @Mock
    private MailDeliveryService mailDeliveryService;

    @Test
    void sendPasswordResetTokenDelegatesToMailDeliveryService() {
        LoggingPasswordResetMailSender sender = new LoggingPasswordResetMailSender(mailDeliveryService);

        sender.sendPasswordResetToken(
                "user@example.com",
                "raw-token",
                "https://app.grun.local/reset?token=raw-token"
        );

        verify(mailDeliveryService).sendTransactionalEmail(
                eq("user@example.com"),
                eq("Reset your GRun password"),
                eq("Use this link to reset your GRun password: https://app.grun.local/reset?token=raw-token"),
                contains("Reset password")
        );
    }
}
