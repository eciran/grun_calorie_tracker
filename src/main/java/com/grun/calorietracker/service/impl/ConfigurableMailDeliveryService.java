package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.service.MailDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigurableMailDeliveryService implements MailDeliveryService {

    private final RestClient.Builder restClientBuilder;

    @Value("${grun.mail.provider:LOG}")
    private String provider;

    @Value("${grun.mail.from-email:no-reply@grun.local}")
    private String fromEmail;

    @Value("${grun.mail.from-name:GRun}")
    private String fromName;

    @Value("${grun.mail.brevo.api-key:}")
    private String brevoApiKey;

    @Value("${grun.mail.brevo.api-url:https://api.brevo.com/v3/smtp/email}")
    private String brevoApiUrl;

    @Override
    public void sendTransactionalEmail(String recipientEmail, String subject, String textBody) {
        if ("BREVO".equalsIgnoreCase(provider)) {
            sendWithBrevo(recipientEmail, subject, textBody);
            return;
        }

        log.info("Transactional email provider=LOG, to={}, subject={}, body={}", recipientEmail, subject, textBody);
    }

    private void sendWithBrevo(String recipientEmail, String subject, String textBody) {
        if (brevoApiKey == null || brevoApiKey.isBlank()) {
            throw new IllegalStateException("Brevo API key is required when grun.mail.provider=BREVO");
        }

        Map<String, Object> payload = Map.of(
                "sender", Map.of("email", fromEmail, "name", fromName),
                "to", List.of(Map.of("email", recipientEmail)),
                "subject", subject,
                "textContent", textBody
        );

        restClientBuilder.build()
                .post()
                .uri(brevoApiUrl)
                .header("api-key", brevoApiKey)
                .header("Content-Type", "application/json")
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }
}
