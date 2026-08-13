package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.config.MailProperties;
import com.grun.calorietracker.enums.MailProvider;
import com.grun.calorietracker.exception.MailDeliveryException;
import com.grun.calorietracker.service.MailDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigurableMailDeliveryService implements MailDeliveryService {

    private static final Pattern SENSITIVE_QUERY_PARAM =
            Pattern.compile("(?i)([?&](?:token|code|state)=)[^&\\s\"'<>]+");

    private final RestClient.Builder restClientBuilder;
    private final MailProperties mailProperties;

    @Override
    public void sendTransactionalEmail(String recipientEmail, String subject, String textBody, String htmlBody) {
        if (mailProperties.getProvider() == MailProvider.BREVO) {
            sendWithBrevo(recipientEmail, subject, textBody, htmlBody);
            return;
        }

        log.info(
                "Transactional email provider=LOG, to={}, subject={}, textBody={}, htmlBody={}",
                recipientEmail,
                subject,
                sanitizeForLog(textBody),
                sanitizeForLog(htmlBody)
        );
    }

    @Override
    public void sendTransactionalTemplate(String recipientEmail, long templateId, Map<String, Object> parameters,
                                          String fallbackSubject, String fallbackText, String fallbackHtml) {
        if (mailProperties.getProvider() != MailProvider.BREVO) {
            sendTransactionalEmail(recipientEmail, fallbackSubject, fallbackText, fallbackHtml);
            return;
        }
        if (templateId <= 0) {
            throw new IllegalStateException("A positive Brevo template ID is required for transactional template email");
        }
        sendTemplateWithBrevo(recipientEmail, templateId, parameters);
    }

    private String sanitizeForLog(String value) {
        if (value == null) {
            return null;
        }
        return SENSITIVE_QUERY_PARAM.matcher(value).replaceAll("$1[REDACTED]");
    }

    private void sendWithBrevo(String recipientEmail, String subject, String textBody, String htmlBody) {
        MailProperties.Brevo brevo = mailProperties.getBrevo();
        if (brevo.getApiKey() == null || brevo.getApiKey().isBlank()) {
            throw new IllegalStateException("Brevo API key is required when grun.mail.provider=BREVO");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sender", Map.of("email", mailProperties.getFromEmail(), "name", mailProperties.getFromName()));
        payload.put("to", List.of(Map.of("email", recipientEmail)));
        payload.put("subject", subject);
        payload.put("textContent", textBody);
        if (htmlBody != null && !htmlBody.isBlank()) {
            payload.put("htmlContent", htmlBody);
        }

        try {
            restClientBuilder.build()
                    .post()
                    .uri(brevo.getApiUrl())
                    .header("api-key", brevo.getApiKey())
                    .header("accept", "application/json")
                    .header("Content-Type", "application/json")
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw new MailDeliveryException("Brevo rejected transactional email request with status " + ex.getStatusCode(), ex);
        } catch (RestClientException ex) {
            throw new MailDeliveryException("Brevo transactional email request failed", ex);
        }
    }

    private void sendTemplateWithBrevo(String recipientEmail, long templateId, Map<String, Object> parameters) {
        MailProperties.Brevo brevo = mailProperties.getBrevo();
        requireBrevoApiKey(brevo);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sender", Map.of("email", mailProperties.getFromEmail(), "name", mailProperties.getFromName()));
        payload.put("to", List.of(Map.of("email", recipientEmail)));
        payload.put("templateId", templateId);
        payload.put("params", parameters == null ? Map.of() : parameters);
        postToBrevo(brevo, payload);
    }

    private void requireBrevoApiKey(MailProperties.Brevo brevo) {
        if (brevo.getApiKey() == null || brevo.getApiKey().isBlank()) {
            throw new IllegalStateException("Brevo API key is required when grun.mail.provider=BREVO");
        }
    }

    private void postToBrevo(MailProperties.Brevo brevo, Map<String, Object> payload) {
        try {
            restClientBuilder.build().post().uri(brevo.getApiUrl())
                    .header("api-key", brevo.getApiKey()).header("accept", "application/json")
                    .header("Content-Type", "application/json").body(payload).retrieve().toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw new MailDeliveryException("Brevo rejected transactional email request with status " + ex.getStatusCode(), ex);
        } catch (RestClientException ex) {
            throw new MailDeliveryException("Brevo transactional email request failed", ex);
        }
    }
}
