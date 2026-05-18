package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.service.EmailVerificationMailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LoggingEmailVerificationMailSender implements EmailVerificationMailSender {

    @Override
    public void sendEmailVerificationToken(String email, String rawToken, String verificationLink) {
        log.info("Email verification requested for email={}, verificationLink={}, token={}", email, verificationLink, rawToken);
    }
}
