package com.grun.calorietracker.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.grun.calorietracker.config.MailProperties;
import com.grun.calorietracker.dto.AdminBrevoSenderDto;
import com.grun.calorietracker.dto.AdminBrevoSenderListDto;
import com.grun.calorietracker.dto.AdminBrevoSenderRequestDto;
import com.grun.calorietracker.enums.MailProvider;
import com.grun.calorietracker.service.AdminBrevoSenderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BrevoAdminSenderService implements AdminBrevoSenderService {

    private final RestClient.Builder restClientBuilder;
    private final MailProperties mailProperties;

    @Override
    public AdminBrevoSenderListDto getSenders() {
        AdminBrevoSenderListDto dto = new AdminBrevoSenderListDto();
        dto.setSenders(List.of());
        if (!brevoReady(dto)) {
            return dto;
        }
        try {
            JsonNode response = get(sendersUri());
            dto.setSenders(readSenders(response));
            dto.setProviderReachable(true);
            dto.setStatusMessage("Brevo senders fetched.");
        } catch (RestClientException ex) {
            dto.setProviderReachable(false);
            dto.setStatusMessage("Brevo senders request failed: " + safeMessage(ex));
        }
        return dto;
    }

    @Override
    public AdminBrevoSenderDto createSender(AdminBrevoSenderRequestDto request) {
        ensureBrevoConfigured();
        JsonNode response = restClientBuilder.build()
                .post()
                .uri(sendersUri())
                .header("api-key", mailProperties.getBrevo().getApiKey())
                .header("accept", "application/json")
                .body(senderPayload(request))
                .retrieve()
                .body(JsonNode.class);
        AdminBrevoSenderDto dto = readSender(response);
        dto.setName(request.getName());
        dto.setEmail(request.getEmail());
        return dto;
    }

    @Override
    public AdminBrevoSenderDto updateSender(Long senderId, AdminBrevoSenderRequestDto request) {
        ensureBrevoConfigured();
        restClientBuilder.build()
                .put()
                .uri(senderUri(senderId))
                .header("api-key", mailProperties.getBrevo().getApiKey())
                .header("accept", "application/json")
                .body(senderPayload(request))
                .retrieve()
                .toBodilessEntity();
        AdminBrevoSenderDto dto = new AdminBrevoSenderDto();
        dto.setId(senderId);
        dto.setName(request.getName());
        dto.setEmail(request.getEmail());
        return dto;
    }

    private boolean brevoReady(AdminBrevoSenderListDto dto) {
        if (mailProperties.getProvider() != MailProvider.BREVO) {
            dto.setProviderReachable(false);
            dto.setStatusMessage("Mail provider is not BREVO. Brevo sender management is disabled.");
            return false;
        }
        if (mailProperties.getBrevo().getApiKey() == null || mailProperties.getBrevo().getApiKey().isBlank()) {
            dto.setProviderReachable(false);
            dto.setStatusMessage("Brevo API key is not configured.");
            return false;
        }
        return true;
    }

    private void ensureBrevoConfigured() {
        if (mailProperties.getProvider() != MailProvider.BREVO) {
            throw new IllegalStateException("Mail provider is not BREVO.");
        }
        if (mailProperties.getBrevo().getApiKey() == null || mailProperties.getBrevo().getApiKey().isBlank()) {
            throw new IllegalStateException("Brevo API key is not configured.");
        }
    }

    private JsonNode get(URI uri) {
        return restClientBuilder.build()
                .get()
                .uri(uri)
                .header("api-key", mailProperties.getBrevo().getApiKey())
                .header("accept", "application/json")
                .retrieve()
                .body(JsonNode.class);
    }

    private Map<String, Object> senderPayload(AdminBrevoSenderRequestDto request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", request.getName());
        payload.put("email", request.getEmail());
        return payload;
    }

    private List<AdminBrevoSenderDto> readSenders(JsonNode response) {
        JsonNode rows = response == null ? null : firstArray(response, "senders", "items");
        if (rows == null || !rows.isArray()) {
            return List.of();
        }
        List<AdminBrevoSenderDto> result = new ArrayList<>();
        for (JsonNode row : rows) {
            result.add(readSender(row));
        }
        return result;
    }

    private JsonNode firstArray(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode candidate = node.path(field);
            if (candidate.isArray()) {
                return candidate;
            }
        }
        return null;
    }

    private AdminBrevoSenderDto readSender(JsonNode node) {
        AdminBrevoSenderDto dto = new AdminBrevoSenderDto();
        dto.setId(readLong(node, "id"));
        dto.setName(readText(node, "name"));
        dto.setEmail(readText(node, "email"));
        dto.setActive(readBoolean(node, "active"));
        dto.setDkimError(readBoolean(node, "dkimError"));
        dto.setSpfError(readBoolean(node, "spfError"));
        dto.setIps(readIps(node == null ? null : node.path("ips")));
        return dto;
    }

    private List<Map<String, Object>> readIps(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonNode ip : node) {
            Map<String, Object> item = new LinkedHashMap<>();
            ip.fields().forEachRemaining(entry -> item.put(entry.getKey(), jsonValue(entry.getValue())));
            result.add(item);
        }
        return result;
    }

    private Object jsonValue(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isBoolean()) return node.asBoolean();
        if (node.isNumber()) return node.numberValue();
        return node.asText();
    }

    private Long readLong(JsonNode node, String field) {
        if (node == null || node.path(field).isMissingNode() || node.path(field).isNull()) {
            return null;
        }
        return node.path(field).asLong();
    }

    private Boolean readBoolean(JsonNode node, String field) {
        if (node == null || node.path(field).isMissingNode() || node.path(field).isNull()) {
            return null;
        }
        return node.path(field).asBoolean();
    }

    private String readText(JsonNode node, String field) {
        if (node == null || node.path(field).isMissingNode() || node.path(field).isNull()) {
            return null;
        }
        String value = node.path(field).asText();
        return value == null || value.isBlank() ? null : value;
    }

    private URI sendersUri() {
        return UriComponentsBuilder.fromHttpUrl(providerBaseUrl())
                .path("/v3/senders")
                .build()
                .toUri();
    }

    private URI senderUri(Long senderId) {
        return UriComponentsBuilder.fromHttpUrl(providerBaseUrl())
                .pathSegment("v3", "senders", String.valueOf(senderId))
                .build()
                .toUri();
    }

    private String providerBaseUrl() {
        String apiUrl = mailProperties.getBrevo().getApiUrl();
        if (apiUrl == null || apiUrl.isBlank()) {
            return "https://api.brevo.com";
        }
        int marker = apiUrl.indexOf("/v3/");
        return marker > 0 ? apiUrl.substring(0, marker) : apiUrl;
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message.length() > 240 ? message.substring(0, 240) : message;
    }
}
