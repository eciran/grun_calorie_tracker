package com.grun.calorietracker.service.push;

import com.grun.calorietracker.entity.NotificationEntity;
import com.grun.calorietracker.entity.UserPushTokenEntity;
import com.grun.calorietracker.enums.PushProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LogPushProviderClient implements PushProviderClient {
    @Override
    public PushProvider provider() {
        return PushProvider.LOG;
    }

    @Override
    public PushProviderSendResult send(UserPushTokenEntity token, NotificationEntity notification) {
        log.info(
                "push_log provider={} userId={} tokenId={} notificationId={} type={} message={}",
                provider(),
                token.getUser() == null ? null : token.getUser().getId(),
                token.getId(),
                notification.getId(),
                notification.getType(),
                notification.getMessage()
        );
        return PushProviderSendResult.sent("log:" + notification.getId() + ":" + token.getId());
    }
}
