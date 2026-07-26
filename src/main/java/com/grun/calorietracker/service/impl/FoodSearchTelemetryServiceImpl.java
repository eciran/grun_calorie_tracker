package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.FoodProductSearchPageDto;
import com.grun.calorietracker.dto.FoodSearchCriteriaDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodSearchTelemetryEntity;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodSearchTelemetryRepository;
import com.grun.calorietracker.service.FoodSearchTelemetryService;
import com.grun.calorietracker.service.support.FoodProductNormalizationRules;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class FoodSearchTelemetryServiceImpl implements FoodSearchTelemetryService {
    private static final Pattern EMAIL = Pattern.compile("(?i)\\b[\\w.%+-]+@[\\w.-]+\\.[a-z]{2,}\\b");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+?\\d[\\d ()-]{7,}\\d)(?!\\d)");

    private final FoodSearchTelemetryRepository telemetryRepository;
    private final FoodItemRepository foodItemRepository;

    @Override
    @Transactional
    public String recordSearch(FoodSearchCriteriaDto criteria, FoodProductSearchPageDto result) {
        String normalized = FoodProductNormalizationRules.normalizeText(criteria.getQuery());
        String safeQuery = redact(normalized);
        FoodSearchTelemetryEntity event = new FoodSearchTelemetryEntity();
        event.setSafeQuery(safeQuery);
        event.setQueryFingerprint(sha256(safeQuery));
        event.setQueryLanguage(criteria.getPreferredLanguage());
        event.setMarketRegion(criteria.getMarketRegion());
        event.setResultCount(result == null || result.getTotalElements() == null
                ? 0
                : Math.toIntExact(Math.min(result.getTotalElements(), Integer.MAX_VALUE)));
        event.setResultFoodItemIds(result == null || result.getContent() == null
                ? ""
                : result.getContent().stream()
                        .map(com.grun.calorietracker.dto.FoodProductDto::getId)
                        .filter(java.util.Objects::nonNull)
                        .map(String::valueOf)
                        .collect(java.util.stream.Collectors.joining(",")));
        return telemetryRepository.save(event).getId();
    }

    @Override
    @Transactional
    public void recordSelection(String searchRequestId, Long foodItemId, int rank) {
        FoodSearchTelemetryEntity event = telemetryRepository.findById(searchRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Search telemetry event not found."));
        if (event.getSearchedAt().isBefore(Instant.now().minus(24, ChronoUnit.HOURS))) {
            throw new IllegalArgumentException("Search telemetry event has expired for selection reporting.");
        }
        if (event.getSelectedAt() != null) {
            throw new IllegalArgumentException("A selection was already recorded for this search.");
        }
        java.util.List<Long> returnedIds = parseReturnedIds(event.getResultFoodItemIds());
        if (rank < 1 || rank > returnedIds.size()) {
            throw new IllegalArgumentException("Selected rank is outside the returned result range.");
        }
        if (!returnedIds.contains(foodItemId)) {
            throw new IllegalArgumentException("Selected product was not present in this search result page.");
        }
        FoodItemEntity selected = foodItemRepository.findById(foodItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Selected food product not found."));
        event.setSelectedFoodItem(selected);
        event.setSelectedRank(rank);
        event.setSelectedAt(Instant.now());
        telemetryRepository.save(event);
        foodItemRepository.incrementSearchSelectionCount(foodItemId);
    }

    @Scheduled(cron = "${grun.product-search.telemetry-cleanup-cron:0 20 3 * * *}")
    @Transactional
    public void deleteExpiredTelemetry() {
        telemetryRepository.deleteByExpiresAtBefore(Instant.now());
    }

    private java.util.List<Long> parseReturnedIds(String value) {
        if (value == null || value.isBlank()) {
            return java.util.List.of();
        }
        return java.util.Arrays.stream(value.split(","))
                .map(Long::valueOf)
                .toList();
    }

    private String redact(String query) {
        String value = query == null ? "" : query;
        value = EMAIL.matcher(value).replaceAll("[email]");
        value = PHONE.matcher(value).replaceAll("[phone]");
        return value.length() <= 120 ? value : value.substring(0, 120);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }
}