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
import java.util.Map;

@Component
@Slf4j
public class ExpoPushProviderClient implements PushProviderClient {

    private final PushProperties properties;
    private final RestTemplate restTemplate;

    public ExpoPushProviderClient(PushProperties properties, RestTemplateBuilder restTemplateBuilder) {
        this.properties = properties;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public PushProvider provider() {
        return PushProvider.EXPO;
    }

    @Override
    public PushProviderSendResult send(UserPushTokenEntity token, NotificationEntity notification) {
        if (properties.getExpo().getUrl() == null || properties.getExpo().getUrl().isBlank()) {
            return PushProviderSendResult.failed("Expo push URL is not configured.");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (properties.getExpo().getAccessToken() != null && !properties.getExpo().getAccessToken().isBlank()) {
            headers.setBearerAuth(properties.getExpo().getAccessToken());
        }
        Map<String, Object> body = Map.of(
                "to", token.getTokenValue(),
                "title", resolveTitle(notification),
                "body", notification.getMessage(),
                "data", Map.of(
                        "notificationId", notification.getId(),
                        "type", notification.getType()
                )
        );
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    properties.getExpo().getUrl(),
                    new HttpEntity<>(body, headers),
                    Map.class
            );
            String status = extractStatus(response);
            if ("ok".equalsIgnoreCase(status)) {
                return PushProviderSendResult.sent(extractId(response));
            }
            String error = response == null ? "Expo push request returned an empty response." : response.toString();
            if (error.contains("DeviceNotRegistered")) {
                return PushProviderSendResult.invalidToken(error);
            }
            return PushProviderSendResult.failed(error);
        } catch (RestClientException ex) {
            log.warn("expo_push_failed tokenId={} notificationId={} reason={}", token.getId(), notification.getId(), ex.getMessage());
            return PushProviderSendResult.failed(ex.getMessage());
        }
    }

    private String resolveTitle(NotificationEntity notification) {
        return notification.getType() == null ? "GRun" : "GRun " + notification.getType().replace('_', ' ');
    }

    @SuppressWarnings("unchecked")
    private String extractStatus(Map<String, Object> response) {
        Object data = response == null ? null : response.get("data");
        if (data instanceof Map<?, ?> map) {
            Object status = map.get("status");
            return status == null ? null : status.toString();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String extractId(Map<String, Object> response) {
        Object data = response == null ? null : response.get("data");
        if (data instanceof Map<?, ?> map) {
            Object id = map.get("id");
            return id == null ? null : id.toString();
        }
        return null;
    }
}
