package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.config.PushProperties;
import com.grun.calorietracker.dto.PushDeliveryResultDto;
import com.grun.calorietracker.entity.NotificationEntity;
import com.grun.calorietracker.entity.PushDeliveryLogEntity;
import com.grun.calorietracker.entity.UserPushTokenEntity;
import com.grun.calorietracker.enums.PushDeliveryStatus;
import com.grun.calorietracker.repository.PushDeliveryLogRepository;
import com.grun.calorietracker.repository.UserPushTokenRepository;
import com.grun.calorietracker.service.PushDeliveryService;
import com.grun.calorietracker.service.push.PushProviderClient;
import com.grun.calorietracker.service.push.PushProviderSendResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PushDeliveryServiceImpl implements PushDeliveryService {

    private final PushProperties pushProperties;
    private final UserPushTokenRepository userPushTokenRepository;
    private final PushDeliveryLogRepository pushDeliveryLogRepository;
    private final List<PushProviderClient> clients;

    @Override
    @Transactional
    public PushDeliveryResultDto deliver(NotificationEntity notification) {
        if (notification == null || notification.getUser() == null) {
            return new PushDeliveryResultDto(0, 0, 0, 0);
        }
        if (!pushProperties.isEnabled()
                || !Boolean.TRUE.equals(notification.getUser().getPushNotificationsEnabled())) {
            return new PushDeliveryResultDto(0, 0, 1, 0);
        }

        Map<com.grun.calorietracker.enums.PushProvider, PushProviderClient> byProvider = new EnumMap<>(com.grun.calorietracker.enums.PushProvider.class);
        clients.forEach(client -> byProvider.put(client.provider(), client));
        PushProviderClient client = byProvider.get(pushProperties.getProvider());
        if (client == null) {
            return new PushDeliveryResultDto(0, 0, 0, 1);
        }

        List<UserPushTokenEntity> tokens = userPushTokenRepository.findByUserAndEnabledTrue(notification.getUser());
        int sent = 0;
        int failed = 0;
        for (UserPushTokenEntity token : tokens) {
            if (token.getProvider() != pushProperties.getProvider()) {
                continue;
            }
            PushProviderSendResult result = client.send(token, notification);
            if (result.invalidToken()) {
                token.setEnabled(false);
                token.setRevokedAt(java.time.LocalDateTime.now());
                userPushTokenRepository.save(token);
            }
            PushDeliveryLogEntity log = new PushDeliveryLogEntity();
            log.setNotification(notification);
            log.setPushToken(token);
            log.setProvider(pushProperties.getProvider());
            log.setStatus(result.sent() ? PushDeliveryStatus.SENT : PushDeliveryStatus.FAILED);
            log.setProviderMessageId(result.providerMessageId());
            log.setErrorMessage(result.errorMessage());
            pushDeliveryLogRepository.save(log);
            if (result.sent()) {
                sent++;
            } else {
                failed++;
            }
        }
        return new PushDeliveryResultDto(tokens.size(), sent, Math.max(0, tokens.size() - sent - failed), failed);
    }
}
