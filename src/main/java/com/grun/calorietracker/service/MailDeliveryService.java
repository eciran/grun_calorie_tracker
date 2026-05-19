package com.grun.calorietracker.service;

public interface MailDeliveryService {

    void sendTransactionalEmail(String recipientEmail, String subject, String textBody);
}
