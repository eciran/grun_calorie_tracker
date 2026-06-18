package com.grun.calorietracker.service.push;

import com.grun.calorietracker.entity.NotificationEntity;
import com.grun.calorietracker.entity.UserPushTokenEntity;
import com.grun.calorietracker.enums.PushProvider;

public interface PushProviderClient {
    PushProvider provider();
    PushProviderSendResult send(UserPushTokenEntity token, NotificationEntity notification);
}
