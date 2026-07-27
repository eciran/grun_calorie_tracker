package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.AdminEngagementAnalyticsDto;
import com.grun.calorietracker.dto.FoodSearchTelemetrySummaryDto;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.ProductAnalyticsEventType;
import com.grun.calorietracker.enums.ProductAnalyticsFeature;
import com.grun.calorietracker.enums.SubscriptionPlan;
import com.grun.calorietracker.repository.ProductAnalyticsEventRepository;
import com.grun.calorietracker.service.AdminEngagementAnalyticsService;
import com.grun.calorietracker.service.FoodSearchTelemetryAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminEngagementAnalyticsServiceImpl implements AdminEngagementAnalyticsService {

    private static final int EVENT_CONTRACT_VERSION = 1;
    private final ProductAnalyticsEventRepository eventRepository;
    private final FoodSearchTelemetryAdminService searchTelemetryService;

    @Override
    @Transactional(readOnly = true)
    public AdminEngagementAnalyticsDto getSummary(int hours, MarketRegion region,
                                                  PreferredLanguage language,
                                                  SubscriptionPlan plan) {
        if (hours < 1 || hours > 2160) {
            throw new IllegalArgumentException("Analytics window must be between 1 and 2160 hours.");
        }
        LocalDateTime generatedAt = LocalDateTime.now();
        LocalDateTime since = generatedAt.minusHours(hours);
        Map<ProductAnalyticsEventType, Metric> metrics = eventMetrics(
                eventRepository.summarizeEvents(since, name(region), name(language), plan));
        FoodSearchTelemetrySummaryDto search = searchTelemetryService.getSummary(hours, region, language);

        return new AdminEngagementAnalyticsDto(
                EVENT_CONTRACT_VERSION,
                hours,
                since,
                generatedAt,
                new AdminEngagementAnalyticsDto.Filters(name(region), name(language), name(plan)),
                onboarding(metrics),
                new AdminEngagementAnalyticsDto.SearchQuality(
                        search.searches(), search.zeroResultSearches(), search.selectedSearches(),
                        search.noSelectionSearches(), search.zeroResultRate(), search.selectionRate(), false,
                        search.topZeroResultQueries().stream()
                                .map(item -> new AdminEngagementAnalyticsDto.SearchQuery(item.query(), item.searches()))
                                .toList()),
                flow(metrics, ProductAnalyticsEventType.LOG_FLOW_STARTED,
                        ProductAnalyticsEventType.LOG_FLOW_COMPLETED,
                        ProductAnalyticsEventType.FIRST_LOG_COMPLETED,
                        ProductAnalyticsEventType.LOG_FLOW_FAILED),
                flow(metrics, ProductAnalyticsEventType.BARCODE_SCAN_STARTED,
                        ProductAnalyticsEventType.BARCODE_SCAN_COMPLETED,
                        null,
                        ProductAnalyticsEventType.BARCODE_SCAN_FAILED),
                featureAdoption(metrics, eventRepository.summarizeFeatures(
                        since, name(region), name(language), plan)),
                segments(eventRepository.summarizeRegions(since)),
                segments(eventRepository.summarizeLanguages(since)),
                planSegments(eventRepository.summarizePlans(since))
        );
    }

    private Map<ProductAnalyticsEventType, Metric> eventMetrics(
            List<ProductAnalyticsEventRepository.EventMetricProjection> rows) {
        Map<ProductAnalyticsEventType, Metric> result = new EnumMap<>(ProductAnalyticsEventType.class);
        rows.forEach(row -> result.put(row.getEventType(), new Metric(
                row.getEventCount(), row.getUserCount(), rounded(row.getAverageDurationMs()))));
        return result;
    }

    private AdminEngagementAnalyticsDto.OnboardingFunnel onboarding(
            Map<ProductAnalyticsEventType, Metric> metrics) {
        Metric started = metric(metrics, ProductAnalyticsEventType.ONBOARDING_STARTED);
        Metric failed = metric(metrics, ProductAnalyticsEventType.ONBOARDING_STEP_FAILED);
        Metric completed = metric(metrics, ProductAnalyticsEventType.ONBOARDING_COMPLETED);
        return new AdminEngagementAnalyticsDto.OnboardingFunnel(
                started.events,
                metric(metrics, ProductAnalyticsEventType.ONBOARDING_STEP_VIEWED).events,
                metric(metrics, ProductAnalyticsEventType.ONBOARDING_STEP_COMPLETED).events,
                failed.events,
                metric(metrics, ProductAnalyticsEventType.ONBOARDING_RESUMED).events,
                metric(metrics, ProductAnalyticsEventType.ONBOARDING_PREVIEWED).events,
                completed.events,
                metric(metrics, ProductAnalyticsEventType.ONBOARDING_ABANDONED).events,
                rate(completed.events, started.events),
                rate(failed.events, metric(metrics, ProductAnalyticsEventType.ONBOARDING_STEP_VIEWED).events),
                completed.averageDurationMs
        );
    }

    private AdminEngagementAnalyticsDto.FlowMetric flow(
            Map<ProductAnalyticsEventType, Metric> metrics,
            ProductAnalyticsEventType startedType,
            ProductAnalyticsEventType completedType,
            ProductAnalyticsEventType firstCompletionType,
            ProductAnalyticsEventType failedType) {
        Metric started = metric(metrics, startedType);
        Metric completed = metric(metrics, completedType);
        Metric firstCompletions = firstCompletionType == null ? Metric.ZERO : metric(metrics, firstCompletionType);
        Metric failed = metric(metrics, failedType);
        return new AdminEngagementAnalyticsDto.FlowMetric(
                started.events, completed.events, firstCompletions.events, failed.events, completed.users,
                rate(completed.events, started.events), completed.averageDurationMs);
    }

    private List<AdminEngagementAnalyticsDto.FeatureAdoption> featureAdoption(
            Map<ProductAnalyticsEventType, Metric> metrics,
            List<ProductAnalyticsEventRepository.FeatureMetricProjection> rows) {
        Map<ProductAnalyticsFeature, Metric> features = new EnumMap<>(ProductAnalyticsFeature.class);
        rows.forEach(row -> {
            try {
                features.put(ProductAnalyticsFeature.valueOf(row.getFeature()), new Metric(
                        row.getEventCount(), row.getUserCount(), rounded(row.getAverageDurationMs())));
            } catch (IllegalArgumentException ignored) {
                // Unknown legacy dimensions are intentionally excluded from the admin contract.
            }
        });
        merge(features, ProductAnalyticsFeature.FOOD_LOG,
                metric(metrics, ProductAnalyticsEventType.LOG_FLOW_COMPLETED));
        merge(features, ProductAnalyticsFeature.AI,
                metric(metrics, ProductAnalyticsEventType.AI_DRAFT_CONFIRMED));
        List<AdminEngagementAnalyticsDto.FeatureAdoption> result = new ArrayList<>();
        for (ProductAnalyticsFeature feature : ProductAnalyticsFeature.values()) {
            Metric metric = features.getOrDefault(feature, Metric.ZERO);
            result.add(new AdminEngagementAnalyticsDto.FeatureAdoption(
                    feature.name(), metric.events, metric.users,
                    Math.max(0, metric.events - metric.users), metric.averageDurationMs));
        }
        return result;
    }

    private void merge(Map<ProductAnalyticsFeature, Metric> features,
                       ProductAnalyticsFeature feature, Metric extra) {
        Metric current = features.getOrDefault(feature, Metric.ZERO);
        features.put(feature, new Metric(current.events + extra.events,
                Math.max(current.users, extra.users), weightedAverage(current, extra)));
    }

    private long weightedAverage(Metric left, Metric right) {
        long events = left.events + right.events;
        return events == 0 ? 0 : Math.round((left.averageDurationMs * left.events
                + right.averageDurationMs * right.events) / (double) events);
    }

    private List<AdminEngagementAnalyticsDto.SegmentMetric> segments(
            List<ProductAnalyticsEventRepository.SegmentMetricProjection> rows) {
        return rows.stream().map(row -> new AdminEngagementAnalyticsDto.SegmentMetric(
                row.getSegment(), row.getEventCount(), row.getUserCount())).toList();
    }

    private List<AdminEngagementAnalyticsDto.SegmentMetric> planSegments(
            List<ProductAnalyticsEventRepository.PlanSegmentMetricProjection> rows) {
        return rows.stream().map(row -> new AdminEngagementAnalyticsDto.SegmentMetric(
                name(row.getSegment()), row.getEventCount(), row.getUserCount())).toList();
    }

    private Metric metric(Map<ProductAnalyticsEventType, Metric> metrics,
                          ProductAnalyticsEventType type) {
        return metrics.getOrDefault(type, Metric.ZERO);
    }

    private long rounded(Double value) {
        return value == null ? 0 : Math.round(value);
    }

    private double rate(long numerator, long denominator) {
        return denominator == 0 ? 0 : Math.round(numerator * 1000.0 / denominator) / 10.0;
    }

    private String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private record Metric(long events, long users, long averageDurationMs) {
        private static final Metric ZERO = new Metric(0, 0, 0);
    }
}
