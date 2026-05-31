package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.GdprDataExportDto;

public interface AccountGdprService {
    GdprDataExportDto exportMyData(String userEmail);

    void anonymizeAndDeleteAccount(String userEmail, String confirmText, String currentPassword);
}
