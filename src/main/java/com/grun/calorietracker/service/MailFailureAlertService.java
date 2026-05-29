package com.grun.calorietracker.service;

public interface MailFailureAlertService {
    void notifyAdminForProviderFailure(String flowType, String recipientEmail, String errorMessage);
}
