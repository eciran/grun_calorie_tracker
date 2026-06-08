package com.grun.calorietracker.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.grun.calorietracker.config.MailProperties;
import com.grun.calorietracker.dto.AdminMailEventDto;
import com.grun.calorietracker.dto.AdminMailMonitoringDto;
import com.grun.calorietracker.enums.MailProvider;
import com.grun.calorietracker.service.AdminMailMonitoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BrevoAdminMailMonitoringService implements AdminMailMonitoringService {

    private final RestClient.Builder restClientBuilder;
    private final MailProperties mailProperties;

    @Override
    public AdminMailMonitoringDto getMonitoring(int days, int limit) {
        int safeDays = clamp(days, 1, 30);
        int safeLimit = clamp(limit, 1, 50);
        String baseUrl = providerBaseUrl();

        AdminMailMonitoringDto dto = baseDto(baseUrl);
        if (mailProperties.getProvider() != MailProvider.BREVO) {
            dto.setProviderReachable(false);
            dto.setStatusMessage("Mail provider is not BREVO. Live Brevo metrics are disabled.");
            return dto;
        }
        if (!dto.isApiKeyConfigured()) {
            dto.setProviderReachable(false);
            dto.setStatusMessage("Brevo API key is not configured.");
            return dto;
        }

        try {
            JsonNode aggregate = restClientBuilder.build()
                    .get()
                    .uri(aggregatedReportUri(baseUrl, safeDays))
                    .header("api-key", mailProperties.getBrevo().getApiKey())
                    .header("accept", "application/json")
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode events = restClientBuilder.build()
                    .get()
                    .uri(eventsUri(baseUrl, safeDays, safeLimit))
                    .header("api-key", mailProperties.getBrevo().getApiKey())
                    .header("accept", "application/json")
                    .retrieve()
                    .body(JsonNode.class);
            dto.setCounters(readCounters(aggregate));
            dto.setRecentEvents(readEvents(events, safeLimit));
            dto.setProviderReachable(true);
            dto.setStatusMessage("Brevo monitoring data fetched.");
        } catch (RestClientException ex) {
            dto.setProviderReachable(false);
            dto.setStatusMessage("Brevo monitoring request failed: " + safeMessage(ex));
        }
        return dto;
    }

    private AdminMailMonitoringDto baseDto(String baseUrl) {
        AdminMailMonitoringDto dto = new AdminMailMonitoringDto();
        dto.setProvider(mailProperties.getProvider() == null ? null : mailProperties.getProvider().name());
        dto.setApiKeyConfigured(mailProperties.getBrevo().getApiKey() != null && !mailProperties.getBrevo().getApiKey().isBlank());
        dto.setProviderBaseUrl(baseUrl);
        dto.setFromEmail(mailProperties.getFromEmail());
        dto.setFromName(mailProperties.getFromName());
        dto.setCounters(new LinkedHashMap<>());
        dto.setRecentEvents(List.of());
        dto.setCheckedAt(LocalDateTime.now());
        return dto;
    }

    private URI aggregatedReportUri(String baseUrl, int days) {
        LocalDate endDate = monitoringEndDate();
        LocalDate startDate = monitoringStartDate(endDate, days);
        return UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path("/v3/smtp/statistics/aggregatedReport")
                .queryParam("startDate", startDate)
                .queryParam("endDate", endDate)
                .build()
                .toUri();
    }

    private URI eventsUri(String baseUrl, int days, int limit) {
        LocalDate endDate = monitoringEndDate();
        LocalDate startDate = monitoringStartDate(endDate, days);
        return UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path("/v3/smtp/statistics/events")
                .queryParam("startDate", startDate)
                .queryParam("endDate", endDate)
                .queryParam("limit", limit)
                .queryParam("offset", 0)
                .build()
                .toUri();
    }

    private LocalDate monitoringEndDate() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    private LocalDate monitoringStartDate(LocalDate endDate, int days) {
        return endDate.minusDays(days - 1L);
    }

    private Map<String, Long> readCounters(JsonNode aggregate) {
        Map<String, Long> counters = new LinkedHashMap<>();
        counters.put("requests", readLong(aggregate, "requests"));
        counters.put("delivered", readLong(aggregate, "delivered"));
        counters.put("hardBounces", readLong(aggregate, "hardBounces"));
        counters.put("softBounces", readLong(aggregate, "softBounces"));
        counters.put("blocked", readLong(aggregate, "blocked"));
        counters.put("spamReports", readLong(aggregate, "spamReports"));
        counters.put("opened", readLong(aggregate, "opened"));
        counters.put("clicked", readLong(aggregate, "clicked"));
        counters.put("unsubscribed", readLong(aggregate, "unsubscribed"));
        return counters;
    }

    private List<AdminMailEventDto> readEvents(JsonNode events, int limit) {
        JsonNode eventRows = events == null ? null : events.path("events");
        if (eventRows == null || !eventRows.isArray()) {
            return List.of();
        }
        List<AdminMailEventDto> result = new ArrayList<>();
        for (JsonNode event : eventRows) {
            if (result.size() >= limit) {
                break;
            }
            AdminMailEventDto dto = new AdminMailEventDto();
            dto.setEvent(readText(event, "event"));
            dto.setEmail(readText(event, "email"));
            dto.setSubject(readText(event, "subject"));
            dto.setMessageId(firstText(event, "messageId", "message-id", "uuid"));
            dto.setDate(firstText(event, "date", "ts", "timestamp"));
            dto.setReason(firstText(event, "reason", "message", "details"));
            result.add(dto);
        }
        return result;
    }

    private long readLong(JsonNode node, String field) {
        if (node == null || node.path(field).isMissingNode() || node.path(field).isNull()) {
            return 0L;
        }
        return node.path(field).asLong(0L);
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = readText(node, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String readText(JsonNode node, String field) {
        if (node == null || node.path(field).isMissingNode() || node.path(field).isNull()) {
            return null;
        }
        String value = node.path(field).asText();
        return value == null || value.isBlank() ? null : value;
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

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
