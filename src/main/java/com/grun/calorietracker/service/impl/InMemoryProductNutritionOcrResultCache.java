package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.config.ProductNutritionOcrProperties;
import com.grun.calorietracker.service.ProductNutritionOcrResultCache;
import com.grun.calorietracker.service.model.ProductNutritionOcrEvidence;
import com.grun.calorietracker.service.model.ProductNutritionOcrFallbackRequest;
import com.grun.calorietracker.service.model.ProductNutritionOcrFallbackResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryProductNutritionOcrResultCache implements ProductNutritionOcrResultCache {
    private final ProductNutritionOcrProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<Key, Entry> entries = new ConcurrentHashMap<>();

    @Autowired
    public InMemoryProductNutritionOcrResultCache(ProductNutritionOcrProperties properties) {
        this(properties, Clock.systemUTC());
    }

    public InMemoryProductNutritionOcrResultCache(ProductNutritionOcrProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public Optional<ProductNutritionOcrFallbackResult> get(
            ProductNutritionOcrFallbackRequest request, ProductNutritionOcrEvidence evidence, String model) {
        Key key = key(request, evidence, model);
        Entry entry = entries.get(key);
        if (entry == null) return Optional.empty();
        if (!entry.expiresAt().isAfter(clock.instant())) {
            entries.remove(key, entry);
            return Optional.empty();
        }
        return Optional.of(entry.result());
    }

    @Override
    public void put(ProductNutritionOcrFallbackRequest request, ProductNutritionOcrEvidence evidence,
                    String model, ProductNutritionOcrFallbackResult result) {
        if (entries.size() >= properties.getCacheMaxEntries()) {
            entries.entrySet().stream().min(Comparator.comparing(value -> value.getValue().expiresAt()))
                    .ifPresent(value -> entries.remove(value.getKey(), value.getValue()));
        }
        entries.put(key(request, evidence, model),
                new Entry(result, clock.instant().plus(properties.getCacheTtl())));
    }

    private Key key(ProductNutritionOcrFallbackRequest request, ProductNutritionOcrEvidence evidence, String model) {
        ArrayList<String> fields = new ArrayList<>(request.uncertainFields());
        fields.sort(String::compareTo);
        return new Key(request.requesterUserId(), request.reviewCaseId(), evidence.sha256(),
                request.parserVersion(), model, String.join(",", fields));
    }

    private record Key(Long userId, Long caseId, String checksum, String parserVersion,
                       String model, String uncertainFields) { }
    private record Entry(ProductNutritionOcrFallbackResult result, Instant expiresAt) { }
}
