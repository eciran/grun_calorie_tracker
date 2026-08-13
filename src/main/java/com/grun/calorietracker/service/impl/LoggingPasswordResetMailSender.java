package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.exception.MailDeliveryException;
import com.grun.calorietracker.service.PasswordResetMailSender;
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
public class LoggingPasswordResetMailSender implements PasswordResetMailSender {

    private final MailDeliveryService mailDeliveryService;
    private final MailFailureAlertService mailFailureAlertService;
    private final UserRepository userRepository;
    private final MailProperties mailProperties;

    @Override
    public void sendPasswordResetToken(String email, String rawToken, String resetLink) {
        log.info("Password reset email requested for email={}", email);
        try {
            PreferredLanguage language = userRepository.findByEmail(email)
                    .map(user -> user.getPreferredLanguage()).orElse(PreferredLanguage.EN);
            boolean turkish = language == PreferredLanguage.TR;
            String subject = turkish ? "GRun şifreni sıfırla" : "Reset your GRun password";
            String text = turkish
                    ? "GRun şifreni sıfırlamak için bu bağlantıyı kullan: " + resetLink
                    : "Use this link to reset your GRun password: " + resetLink;
            String html = turkish
                    ? "<p>GRun şifreni sıfırlamak için aşağıdaki bağlantıyı kullan.</p><p><a href=\"%s\">Şifremi sıfırla</a></p><p>Bu isteği sen yapmadıysan bu e-postayı yok sayabilirsin.</p>".formatted(resetLink)
                    : "<p>Use the link below to reset your GRun password.</p><p><a href=\"%s\">Reset password</a></p><p>If you did not request this, you can ignore this email.</p>".formatted(resetLink);
            long templateId = turkish
                    ? mailProperties.getBrevo().getTemplates().getPasswordResetTr()
                    : mailProperties.getBrevo().getTemplates().getPasswordResetEn();
            mailDeliveryService.sendTransactionalTemplate(
                    email,
                    templateId,
                    java.util.Map.of("resetUrl", resetLink),
                    subject, text, html
            );
        } catch (MailDeliveryException ex) {
            mailFailureAlertService.notifyAdminForProviderFailure("PASSWORD_RESET", email, ex.getMessage());
            throw ex;
        }
    }
}
