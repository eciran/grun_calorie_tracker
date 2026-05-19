package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.service.EmailVerificationMailSender;
import com.grun.calorietracker.service.MailDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoggingEmailVerificationMailSender implements EmailVerificationMailSender {

    private final MailDeliveryService mailDeliveryService;

    @Override
    public void sendEmailVerificationToken(String email, String rawToken, String verificationLink) {
        log.info("Email verification requested for email={}, verificationLink={}, token={}", email, verificationLink, rawToken);
        mailDeliveryService.sendTransactionalEmail(
                email,
                "Verify your GRun email",
                "Use this link to verify your GRun email: " + verificationLink
        );
    }
}
