package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.FoodProductEvidenceComparisonDto;
import com.grun.calorietracker.dto.FoodProductEvidenceContextDto;
import com.grun.calorietracker.dto.FoodProductEvidenceDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodProductSourceEvidenceEntity;
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodDataSource;
import com.grun.calorietracker.enums.FoodEvidenceBasis;
import com.grun.calorietracker.enums.FoodEvidenceComparisonState;
import com.grun.calorietracker.enums.FoodEvidenceField;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodProductSourceEvidenceRepository;
import com.grun.calorietracker.service.FoodProductEvidenceService;
import com.grun.calorietracker.service.support.BatchQuerySupport;
import com.grun.calorietracker.service.support.evidence.CuratedFoodSourceEvidenceAdapter;
import com.grun.calorietracker.service.support.evidence.FallbackFoodSourceEvidenceAdapter;
import com.grun.calorietracker.service.support.evidence.FoodSourceEvidenceAdapter;
import com.grun.calorietracker.service.support.evidence.OpenFoodFactsSourceEvidenceAdapter;
import com.grun.calorietracker.service.support.evidence.UsdaFoodSourceEvidenceAdapter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FoodProductEvidenceServiceImpl implements FoodProductEvidenceService {

    private static final Set<FoodEvidenceField> CRITICAL_FIELDS = EnumSet.of(
            FoodEvidenceField.CALORIES,
            FoodEvidenceField.PROTEIN,
            FoodEvidenceField.FAT,
            FoodEvidenceField.CARBS,
            FoodEvidenceField.SERVING_SIZE_GRAMS
    );

    private static final List<FoodSourceEvidenceAdapter> SOURCE_ADAPTERS = List.of(
            new UsdaFoodSourceEvidenceAdapter(),
            new OpenFoodFactsSourceEvidenceAdapter(),
            new CuratedFoodSourceEvidenceAdapter(),
            new FallbackFoodSourceEvidenceAdapter()
    );
    private final FoodProductSourceEvidenceRepository evidenceRepository;
    private final FoodItemRepository foodItemRepository;

    @Override
    @Transactional
    public int recordImportEvidence(
            List<FoodItemEntity> products,
            FoodEvidenceBasis basis,
            LocalDateTime observedAt,
            String sourceVersion,
            String reviewerIdentity
    ) {
        if (products == null || products.isEmpty()) {
            return 0;
        }
        FoodEvidenceBasis resolvedBasis = basis == null ? FoodEvidenceBasis.PER_100_G : basis;
        LocalDateTime resolvedObservedAt = observedAt == null ? LocalDateTime.now() : observedAt;
        List<FoodProductSourceEvidenceEntity> candidates = new ArrayList<>();
        for (FoodItemEntity product : products) {
            if (product == null || product.getId() == null || product.getDataSource() == null) {
                continue;
            }
            String externalId = firstNonBlank(product.getSourceKey(), product.getNormalizedBarcode(), String.valueOf(product.getId()));
            int confidence = adapterFor(product.getDataSource()).confidence(product.getDataSource());
            add(candidates, product, externalId, FoodEvidenceField.CALORIES, product.getCalories(), resolvedBasis, confidence, resolvedObservedAt, sourceVersion);
            add(candidates, product, externalId, FoodEvidenceField.PROTEIN, product.getProtein(), resolvedBasis, confidence, resolvedObservedAt, sourceVersion);
            add(candidates, product, externalId, FoodEvidenceField.FAT, product.getFat(), resolvedBasis, confidence, resolvedObservedAt, sourceVersion);
            add(candidates, product, externalId, FoodEvidenceField.CARBS, product.getCarbs(), resolvedBasis, confidence, resolvedObservedAt, sourceVersion);
            add(candidates, product, externalId, FoodEvidenceField.FIBER, product.getFiber(), resolvedBasis, confidence, resolvedObservedAt, sourceVersion);
            add(candidates, product, externalId, FoodEvidenceField.SUGAR, product.getSugar(), resolvedBasis, confidence, resolvedObservedAt, sourceVersion);
            add(candidates, product, externalId, FoodEvidenceField.SODIUM, product.getSodium(), resolvedBasis, confidence, resolvedObservedAt, sourceVersion);
            add(candidates, product, externalId, FoodEvidenceField.POTASSIUM, product.getPotassium(), resolvedBasis, confidence, resolvedObservedAt, sourceVersion);
            add(candidates, product, externalId, FoodEvidenceField.CHOLESTEROL, product.getCholesterol(), resolvedBasis, confidence, resolvedObservedAt, sourceVersion);
            add(candidates, product, externalId, FoodEvidenceField.CALCIUM, product.getCalcium(), resolvedBasis, confidence, resolvedObservedAt, sourceVersion);
            add(candidates, product, externalId, FoodEvidenceField.IRON, product.getIron(), resolvedBasis, confidence, resolvedObservedAt, sourceVersion);
            add(candidates, product, externalId, FoodEvidenceField.MAGNESIUM, product.getMagnesium(), resolvedBasis, confidence, resolvedObservedAt, sourceVersion);
            add(candidates, product, externalId, FoodEvidenceField.ZINC, product.getZinc(), resolvedBasis, confidence, resolvedObservedAt, sourceVersion);
            add(candidates, product, externalId, FoodEvidenceField.VITAMIN_A, product.getVitaminA(), resolvedBasis, confidence, resolvedObservedAt, sourceVersion);
            add(candidates, product, externalId, FoodEvidenceField.VITAMIN_C, product.getVitaminC(), resolvedBasis, confidence, resolvedObservedAt, sourceVersion);
            add(candidates, product, externalId, FoodEvidenceField.VITAMIN_D, product.getVitaminD(), resolvedBasis, confidence, resolvedObservedAt, sourceVersion);
            add(candidates, product, externalId, FoodEvidenceField.VITAMIN_E, product.getVitaminE(), resolvedBasis, confidence, resolvedObservedAt, sourceVersion);
            add(candidates, product, externalId, FoodEvidenceField.VITAMIN_B12, product.getVitaminB12(), resolvedBasis, confidence, resolvedObservedAt, sourceVersion);
            add(candidates, product, externalId, FoodEvidenceField.SATURATED_FAT, product.getSaturatedFat(), resolvedBasis, confidence, resolvedObservedAt, sourceVersion);
            add(candidates, product, externalId, FoodEvidenceField.TRANS_FAT, product.getTransFat(), resolvedBasis, confidence, resolvedObservedAt, sourceVersion);
            add(candidates, product, externalId, FoodEvidenceField.SUGAR_ALCOHOL, product.getSugarAlcohol(), resolvedBasis, confidence, resolvedObservedAt, sourceVersion);
            add(candidates, product, externalId, FoodEvidenceField.SERVING_SIZE_GRAMS, product.getServingSizeGrams(), FoodEvidenceBasis.SERVING, confidence, resolvedObservedAt, sourceVersion);
        }
        String resolvedReviewerIdentity = reviewerIdentity == null || reviewerIdentity.isBlank()
                ? null
                : reviewerIdentity.trim();
        candidates.forEach(value -> value.setReviewerIdentity(resolvedReviewerIdentity));
        if (candidates.isEmpty()) {
            return 0;
        }
        Set<String> fingerprints = new LinkedHashSet<>();
        candidates.forEach(value -> fingerprints.add(value.getFingerprint()));
        Set<String> existing = new LinkedHashSet<>(BatchQuerySupport.loadInChunks(
                fingerprints,
                evidenceRepository::findExistingFingerprints
        ));
        List<FoodProductSourceEvidenceEntity> newEvidence = candidates.stream()
                .filter(value -> !existing.contains(value.getFingerprint()))
                .toList();
        for (int start = 0; start < newEvidence.size(); start += BatchQuerySupport.DEFAULT_CHUNK_SIZE) {
            evidenceRepository.saveAll(newEvidence.subList(
                    start,
                    Math.min(start + BatchQuerySupport.DEFAULT_CHUNK_SIZE, newEvidence.size())
            ));
        }
        return newEvidence.size();
    }

    @Override
    @Transactional(readOnly = true)
    public FoodProductEvidenceContextDto buildContext(FoodItemEntity product) {
        List<FoodItemEntity> candidates = canonicalCandidates(product);
        List<Long> productIds = candidates.stream().map(FoodItemEntity::getId).toList();
        List<FoodProductSourceEvidenceEntity> all = productIds.isEmpty()
                ? List.of()
                : evidenceRepository.findByFoodItemIdInOrderByObservedAtDescIdDesc(productIds);
        List<FoodProductSourceEvidenceEntity> latest = latestPerProviderField(all);
        Set<FoodEvidenceField> fields = new LinkedHashSet<>(CRITICAL_FIELDS);
        latest.forEach(value -> fields.add(value.getFieldName()));
        List<FoodProductEvidenceComparisonDto> comparisons = fields.stream()
                .map(field -> compareEvidence(candidates, latest, field, basisFor(field)))
                .toList();
        return new FoodProductEvidenceContextDto(latest.stream().map(this::toDto).toList(), comparisons);
    }

    @Override
    @Transactional(readOnly = true)
    public FoodProductEvidenceComparisonDto compare(
            List<FoodItemEntity> products,
            FoodEvidenceField field,
            FoodEvidenceBasis basis
    ) {
        if (products == null || products.isEmpty()) {
            return missing(field, basis);
        }
        List<Long> ids = products.stream().map(FoodItemEntity::getId).filter(java.util.Objects::nonNull).toList();
        List<FoodProductSourceEvidenceEntity> evidence = ids.isEmpty()
                ? List.of()
                : latestPerProviderField(evidenceRepository.findByFoodItemIdInOrderByObservedAtDescIdDesc(ids));
        return compareEvidence(products, evidence, field, basis);
    }

    private FoodProductEvidenceComparisonDto compareEvidence(
            List<FoodItemEntity> products,
            List<FoodProductSourceEvidenceEntity> evidence,
            FoodEvidenceField field,
            FoodEvidenceBasis basis
    ) {
        List<FoodProductSourceEvidenceEntity> matching = evidence.stream()
                .filter(value -> value.getFieldName() == field && value.getBasis() == basis)
                .toList();
        if (matching.isEmpty()) {
            return missing(field, basis);
        }
        List<FoodProductSourceEvidenceEntity> current = matching.stream().filter(value -> !isStale(value)).toList();
        FoodItemEntity reference = products == null || products.isEmpty() ? null : products.get(0);
        FoodProductSourceEvidenceEntity preferred = matching.stream()
                .min(preferenceComparator(reference))
                .orElse(matching.get(0));
        if (current.isEmpty()) {
            return comparison(field, basis, FoodEvidenceComparisonState.STALE, preferred, 0.0,
                    "All available evidence is stale and must be refreshed.", matching);
        }
        if (current.stream().map(FoodProductSourceEvidenceEntity::getProvider).distinct().count() < 2) {
            return comparison(field, basis, FoodEvidenceComparisonState.SINGLE_SOURCE, preferred, 0.0,
                    "Only one current provider supports this field.", current);
        }
        double min = current.stream().mapToDouble(FoodProductSourceEvidenceEntity::getNumericValue).min().orElse(0.0);
        double max = current.stream().mapToDouble(FoodProductSourceEvidenceEntity::getNumericValue).max().orElse(0.0);
        double difference = max - min;
        double tolerance = tolerance(field, Math.max(Math.abs(min), Math.abs(max)));
        FoodEvidenceComparisonState state = difference <= tolerance
                ? FoodEvidenceComparisonState.MATCH
                : FoodEvidenceComparisonState.CONFLICT;
        String reason = state == FoodEvidenceComparisonState.MATCH
                ? "Current provider values agree within the deterministic tolerance."
                : "Current provider values differ beyond the deterministic tolerance; exact AI replacement is not allowed.";
        return comparison(field, basis, state, preferred, difference, reason, current);
    }

    private FoodProductEvidenceComparisonDto comparison(
            FoodEvidenceField field,
            FoodEvidenceBasis basis,
            FoodEvidenceComparisonState state,
            FoodProductSourceEvidenceEntity preferred,
            Double difference,
            String reason,
            List<FoodProductSourceEvidenceEntity> evidence
    ) {
        return new FoodProductEvidenceComparisonDto(
                field, basis, state, preferred == null ? null : preferred.getId(), difference, reason,
                evidence.stream().map(FoodProductSourceEvidenceEntity::getId).toList());
    }

    private FoodProductEvidenceComparisonDto missing(FoodEvidenceField field, FoodEvidenceBasis basis) {
        return new FoodProductEvidenceComparisonDto(
                field, basis, FoodEvidenceComparisonState.MISSING, null, null,
                "No source evidence is available for this field.", List.of());
    }

    private List<FoodItemEntity> canonicalCandidates(FoodItemEntity product) {
        if (product == null || product.getId() == null) {
            return List.of();
        }
        if (product.getCanonicalFoodKey() == null || product.getCanonicalFoodKey().isBlank()) {
            return List.of(product);
        }
        List<FoodItemEntity> candidates = foodItemRepository.findByCanonicalFoodKeyIn(
                List.of(product.getCanonicalFoodKey()), Sort.by(Sort.Order.asc("id")));
        return candidates.isEmpty() ? List.of(product) : candidates;
    }

    private List<FoodProductSourceEvidenceEntity> latestPerProviderField(List<FoodProductSourceEvidenceEntity> evidence) {
        Map<String, FoodProductSourceEvidenceEntity> latest = new LinkedHashMap<>();
        for (FoodProductSourceEvidenceEntity value : evidence) {
            String key = value.getFoodItem().getId() + ":" + value.getProvider() + ":" + value.getFieldName() + ":" + value.getBasis();
            latest.putIfAbsent(key, value);
        }
        return new ArrayList<>(latest.values());
    }

    private Comparator<FoodProductSourceEvidenceEntity> preferenceComparator(FoodItemEntity product) {
        return Comparator
                .comparingInt((FoodProductSourceEvidenceEntity value) -> isStale(value) ? 1 : 0)
                .thenComparingInt(value -> precedence(product, value.getProvider()))
                .thenComparing(FoodProductSourceEvidenceEntity::getConfidenceScore, Comparator.reverseOrder())
                .thenComparing(FoodProductSourceEvidenceEntity::getObservedAt, Comparator.reverseOrder());
    }

    private int precedence(FoodItemEntity product, FoodDataSource provider) {
        FoodCatalogType catalogType = product == null ? null : product.getCatalogType();
        return adapterFor(provider).precedence(catalogType, provider);
    }

    private boolean isStale(FoodProductSourceEvidenceEntity evidence) {
        int days = adapterFor(evidence.getProvider()).staleAfterDays();
        return evidence.getObservedAt().isBefore(LocalDateTime.now().minusDays(days));
    }

    private double tolerance(FoodEvidenceField field, double reference) {
        return switch (field) {
            case CALORIES -> Math.max(10.0, reference * 0.10);
            case PROTEIN, FAT, CARBS, FIBER, SUGAR, SATURATED_FAT, TRANS_FAT, SUGAR_ALCOHOL ->
                    Math.max(1.0, reference * 0.15);
            case SERVING_SIZE_GRAMS -> Math.max(2.0, reference * 0.10);
            case SODIUM -> Math.max(0.05, reference * 0.20);
            default -> Math.max(0.01, reference * 0.20);
        };
    }

    private FoodEvidenceBasis basisFor(FoodEvidenceField field) {
        return field == FoodEvidenceField.SERVING_SIZE_GRAMS ? FoodEvidenceBasis.SERVING : FoodEvidenceBasis.PER_100_G;
    }

    private void add(
            List<FoodProductSourceEvidenceEntity> target,
            FoodItemEntity product,
            String externalId,
            FoodEvidenceField field,
            Double value,
            FoodEvidenceBasis basis,
            int confidence,
            LocalDateTime observedAt,
            String sourceVersion
    ) {
        if (value == null || value.isNaN() || value.isInfinite() || value < 0.0) {
            return;
        }
        FoodProductSourceEvidenceEntity evidence = new FoodProductSourceEvidenceEntity();
        evidence.setFoodItem(product);
        evidence.setProvider(product.getDataSource());
        evidence.setExternalId(externalId);
        evidence.setFieldName(field);
        evidence.setNumericValue(value);
        evidence.setBasis(basis);
        evidence.setConfidenceScore(confidence);
        evidence.setObservedAt(observedAt);
        evidence.setSourceVersion(sourceVersion);
        evidence.setFingerprint(fingerprint(product.getDataSource(), externalId, field, value, basis, observedAt.toLocalDate(), sourceVersion));
        target.add(evidence);
    }

    private FoodSourceEvidenceAdapter adapterFor(FoodDataSource source) {
        return SOURCE_ADAPTERS.stream()
                .filter(adapter -> adapter.supports(source))
                .findFirst()
                .orElseThrow();
    }

    private String fingerprint(
            FoodDataSource provider,
            String externalId,
            FoodEvidenceField field,
            Double value,
            FoodEvidenceBasis basis,
            LocalDate observedDate,
            String sourceVersion
    ) {
        String raw = provider + "|" + externalId + "|" + field + "|" + value + "|" + basis + "|" + observedDate + "|" + firstNonBlank(sourceVersion, "unknown");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private FoodProductEvidenceDto toDto(FoodProductSourceEvidenceEntity evidence) {
        return new FoodProductEvidenceDto(
                evidence.getId(), evidence.getFoodItem().getId(), evidence.getProvider(), evidence.getExternalId(),
                evidence.getFieldName(), evidence.getNumericValue(), evidence.getBasis(), evidence.getConfidenceScore(),
                evidence.getObservedAt(), isStale(evidence), evidence.getSourceVersion(),
                evidence.getReviewerIdentity());
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "unknown";
    }
}