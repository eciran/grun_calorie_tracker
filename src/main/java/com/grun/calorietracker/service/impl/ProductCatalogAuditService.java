package com.grun.calorietracker.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.config.ProductCatalogAuditProperties;
import com.grun.calorietracker.dto.ProductCatalogAuditStatusDto;
import com.grun.calorietracker.entity.*;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.repository.*;
import com.grun.calorietracker.service.ProductQualitySuggestionService;
import com.grun.calorietracker.service.support.FoodProductQualityIssueTracker;
import com.grun.calorietracker.service.support.GtinValidator;
import com.grun.calorietracker.service.support.FoodCatalogNamingRules;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProductCatalogAuditService {
    public static final String RULESET = "catalog-audit-r5-v1";
    private final ProductCatalogAuditStateRepository stateRepository;
    private final ProductCatalogAuditItemRepository itemRepository;
    private final ProductQualityScanRunRepository runRepository;
    private final FoodItemRepository foodRepository;
    private final FoodItemServingOptionRepository servingRepository;
    private final FoodItemLocalizationRepository localizationRepository;
    private final FoodProductQualityIssueTracker issueTracker;
    private final ProductQualitySuggestionService suggestions;
    private final ProductQualitySuggestionRepository suggestionRepository;
    private final PlatformTransactionManager transactionManager;
    private final ProductCatalogAuditProperties properties;
    private final ObjectMapper objectMapper;
    private Clock clock = Clock.systemUTC();

    public ProductCatalogAuditStatusDto status() {
        return tx().execute(status -> toDto(stateRepository.findById(1L).orElseThrow()));
    }

    public ProductCatalogAuditStatusDto start(String actor) {
        return start(actor, ProductQualityScanTriggerType.MANUAL);
    }

    private ProductCatalogAuditStatusDto start(String actor, ProductQualityScanTriggerType trigger) {
        return tx().execute(status -> {
            var state = lockedState();
            if ("RUNNING".equals(state.getStatus())) {
                requireRuleset(state);
                return toDto(state);
            }
            var run = new ProductQualityScanRunEntity();
            run.setSource(ProductQualitySuggestionSource.RULE_BASED);
            run.setTriggerType(trigger);
            run.setStatus(ProductQualityScanStatus.RUNNING);
            run.setEffectiveLimit(batchSize());
            run.setTriggeredBy(actor == null ? "system:catalog-audit" : actor);
            run.setStartedAt(now());
            run = runRepository.saveAndFlush(run);
            state.setScanRunId(run.getId());
            state.setRulesetVersion(RULESET);
            state.setStatus("RUNNING");
            state.setCursorId(0);
            state.setHighWaterId(foodRepository.maxCatalogAuditId());
            clearLease(state);
            stateRepository.save(state);
            return toDto(state);
        });
    }

    public ProductCatalogAuditStatusDto cancel() {
        return tx().execute(status -> {
            var state = lockedState();
            if ("RUNNING".equals(state.getStatus())) {
                state.setStatus("CANCELLED");
                clearLease(state);
                var run = runRepository.findById(state.getScanRunId()).orElseThrow();
                run.setStatus(ProductQualityScanStatus.FAILED);
                run.setCompletedAt(now());
                run.setErrorMessage("Cancelled by admin; no catalog facts were changed.");
                runRepository.save(run);
                stateRepository.save(state);
            }
            return toDto(state);
        });
    }

    public Page<ProductCatalogAuditItemEntity> items(String outcome, int page, int size) {
        if (outcome != null && !Set.of("RULES_PASSED", "REVIEW_REQUIRED", "RETRY_PENDING", "DEAD_LETTER", "EXCLUDED").contains(outcome)) {
            throw new IllegalArgumentException("Unsupported audit outcome");
        }
        return tx().execute(status -> {
            Long run = stateRepository.findById(1L).orElseThrow().getScanRunId();
            Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 100)));
            if (run == null) return Page.empty(pageable);
            return outcome == null ? itemRepository.findByScanRunIdOrderByProductIdAsc(run, pageable)
                    : itemRepository.findByScanRunIdAndStatusOrderByProductIdAsc(run, outcome, pageable);
        });
    }

    public ProductCatalogAuditStatusDto step() {
        String owner = UUID.randomUUID().toString();
        boolean claimed = Boolean.TRUE.equals(tx().execute(status -> {
            var state = lockedState();
            if (!"RUNNING".equals(state.getStatus()) || leaseActive(state)) return false;
            requireRuleset(state);
            state.setLeaseOwner(owner);
            state.setLeaseUntil(now().plusSeconds(Math.max(60, Math.min(properties.getLease().toSeconds(), 600))));
            stateRepository.save(state);
            return true;
        }));
        if (!claimed) return status();

        long budget = Math.max(1, Math.min(properties.getTimeBudget().toSeconds(), 30));
        long deadline = System.nanoTime() + Duration.ofSeconds(budget).toNanos();
        try {
            // Retries have a reserved minority budget so a broken product cannot starve fresh IDs.
            List<Long> ids = tx().execute(status -> {
                var state = lockedState();
                if (!owns(state, owner)) return List.of();
                int retryLimit = Math.max(1, batchSize() / 4);
                var retryIds = itemRepository.dueRetryIds(state.getScanRunId(), now(), PageRequest.of(0, retryLimit));
                var selected = new ArrayList<>(retryIds);
                selected.addAll(foodRepository.findCatalogAuditIds(state.getCursorId(), state.getHighWaterId(),
                        PageRequest.of(0, batchSize() - retryIds.size())));
                return selected;
            });
            for (Long id : ids) {
                if (System.nanoTime() >= deadline) break;
                try {
                    if (!Boolean.TRUE.equals(tx().execute(status -> inspect(id, owner)))) break;
                } catch (RuntimeException failure) {
                    // The failed product transaction is already rolled back; persist failure separately.
                    if (!Boolean.TRUE.equals(tx().execute(status -> recordFailure(id, owner, failure)))) break;
                }
            }
            tx().executeWithoutResult(status -> finishIfExhausted(owner));
        } finally {
            tx().executeWithoutResult(status -> {
                var state = lockedState();
                if (owner.equals(state.getLeaseOwner())) {
                    clearLease(state);
                    stateRepository.save(state);
                }
            });
        }
        return status();
    }

    public void scheduledStep() {
        if (!properties.isEnabled() || inDowntime()) return;
        var current = status();
        if ("READY".equals(current.status()) || ("COMPLETED".equals(current.status())
                && (current.lastCompletedAt() == null || !now().isBefore(current.lastCompletedAt().plus(properties.getRepeatInterval()))))) {
            start("system:catalog-audit", ProductQualityScanTriggerType.SCHEDULED);
        }
        // FAILED/CANCELLED sweeps need an explicit admin restart, not an infinite failure loop.
        step();
    }

    public boolean inDowntime() {
        LocalTime time = ZonedDateTime.now(clock.withZone(ZoneId.of(properties.getZone()))).toLocalTime();
        LocalTime start = properties.getDowntimeStart();
        LocalTime end = properties.getDowntimeEnd();
        if (start.equals(end)) return false;
        return start.isBefore(end) ? !time.isBefore(start) && time.isBefore(end)
                : !time.isBefore(start) || time.isBefore(end);
    }

    private boolean inspect(Long id, String owner) {
        var state = lockedState();
        if (!owns(state, owner)) return false;
        var product = foodRepository.lockCatalogAuditProduct(id).orElse(null);
        if (product == null) {
            state.setCursorId(Math.max(state.getCursorId(), id));
            stateRepository.save(state);
            return true;
        }
        var item = currentItem(id, state);
        boolean firstAttempt = item.getAttempts() == 0;
        item.setAttempts(item.getAttempts() + 1);
        item.setLastScannedAt(now());
        item.setNextAttemptAt(null);
        item.setLastErrorCode(null);
        var run = runRepository.findById(state.getScanRunId()).orElseThrow();
        if (firstAttempt) run.setScannedProducts(run.getScannedProducts() + 1);
        if (Boolean.TRUE.equals(product.getIsCustom())) {
            item.setStatus("EXCLUDED");
            item.setFindingsJson("[\"CUSTOM_FOOD\"]");
        } else {
            var options = servingRepository.findByFoodItemId(product.getId());
            var localizations = localizationRepository.findByFoodItemIdOrderByLanguageAsc(product.getId());
            var findings = findings(product, options);
            findings.addAll(FoodCatalogNamingRules.findings(product, localizations));
            issueTracker.syncReviewIssues(product, options, "system:catalog-audit");
            int created = suggestions.createRuleSuggestionsForAudit(product);
            if (created > 0 || suggestionRepository.existsByFoodItemIdAndStatus(product.getId(), ProductQualitySuggestionStatus.OPEN)) {
                findings.add("OPEN_SUGGESTIONS");
            }
            run.setCreatedSuggestions(run.getCreatedSuggestions() + created);
            item.setStatus(findings.isEmpty() ? "RULES_PASSED" : "REVIEW_REQUIRED");
            item.setFindingsJson(json(findings));
            item.setInputFingerprint(fingerprint(product, options, localizations));
        }
        itemRepository.saveAndFlush(item);
        runRepository.save(run);
        state.setCursorId(Math.max(state.getCursorId(), id));
        stateRepository.save(state);
        return true;
    }

    private boolean recordFailure(Long id, String owner, RuntimeException failure) {
        var state = lockedState();
        if (!owns(state, owner)) return false;
        if (foodRepository.existsById(id)) {
            var item = currentItem(id, state);
            var run = runRepository.findById(state.getScanRunId()).orElseThrow();
            if (item.getAttempts() == 0) run.setScannedProducts(run.getScannedProducts() + 1);
            item.setAttempts(item.getAttempts() + 1);
            boolean dead = item.getAttempts() >= Math.max(1, Math.min(properties.getMaxAttempts(), 5));
            item.setStatus(dead ? "DEAD_LETTER" : "RETRY_PENDING");
            item.setLastScannedAt(now());
            long delay = Math.max(1, Math.min(properties.getRetryDelay().toSeconds(), 3600)) * (1L << (item.getAttempts() - 1));
            item.setNextAttemptAt(dead ? null : now().plusSeconds(delay));
            // Exception messages can contain SQL, user input or credentials; store only the class.
            item.setLastErrorCode(failure.getClass().getSimpleName());
            itemRepository.saveAndFlush(item);
            runRepository.save(run);
        }
        state.setCursorId(Math.max(state.getCursorId(), id));
        stateRepository.save(state);
        return true;
    }

    private void finishIfExhausted(String owner) {
        var state = lockedState();
        if (!owns(state, owner)) return;
        if (!foodRepository.findCatalogAuditIds(state.getCursorId(), state.getHighWaterId(), PageRequest.of(0, 1)).isEmpty()) return;
        state.setCursorId(state.getHighWaterId());
        if (count(state, "RETRY_PENDING") == 0) {
            boolean failed = count(state, "DEAD_LETTER") > 0;
            state.setStatus(failed ? "FAILED" : "COMPLETED");
            state.setLastCompletedAt(now());
            var run = runRepository.findById(state.getScanRunId()).orElseThrow();
            run.setStatus(failed ? ProductQualityScanStatus.FAILED : ProductQualityScanStatus.COMPLETED);
            run.setCompletedAt(now());
            if (failed) run.setErrorMessage("Catalog scan contains dead letters; explicit restart/review required.");
            runRepository.save(run);
        }
        stateRepository.save(state);
    }

    private Set<String> findings(FoodItemEntity product, List<FoodItemServingOptionEntity> options) {
        var result = new TreeSet<String>();
        issueTracker.deriveReviewIssues(product, options).keySet().forEach(issue -> result.add(issue.name()));
        if (product.getName() == null || product.getName().isBlank()) result.add("MISSING_NAME");
        if (product.getDisplayName() == null || product.getDisplayName().isBlank()) result.add("MISSING_DISPLAY_NAME");
        if (product.getNutritionReferenceUnit() == null) result.add("MISSING_REFERENCE_UNIT");
        if (product.getCatalogType() != FoodCatalogType.BRANDED_PRODUCT && product.getPreparationState() == null) result.add("MISSING_PREPARATION_STATE");
        String barcode = product.getNormalizedBarcode() == null ? product.getBarcode() : product.getNormalizedBarcode();
        if (product.getCatalogType() == FoodCatalogType.BRANDED_PRODUCT && !GtinValidator.isValid(barcode)) result.add("INVALID_GTIN");
        if (product.getDataSource() == com.grun.calorietracker.enums.FoodDataSource.USDA_FOODDATA) {
            if (product.getSourceDataType() == null) {
                result.add("MISSING_USDA_DATA_TYPE");
            } else {
                try {
                    com.grun.calorietracker.service.support.UsdaCatalogClassificationRules.resolve(
                            product.getSourceDataType(), product.getCatalogType(), barcode != null && !barcode.isBlank(),
                            product.getSourceKey(), product.getName());
                } catch (IllegalArgumentException exception) {
                    result.add("USDA_CLASSIFICATION_CONFLICT");
                }
            }
        }
        try {
            String expected = com.grun.calorietracker.service.support.FoodProductCanonicalKeyRules.resolve(product);
            if (product.getFoodConceptKey() != null && !Objects.equals(expected, product.getCanonicalFoodKey())) {
                result.add("CONCEPT_CANONICAL_KEY_DRIFT");
            }
        } catch (IllegalArgumentException exception) {
            result.add("INVALID_CONCEPT_IDENTITY");
        }
        var liquid = com.grun.calorietracker.service.support.FoodLiquidUnitClassifier.classify(
                product.getSourceCategoryTags(), product.getNutritionReferenceUnit());
        if (liquid == com.grun.calorietracker.service.support.FoodLiquidUnitClassifier.Decision.MASS_VOLUME_BRIDGE_REQUIRED) {
            result.add("LIQUID_MASS_BASIS_SOURCE_REVIEW");
        } else if (liquid == com.grun.calorietracker.service.support.FoodLiquidUnitClassifier.Decision.FORM_CONFLICT_REVIEW) {
            result.add("LIQUID_FORM_CONFLICT_REVIEW");
        }
        if (product.getNutritionReferenceUnit() == FoodNutritionReferenceUnit.PER_100ML
                && product.getServingSizeGrams() != null) result.add("LEGACY_MASS_ON_VOLUME_PRODUCT_REVIEW");
        if (options.stream().filter(option -> Boolean.TRUE.equals(option.getIsDefault())
                && !Boolean.FALSE.equals(option.getUserVisible())).count() > 1) result.add("MULTIPLE_VISIBLE_SERVING_DEFAULTS");
        for (var option : options) {
            if (Boolean.TRUE.equals(option.getUserVisible()) && option.getQualityStatus() == FoodServingOptionQualityStatus.VERIFIED
                    && !com.grun.calorietracker.service.support.FoodPortionUnitResolver.supportsOption(product, option)) {
                result.add("VISIBLE_SERVING_NOT_CALCULABLE");
            }
            if (option.getQuantity() == null || !Double.isFinite(option.getQuantity()) || option.getQuantity() <= 0) result.add("INVALID_SERVING_QUANTITY");
            if (option.getGramWeight() != null && (!Double.isFinite(option.getGramWeight()) || option.getGramWeight() <= 0)) result.add("INVALID_SERVING_MASS");
            if (option.getMlVolume() != null && (!Double.isFinite(option.getMlVolume()) || option.getMlVolume() <= 0)) result.add("INVALID_SERVING_VOLUME");
            if (option.getMlVolume() != null && product.getNutritionReferenceUnit() == FoodNutritionReferenceUnit.PER_100G
                    && (option.getGramWeight() == null || option.getGramWeight() <= 0)) result.add("VOLUME_WITHOUT_MASS_BRIDGE");
            if (option.getGramWeight() != null && product.getNutritionReferenceUnit() == FoodNutritionReferenceUnit.PER_100ML
                    && (option.getMlVolume() == null || option.getMlVolume() <= 0)) result.add("MASS_WITHOUT_VOLUME_BRIDGE");
        }
        return result;
    }

    private String fingerprint(FoodItemEntity product, List<FoodItemServingOptionEntity> options, List<FoodItemLocalizationEntity> localizations) {
        Map<String, Object> input = new TreeMap<>();
        input.put("ruleset", RULESET);
        var bean = new org.springframework.beans.BeanWrapperImpl(product);
        for (String field : List.of("name", "displayName", "brand", "sourceKey", "dataSource", "lastExternalSyncAt",
                "catalogType", "sourceDataType", "foodConceptKey", "compositionVariantKey", "canonicalFoodKey",
                "preparationState", "nutritionReferenceUnit", "nutritionBasis", "marketRegion", "normalizedBarcode", "barcode", "confidenceScore",
                "calories", "protein", "fat", "carbs", "fiber", "sugar", "sodium", "potassium", "calcium",
                "iron", "magnesium", "zinc", "cholesterol", "vitaminA", "vitaminC", "vitaminD", "vitaminE",
                "vitaminB12", "saturatedFat", "transFat", "sugarAlcohol", "servingSizeGrams", "servingUnit")) {
            input.put(field, bean.getPropertyValue(field));
        }
        input.put("sourceCategories", new TreeSet<>(product.getSourceCategoryTags()));
        input.put("namingRuleset", FoodCatalogNamingRules.VERSION);
        input.put("reviewedSourceNaming", com.grun.calorietracker.service.support.FoodSourceNameRules.reviewedSource(product.getSourceKey()).orElse(null));
        input.put("reviewedBrandDisplay", com.grun.calorietracker.service.support.FoodProductNormalizationRules.normalizeBrandDisplayName(product.getBrand()));
        input.put("localizations", localizations.stream().sorted(Comparator.comparing(FoodItemLocalizationEntity::getLanguage))
                .map(localization -> Arrays.asList(localization.getLanguage(), localization.getDisplayName(), localization.getShortDisplayName(), localization.getActive(), localization.getSource())).toList());
        var brand = product.getCanonicalBrand();
        input.put("canonicalBrand", brand == null ? null : Arrays.asList(brand.getId(), brand.getCanonicalName(), brand.getStatus(), brand.isVerified()));
        input.put("servings", options.stream()
                .sorted(Comparator.comparing(FoodItemServingOptionEntity::getId))
                .map(option -> Arrays.asList(option.getId(), option.getLabel(), option.getSource(), option.getIsDefault(), option.getQuantity(), option.getGramWeight(),
                        option.getMlVolume(), option.getUnitType(), option.getQualityStatus(), option.getUserVisible())).toList());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json(input).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (com.fasterxml.jackson.core.JsonProcessingException exception) { throw new IllegalStateException("Audit serialization failed", exception); }
    }

    private ProductCatalogAuditItemEntity currentItem(Long id, ProductCatalogAuditStateEntity state) {
        var item = itemRepository.findById(id).orElseGet(ProductCatalogAuditItemEntity::new);
        if (!Objects.equals(state.getScanRunId(), item.getScanRunId())) {
            item = new ProductCatalogAuditItemEntity();
            item.setProductId(id);
            item.setScanRunId(state.getScanRunId());
            item.setRulesetVersion(RULESET);
        }
        return item;
    }

    private ProductCatalogAuditStateEntity lockedState() { return stateRepository.lockState().orElseThrow(); }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    private int batchSize() { return Math.max(2, Math.min(properties.getBatchSize(), 1000)); }
    private boolean leaseActive(ProductCatalogAuditStateEntity state) { return state.getLeaseOwner() != null && state.getLeaseUntil() != null && state.getLeaseUntil().isAfter(now()); }
    private boolean owns(ProductCatalogAuditStateEntity state, String owner) { return "RUNNING".equals(state.getStatus()) && owner.equals(state.getLeaseOwner()) && leaseActive(state); }
    private void clearLease(ProductCatalogAuditStateEntity state) { state.setLeaseOwner(null); state.setLeaseUntil(null); }
    private void requireRuleset(ProductCatalogAuditStateEntity state) { if (!RULESET.equals(state.getRulesetVersion())) throw new IllegalArgumentException("Ruleset changed; cancel and restart the audit explicitly"); }
    private long count(ProductCatalogAuditStateEntity state, String outcome) { return state.getScanRunId() == null ? 0 : itemRepository.countByScanRunIdAndStatus(state.getScanRunId(), outcome); }
    private ProductCatalogAuditStatusDto toDto(ProductCatalogAuditStateEntity state) {
        return new ProductCatalogAuditStatusDto(state.getScanRunId(), state.getStatus(), state.getRulesetVersion(), state.getCursorId(), state.getHighWaterId(),
                count(state, "RULES_PASSED"), count(state, "REVIEW_REQUIRED"), count(state, "RETRY_PENDING"), count(state, "DEAD_LETTER"),
                leaseActive(state), state.getLeaseUntil(), state.getLastCompletedAt());
    }
    private TransactionTemplate tx() {
        var tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setTimeout(30);
        return tx;
    }
}
