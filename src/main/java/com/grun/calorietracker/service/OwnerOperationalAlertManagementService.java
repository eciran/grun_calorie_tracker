package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.OwnerOperationalAlertDto;

public interface OwnerOperationalAlertManagementService {
    OwnerOperationalAlertDto retry(long id, String ownerEmail, String reauthToken, String reason, String correlationId);
    OwnerOperationalAlertDto acknowledge(long id, String ownerEmail, String reauthToken, String reason, String correlationId);
}
