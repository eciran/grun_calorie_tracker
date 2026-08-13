package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.exception.MailDeliveryException;
import com.grun.calorietracker.service.EmailVerificationMailSender;
import com.grun.calorietracker.service.MailFailureAlertService;
import com.grun.calorietracker.service.MailDeliveryService;
import com.grun.calorietracker.config.MailProperties;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoggingEmailVerificationMailSender implements EmailVerificationMailSender {

    private final MailDeliveryService mailDeliveryService;
    private final MailFailureAlertService mailFailureAlertService;
    private final UserRepository userRepository;
    private final MailProperties mailProperties;

    @Override
    public void sendEmailVerificationToken(String email, String rawToken, String verificationLink) {
        log.info("Email verification email requested for email={}", email);
        try {
            PreferredLanguage language = userRepository.findByEmail(email)
                    .map(user -> user.getPreferredLanguage()).orElse(PreferredLanguage.EN);
            boolean turkish = language == PreferredLanguage.TR;
            String subject = turkish ? "GRun e-posta adresini doğrula" : "Verify your GRun email";
            String text = turkish
                    ? "GRun e-posta adresini doğrulamak için bu bağlantıyı kullan: " + verificationLink
                    : "Use this link to verify your GRun email: " + verificationLink;
            String html = turkish
                    ? "<p>GRun e-posta adresini doğrulamak için aşağıdaki bağlantıyı kullan.</p><p><a href=\"%s\">E-posta adresimi doğrula</a></p><p>Bu hesabı sen oluşturmadıysan bu e-postayı yok sayabilirsin.</p>".formatted(verificationLink)
                    : "<p>Use the link below to verify your GRun email address.</p><p><a href=\"%s\">Verify email</a></p><p>If you did not create this account, you can ignore this email.</p>".formatted(verificationLink);
            long templateId = turkish
                    ? mailProperties.getBrevo().getTemplates().getEmailVerificationTr()
                    : mailProperties.getBrevo().getTemplates().getEmailVerificationEn();
            mailDeliveryService.sendTransactionalTemplate(
                    email,
                    templateId,
                    java.util.Map.of("verificationUrl", verificationLink),
                    subject, text, html
            );
        } catch (MailDeliveryException ex) {
            mailFailureAlertService.notifyAdminForProviderFailure("EMAIL_VERIFICATION", email, ex.getMessage());
            throw ex;
        }
    }
}
