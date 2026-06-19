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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

@Component
@Slf4j
public class FcmPushProviderClient implements PushProviderClient {

    private final PushProperties properties;
    private final FcmAccessTokenProvider accessTokenProvider;
    private final RestTemplate restTemplate;

    public FcmPushProviderClient(PushProperties properties,
                                 FcmAccessTokenProvider accessTokenProvider,
                                 RestTemplateBuilder restTemplateBuilder) {
        this.properties = properties;
        this.accessTokenProvider = accessTokenProvider;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public PushProvider provider() {
        return PushProvider.FCM;
    }

    @Override
    public PushProviderSendResult send(UserPushTokenEntity token, NotificationEntity notification) {
        String projectId = properties.getFcm().getProjectId();
        String accessToken;
        try {
            accessToken = accessTokenProvider.getAccessToken();
        } catch (RuntimeException ex) {
            log.warn("fcm_access_token_failed tokenId={} notificationId={} reason={}",
                    token.getId(), notification.getId(), ex.getMessage());
            return PushProviderSendResult.failed(ex.getMessage());
        }
        if (isBlank(projectId) || isBlank(accessToken)) {
            return PushProviderSendResult.failed("FCM project id or credentials are not configured.");
        }
        String url = "https://fcm.googleapis.com/v1/projects/" + projectId + "/messages:send";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        Map<String, Object> body = Map.of(
                "message", Map.of(
                        "token", token.getTokenValue(),
                        "notification", Map.of(
                                "title", "GRun",
                                "body", notification.getMessage()
                        ),
                        "data", Map.of(
                                "notificationId", String.valueOf(notification.getId()),
                                "type", notification.getType() == null ? "" : notification.getType()
                        )
                )
        );
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, new HttpEntity<>(body, headers), Map.class);
            Object name = response == null ? null : response.get("name");
            return PushProviderSendResult.sent(name == null ? null : name.toString());
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.BadRequest ex) {
            String bodyText = ex.getResponseBodyAsString();
            if (bodyText.contains("UNREGISTERED") || bodyText.contains("registration-token-not-registered")) {
                return PushProviderSendResult.invalidToken(bodyText);
            }
            return PushProviderSendResult.failed(bodyText);
        } catch (RestClientException ex) {
            log.warn("fcm_push_failed tokenId={} notificationId={} reason={}", token.getId(), notification.getId(), ex.getMessage());
            return PushProviderSendResult.failed(ex.getMessage());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
