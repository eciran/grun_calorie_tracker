package com.grun.calorietracker.service;

import java.util.Map;

public interface MailDeliveryService {

    default void sendTransactionalEmail(String recipientEmail, String subject, String textBody) {
        sendTransactionalEmail(recipientEmail, subject, textBody, null);
    }

    void sendTransactionalEmail(String recipientEmail, String subject, String textBody, String htmlBody);

    default void sendTransactionalTemplate(String recipientEmail,
                                           long templateId,
                                           Map<String, Object> parameters,
                                           String fallbackSubject,
                                           String fallbackText,
                                           String fallbackHtml) {
        sendTransactionalEmail(recipientEmail, fallbackSubject, fallbackText, fallbackHtml);
    }
}
