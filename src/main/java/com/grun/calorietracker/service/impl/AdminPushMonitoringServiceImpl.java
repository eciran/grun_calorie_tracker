package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.config.PushProperties;
import com.grun.calorietracker.dto.AdminPushMonitoringDto;
import com.grun.calorietracker.enums.PushDeliveryStatus;
import com.grun.calorietracker.enums.PushProvider;
import com.grun.calorietracker.repository.PushDeliveryLogRepository;
import com.grun.calorietracker.repository.UserPushTokenRepository;
import com.grun.calorietracker.service.AdminPushMonitoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminPushMonitoringServiceImpl implements AdminPushMonitoringService {

    private final PushProperties pushProperties;
    private final UserPushTokenRepository userPushTokenRepository;
    private final PushDeliveryLogRepository pushDeliveryLogRepository;

    @Override
    @Transactional(readOnly = true)
    public AdminPushMonitoringDto getMonitoring() {
        Map<PushProvider, Long> byProvider = new EnumMap<>(PushProvider.class);
        for (PushProvider provider : PushProvider.values()) {
            byProvider.put(provider, userPushTokenRepository.countByProviderAndEnabledTrue(provider));
        }
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        return new AdminPushMonitoringDto(
                pushProperties.isEnabled(),
                pushProperties.getProvider(),
                userPushTokenRepository.countByEnabledTrue(),
                byProvider,
                pushDeliveryLogRepository.countByStatusAndCreatedAtAfter(PushDeliveryStatus.SENT, since),
                pushDeliveryLogRepository.countByStatusAndCreatedAtAfter(PushDeliveryStatus.FAILED, since),
                !isBlank(pushProperties.getExpo().getUrl()),
                !isBlank(pushProperties.getFcm().getProjectId()) && !isBlank(pushProperties.getFcm().getAccessToken()),
                !isBlank(pushProperties.getOnesignal().getAppId()) && !isBlank(pushProperties.getOnesignal().getApiKey())
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
