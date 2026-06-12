package com.grun.calorietracker.service.push;

import com.grun.calorietracker.config.PushProperties;
import com.grun.calorietracker.entity.NotificationEntity;
import com.grun.calorietracker.entity.UserPushTokenEntity;
import com.grun.calorietracker.enums.PushProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class OneSignalPushProviderClient implements PushProviderClient {

    private static final String ONESIGNAL_NOTIFICATIONS_URL = "https://onesignal.com/api/v1/notifications";

    private final PushProperties properties;
    private final RestTemplate restTemplate;

    public OneSignalPushProviderClient(PushProperties properties, RestTemplateBuilder restTemplateBuilder) {
        this.properties = properties;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public PushProvider provider() {
        return PushProvider.ONESIGNAL;
    }

    @Override
    public PushProviderSendResult send(UserPushTokenEntity token, NotificationEntity notification) {
        if (isBlank(properties.getOnesignal().getAppId()) || isBlank(properties.getOnesignal().getApiKey())) {
            return PushProviderSendResult.failed("OneSignal app id or api key is not configured.");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Basic " + properties.getOnesignal().getApiKey());
        Map<String, Object> body = Map.of(
                "app_id", properties.getOnesignal().getAppId(),
                "include_player_ids", List.of(token.getTokenValue()),
                "headings", Map.of("en", "GRun"),
                "contents", Map.of("en", notification.getMessage()),
                "data", Map.of(
                        "notificationId", notification.getId(),
                        "type", notification.getType()
                )
        );
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    ONESIGNAL_NOTIFICATIONS_URL,
                    new HttpEntity<>(body, headers),
                    Map.class
            );
            Object id = response == null ? null : response.get("id");
            return PushProviderSendResult.sent(id == null ? null : id.toString());
        } catch (RestClientException ex) {
            log.warn("onesignal_push_failed tokenId={} notificationId={} reason={}", token.getId(), notification.getId(), ex.getMessage());
            return PushProviderSendResult.failed(ex.getMessage());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
