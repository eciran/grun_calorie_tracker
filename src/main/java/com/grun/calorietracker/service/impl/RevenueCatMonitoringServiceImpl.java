package com.grun.calorietracker.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.grun.calorietracker.config.RevenueCatProperties;
import com.grun.calorietracker.dto.RevenueCatChartDto;
import com.grun.calorietracker.dto.RevenueCatChartPointDto;
import com.grun.calorietracker.dto.RevenueCatMetricCardDto;
import com.grun.calorietracker.dto.RevenueCatMonitoringChartsDto;
import com.grun.calorietracker.dto.RevenueCatMonitoringOverviewDto;
import com.grun.calorietracker.enums.SubscriptionProviderEventStatus;
import com.grun.calorietracker.repository.SubscriptionProviderEventRepository;
import com.grun.calorietracker.service.RevenueCatMonitoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RevenueCatMonitoringServiceImpl implements RevenueCatMonitoringService {

    private static final List<String> OVERVIEW_KEYS = List.of(
            "active_trials",
            "active_subscriptions",
            "mrr",
            "revenue",
            "new_customers",
            "active_customers"
    );
    private static final List<String> CHART_NAMES = List.of(
            "revenue",
            "actives",
            "trials",
            "mrr",
            "customers_new",
            "customers_active"
    );

    private final RevenueCatProperties properties;
    private final RestClient.Builder restClientBuilder;
    private final SubscriptionProviderEventRepository eventRepository;

    @Override
    public RevenueCatMonitoringOverviewDto getOverview(String environment) {
        String safeEnvironment = normalizeEnvironment(environment);
        RevenueCatMonitoringOverviewDto dto = baseOverview(safeEnvironment);
        if ("sandbox".equals(safeEnvironment)) {
            applySandboxOverview(dto);
            return dto;
        }
        if (!readyForApi(dto)) {
            dto.setProviderReachable(false);
            dto.setStatusMessage("RevenueCat Metrics API is not configured.");
            return dto;
        }
        try {
            JsonNode response = restClientBuilder.build()
                    .get()
                    .uri(overviewUri())
                    .header("Authorization", "Bearer " + properties.getApi().getSecretKey())
                    .header("accept", "application/json")
                    .retrieve()
                    .body(JsonNode.class);
            dto.setMetrics(readOverviewMetrics(response));
            dto.setProviderReachable(true);
            dto.setStatusMessage("RevenueCat overview metrics fetched.");
        } catch (RestClientException ex) {
            dto.setProviderReachable(false);
            dto.setStatusMessage("RevenueCat overview request failed: " + monitoringErrorMessage(ex));
        }
        return dto;
    }

    @Override
    public RevenueCatMonitoringChartsDto getCharts(String environment, String range, String startDate, String endDate) {
        String safeEnvironment = normalizeEnvironment(environment);
        RevenueCatMonitoringChartsDto dto = new RevenueCatMonitoringChartsDto();
        dto.setEnvironment(safeEnvironment);
        dto.setCurrency(currency());
        dto.setCheckedAt(LocalDateTime.now());
        if ("sandbox".equals(safeEnvironment)) {
            dto.setProviderReachable(true);
            dto.setStatusMessage("Sandbox charts are based on stored webhook events. RevenueCat Charts API is production-only.");
            dto.setCharts(sandboxCharts());
            return dto;
        }
        if (!properties.getApi().isEnabled() || !hasText(properties.getApi().getSecretKey()) || !hasText(properties.getApi().getProjectId())) {
            dto.setProviderReachable(false);
            dto.setStatusMessage("RevenueCat Metrics API is not configured.");
            dto.setCharts(emptyCharts("production"));
            return dto;
        }
        List<RevenueCatChartDto> charts = new ArrayList<>();
        boolean reachable = true;
        String status = "RevenueCat charts fetched.";
        for (String chartName : CHART_NAMES) {
            try {
                JsonNode response = restClientBuilder.build()
                        .get()
                        .uri(chartUri(chartName, range, startDate, endDate))
                        .header("Authorization", "Bearer " + properties.getApi().getSecretKey())
                        .header("accept", "application/json")
                        .retrieve()
                        .body(JsonNode.class);
                charts.add(readChart(chartName, "production", response));
            } catch (RestClientException ex) {
                reachable = false;
                status = "RevenueCat chart request failed: " + monitoringErrorMessage(ex);
                charts.add(emptyChart(chartName, "production", status));
            }
        }
        dto.setProviderReachable(reachable);
        dto.setStatusMessage(status);
        dto.setCharts(charts);
        return dto;
    }

    private RevenueCatMonitoringOverviewDto baseOverview(String environment) {
        RevenueCatMonitoringOverviewDto dto = new RevenueCatMonitoringOverviewDto();
        dto.setEnvironment(environment);
        dto.setCurrency(currency());
        dto.setApiEnabled(properties.getApi().isEnabled());
        dto.setApiSecretConfigured(hasText(properties.getApi().getSecretKey()));
        dto.setApiProjectConfigured(hasText(properties.getApi().getProjectId()));
        dto.setCheckedAt(LocalDateTime.now());
        dto.setMetrics(defaultOverviewMetrics());
        return dto;
    }

    private boolean readyForApi(RevenueCatMonitoringOverviewDto dto) {
        return dto.isApiEnabled() && dto.isApiSecretConfigured() && dto.isApiProjectConfigured();
    }

    private URI overviewUri() {
        return UriComponentsBuilder.fromHttpUrl(baseUrl())
                .path("/projects/{projectId}/metrics/overview")
                .queryParam("currency", currency())
                .buildAndExpand(properties.getApi().getProjectId())
                .toUri();
    }

    private URI chartUri(String chartName, String range, String requestedStartDate, String requestedEndDate) {
        DateRange dateRange = resolveDateRange(range, requestedStartDate, requestedEndDate);
        return UriComponentsBuilder.fromHttpUrl(baseUrl())
                .path("/projects/{projectId}/charts/{chartName}")
                .queryParam("currency", currency())
                .queryParam("start_date", dateRange.startDate())
                .queryParam("end_date", dateRange.endDate())
                .queryParam("resolution", "day")
                .buildAndExpand(properties.getApi().getProjectId(), chartName)
                .toUri();
    }

    private List<RevenueCatMetricCardDto> readOverviewMetrics(JsonNode node) {
        List<RevenueCatMetricCardDto> metrics = new ArrayList<>();
        for (String key : OVERVIEW_KEYS) {
            metrics.add(metric(key, readMetricValue(node, key)));
        }
        return metrics;
    }

    private String readMetricValue(JsonNode node, String key) {
        JsonNode value = findMetricNode(node, key);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return "0";
        }
        if (value.isNumber() || value.isTextual() || value.isBoolean()) {
            return value.asText("0");
        }
        for (String field : List.of("value", "total", "current", "amount")) {
            if (value.has(field)) {
                return value.path(field).asText("0");
            }
        }
        return "0";
    }

    private JsonNode findMetricNode(JsonNode node, String key) {
        if (node == null) {
            return null;
        }
        for (String candidate : List.of(key, camelize(key), key.toUpperCase(Locale.ROOT))) {
            if (node.has(candidate)) {
                return node.path(candidate);
            }
            if (node.has("metrics") && node.path("metrics").has(candidate)) {
                return node.path("metrics").path(candidate);
            }
            if (node.has("data") && node.path("data").has(candidate)) {
                return node.path("data").path(candidate);
            }
        }
        return null;
    }

    private RevenueCatChartDto readChart(String chartName, String environment, JsonNode response) {
        RevenueCatChartDto chart = emptyChart(chartName, environment, "RevenueCat chart fetched.");
        List<RevenueCatChartPointDto> points = new ArrayList<>();
        collectPoints(response, points);
        chart.setPoints(normalizePoints(points));
        return chart;
    }

    private void collectPoints(JsonNode node, List<RevenueCatChartPointDto> points) {
        if (node == null || node.isNull() || points.size() >= 120) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectPoints(child, points);
            }
            return;
        }
        if (node.isObject()) {
            collectPairedArrays(node, points);
            collectDateValueMap(node, points);
            String date = firstText(node, "date", "period", "timestamp", "x");
            Double value = firstDouble(node, "value", "total", "amount", "y");
            if (date != null && value != null) {
                points.add(new RevenueCatChartPointDto(date, value));
            }
            for (JsonNode child : node) {
                collectPoints(child, points);
            }
        }
    }

    private void collectPairedArrays(JsonNode node, List<RevenueCatChartPointDto> points) {
        JsonNode dates = firstArray(node, "dates", "date", "periods", "labels", "x");
        JsonNode values = firstArray(node, "values", "value", "totals", "amounts", "y");
        if (dates == null || values == null) {
            return;
        }
        int size = Math.min(dates.size(), values.size());
        for (int index = 0; index < size && points.size() < 120; index++) {
            JsonNode dateNode = dates.get(index);
            JsonNode valueNode = values.get(index);
            if (dateNode != null && valueNode != null && valueNode.isNumber()) {
                String date = dateNode.asText();
                if (hasText(date)) {
                    points.add(new RevenueCatChartPointDto(date, valueNode.asDouble()));
                }
            }
        }
    }

    private void collectDateValueMap(JsonNode node, List<RevenueCatChartPointDto> points) {
        node.fields().forEachRemaining((entry) -> {
            if (points.size() >= 120) {
                return;
            }
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if (looksLikeDateOrBucket(key) && value != null && value.isNumber()) {
                points.add(new RevenueCatChartPointDto(key, value.asDouble()));
            }
        });
    }

    private List<RevenueCatChartPointDto> normalizePoints(List<RevenueCatChartPointDto> points) {
        Map<String, Double> normalized = new LinkedHashMap<>();
        for (RevenueCatChartPointDto point : points) {
            if (point.getDate() == null || point.getValue() == null) {
                continue;
            }
            normalized.put(point.getDate(), point.getValue());
        }
        return normalized.entrySet().stream()
                .map((entry) -> new RevenueCatChartPointDto(entry.getKey(), entry.getValue()))
                .toList();
    }

    private JsonNode firstArray(JsonNode node, String... fields) {
        for (String field : fields) {
            if (node.has(field) && node.path(field).isArray()) {
                return node.path(field);
            }
        }
        return null;
    }

    private boolean looksLikeDateOrBucket(String value) {
        return value != null
                && (value.matches("\\d{4}-\\d{2}-\\d{2}.*")
                || value.matches("\\d{4}-\\d{2}")
                || List.of("processed", "failed", "ignored").contains(value.toLowerCase(Locale.ROOT)));
    }

    private void applySandboxOverview(RevenueCatMonitoringOverviewDto dto) {
        long processed = eventRepository.countByStatus(SubscriptionProviderEventStatus.PROCESSED);
        long failed = eventRepository.countByStatus(SubscriptionProviderEventStatus.FAILED);
        long ignored = eventRepository.countByStatus(SubscriptionProviderEventStatus.IGNORED);
        dto.setProviderReachable(true);
        dto.setStatusMessage("Sandbox overview is based on stored RevenueCat webhook events.");
        dto.setMetrics(List.of(
                new RevenueCatMetricCardDto("sandbox_processed_events", "Processed Events", String.valueOf(processed), null, "Stored sandbox/test webhook events processed by backend."),
                new RevenueCatMetricCardDto("sandbox_failed_events", "Failed Events", String.valueOf(failed), null, "Stored provider events requiring retry or review."),
                new RevenueCatMetricCardDto("sandbox_ignored_events", "Ignored Events", String.valueOf(ignored), null, "Duplicate or non-entitlement events ignored by backend."),
                new RevenueCatMetricCardDto("sandbox_total_events", "Total Events", String.valueOf(processed + failed + ignored), null, "All stored provider events.")
        ));
    }

    private List<RevenueCatChartDto> sandboxCharts() {
        return List.of(
                sandboxStatusChart("sandbox_events", "Sandbox Events")
        );
    }

    private RevenueCatChartDto sandboxStatusChart(String chartName, String label) {
        RevenueCatChartDto chart = new RevenueCatChartDto();
        chart.setChartName(chartName);
        chart.setLabel(label);
        chart.setEnvironment("sandbox");
        chart.setCurrency(currency());
        chart.setProviderReachable(true);
        chart.setStatusMessage("Sandbox chart generated from stored webhook status counters.");
        chart.setPoints(List.of(
                new RevenueCatChartPointDto("processed", (double) eventRepository.countByStatus(SubscriptionProviderEventStatus.PROCESSED)),
                new RevenueCatChartPointDto("failed", (double) eventRepository.countByStatus(SubscriptionProviderEventStatus.FAILED)),
                new RevenueCatChartPointDto("ignored", (double) eventRepository.countByStatus(SubscriptionProviderEventStatus.IGNORED))
        ));
        return chart;
    }

    private List<RevenueCatMetricCardDto> defaultOverviewMetrics() {
        return OVERVIEW_KEYS.stream().map((key) -> metric(key, "0")).toList();
    }

    private RevenueCatMetricCardDto metric(String key, String value) {
        return new RevenueCatMetricCardDto(key, label(key), value, unit(key), description(key));
    }

    private List<RevenueCatChartDto> emptyCharts(String environment) {
        return CHART_NAMES.stream().map((chart) -> emptyChart(chart, environment, "No chart data returned.")).toList();
    }

    private RevenueCatChartDto emptyChart(String chartName, String environment, String statusMessage) {
        RevenueCatChartDto chart = new RevenueCatChartDto();
        chart.setChartName(chartName);
        chart.setLabel(label(chartName));
        chart.setEnvironment(environment);
        chart.setCurrency(currency());
        chart.setProviderReachable(false);
        chart.setStatusMessage(statusMessage);
        chart.setPoints(List.of());
        return chart;
    }

    private String label(String key) {
        return switch (key) {
            case "active_trials" -> "Active Trials";
            case "active_subscriptions" -> "Active Subscriptions";
            case "actives" -> "Active Subscriptions";
            case "trials" -> "Active Trials";
            case "mrr" -> "MRR";
            case "revenue" -> "Revenue";
            case "new_customers" -> "New Customers";
            case "active_customers" -> "Active Customers";
            case "customers_new" -> "New Customers";
            case "customers_active" -> "Active Customers";
            default -> key.replace('_', ' ');
        };
    }

    private String unit(String key) {
        return switch (key) {
            case "mrr", "revenue" -> currency();
            default -> null;
        };
    }

    private String description(String key) {
        return switch (key) {
            case "active_trials", "active_subscriptions", "mrr" -> "Current RevenueCat overview metric.";
            case "revenue", "new_customers", "active_customers" -> "RevenueCat overview metric for the selected period.";
            default -> "RevenueCat metric.";
        };
    }

    private String normalizeEnvironment(String value) {
        return "sandbox".equalsIgnoreCase(value) ? "sandbox" : "production";
    }

    private int rangeDays(String range) {
        if ("7d".equalsIgnoreCase(range)) return 7;
        if ("90d".equalsIgnoreCase(range)) return 90;
        return 28;
    }

    private DateRange resolveDateRange(String range, String requestedStartDate, String requestedEndDate) {
        LocalDate fallbackEnd = LocalDate.now(ZoneOffset.UTC);
        LocalDate fallbackStart = fallbackEnd.minusDays(rangeDays(range) - 1L);
        if (!"custom".equalsIgnoreCase(range) || !hasText(requestedStartDate) || !hasText(requestedEndDate)) {
            return new DateRange(fallbackStart, fallbackEnd);
        }
        try {
            LocalDate start = LocalDate.parse(requestedStartDate);
            LocalDate end = LocalDate.parse(requestedEndDate);
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            if (end.isAfter(today)) {
                end = today;
            }
            if (start.isAfter(end)) {
                return new DateRange(fallbackStart, fallbackEnd);
            }
            if (start.isBefore(end.minusDays(364))) {
                start = end.minusDays(364);
            }
            return new DateRange(start, end);
        } catch (RuntimeException ex) {
            return new DateRange(fallbackStart, fallbackEnd);
        }
    }

    private record DateRange(LocalDate startDate, LocalDate endDate) {
    }

    private String baseUrl() {
        return hasText(properties.getApi().getBaseUrl()) ? properties.getApi().getBaseUrl() : "https://api.revenuecat.com/v2";
    }

    private String currency() {
        return hasText(properties.getApi().getCurrency()) ? properties.getApi().getCurrency() : "EUR";
    }

    private String camelize(String value) {
        StringBuilder result = new StringBuilder();
        boolean upperNext = false;
        for (char c : value.toCharArray()) {
            if (c == '_') {
                upperNext = true;
            } else if (upperNext) {
                result.append(Character.toUpperCase(c));
                upperNext = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            if (node.has(field) && !node.path(field).isNull()) {
                String value = node.path(field).asText();
                if (hasText(value)) {
                    return value;
                }
            }
        }
        return null;
    }

    private Double firstDouble(JsonNode node, String... fields) {
        for (String field : fields) {
            if (node.has(field) && node.path(field).isNumber()) {
                return node.path(field).asDouble();
            }
        }
        return null;
    }

    private String monitoringErrorMessage(Exception ex) {
        if (ex instanceof RestClientResponseException responseException) {
            int statusCode = responseException.getStatusCode().value();
            if (statusCode == 401 || statusCode == 403) {
                return "Authentication failed. Check RevenueCat API secret key and project access.";
            }
            if (statusCode == 400) {
                return "RevenueCat rejected the request parameters. Check project id, chart name, date range, and currency.";
            }
            return "RevenueCat API returned HTTP " + statusCode + ".";
        }
        return safeMessage(ex);
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        String secret = properties.getApi().getSecretKey();
        return hasText(secret) ? message.replace(secret, "[redacted]") : message;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
