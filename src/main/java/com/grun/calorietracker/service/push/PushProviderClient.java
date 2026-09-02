package com.grun.calorietracker.service.push;

import com.grun.calorietracker.entity.NotificationEntity;
import com.grun.calorietracker.entity.UserPushTokenEntity;
import com.grun.calorietracker.enums.PushProvider;

import java.time.Duration;

public interface PushProviderClient {
    PushProvider provider();
    PushProviderSendResult send(UserPushTokenEntity token, NotificationEntity notification);

    default PushProviderSendResult send(
            UserPushTokenEntity token,
            NotificationEntity notification,
            Duration timeToLive
    ) {
        if (timeToLive == null || timeToLive.isZero() || timeToLive.isNegative()) {
            return PushProviderSendResult.failed("Push TTL has expired");
        }
        return send(token, notification);
    }
}
