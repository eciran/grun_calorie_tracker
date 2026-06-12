package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.PushDeliveryResultDto;
import com.grun.calorietracker.entity.NotificationEntity;

public interface PushDeliveryService {
    PushDeliveryResultDto deliver(NotificationEntity notification);
}
