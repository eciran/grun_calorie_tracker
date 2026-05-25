package com.grun.calorietracker.service;

public interface MailDeliveryService {

    default void sendTransactionalEmail(String recipientEmail, String subject, String textBody) {
        sendTransactionalEmail(recipientEmail, subject, textBody, null);
    }

    void sendTransactionalEmail(String recipientEmail, String subject, String textBody, String htmlBody);
}
