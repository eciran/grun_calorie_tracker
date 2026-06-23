package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.ProductQualitySuggestionDto;
import com.grun.calorietracker.dto.ProductQualitySuggestionPageDto;
import com.grun.calorietracker.dto.ProductQualitySuggestionScanResultDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodItemSearchAliasEntity;
import com.grun.calorietracker.entity.FoodProductReviewAuditEntity;
import com.grun.calorietracker.entity.ProductQualitySuggestionEntity;
import com.grun.calorietracker.enums.FoodProductReviewAuditAction;
import com.grun.calorietracker.enums.FoodSearchAliasType;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.ProductQualitySuggestionSource;
import com.grun.calorietracker.enums.ProductQualitySuggestionStatus;
import com.grun.calorietracker.enums.ProductQualitySuggestionType;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodItemSearchAliasRepository;
import com.grun.calorietracker.repository.FoodProductReviewAuditRepository;
import com.grun.calorietracker.repository.ProductQualitySuggestionRepository;
import com.grun.calorietracker.service.ProductQualitySuggestionService;
import com.grun.calorietracker.service.support.FoodProductNormalizationRules;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ProductQualitySuggestionServiceImpl implements ProductQualitySuggestionService {

    private static final int MAX_SCAN_LIMIT = 1000;
    private static final String QUALITY_SUGGESTION_SOURCE = "quality_suggestion";

    private final FoodItemRepository foodItemRepository;
    private final FoodItemSearchAliasRepository foodItemSearchAliasRepository;
    private final FoodProductReviewAuditRepository foodProductReviewAuditRepository;
    private final ProductQualitySuggestionRepository productQualitySuggestionRepository;

    @Override
    @Transactional
    public ProductQualitySuggestionScanResultDto scanSuggestions(MarketRegion marketRegion, int limit) {
        int resolvedLimit = Math.max(1, Math.min(limit, MAX_SCAN_LIMIT));
        Page<FoodItemEntity> candidates = foodItemRepository.findAll(
                buildCandidateSpecification(marketRegion),
                PageRequest.of(0, resolvedLimit, Sort.by(
                        Sort.Order.asc("id")
                ))
        );

        int created = 0;
        int skippedExisting = 0;
        List<ProductQualitySuggestionEntity> suggestionsToSave = new ArrayList<>();

        for (FoodItemEntity product : candidates.getContent()) {
            List<ProductQualitySuggestionEntity> suggestions = buildSuggestions(product);
            for (ProductQualitySuggestionEntity suggestion : suggestions) {
                boolean exists = productQualitySuggestionRepository.existsByFoodItemIdAndSuggestionTypeAndSuggestedValueAndStatus(
                        product.getId(),
                        suggestion.getSuggestionType(),
                        suggestion.getSuggestedValue(),
                        ProductQualitySuggestionStatus.OPEN
                );
                if (exists) {
                    skippedExisting++;
                    continue;
                }
                suggestionsToSave.add(suggestion);
                created++;
            }
        }

        if (!suggestionsToSave.isEmpty()) {
            productQualitySuggestionRepository.saveAll(suggestionsToSave);
        }

        return new ProductQualitySuggestionScanResultDto(candidates.getContent().size(), created, skippedExisting);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductQualitySuggestionPageDto getSuggestions(ProductQualitySuggestionStatus status, int page, int size) {
        ProductQualitySuggestionStatus resolvedStatus = status == null ? ProductQualitySuggestionStatus.OPEN : status;
        int resolvedPage = Math.max(page, 0);
        int resolvedSize = Math.max(1, Math.min(size, 100));
        Page<ProductQualitySuggestionEntity> suggestions = productQualitySuggestionRepository.findByStatusOrderByCreatedAtDesc(
                resolvedStatus,
                PageRequest.of(resolvedPage, resolvedSize)
        );
        return new ProductQualitySuggestionPageDto(
                suggestions.getContent().stream().map(this::toDto).toList(),
                suggestions.getNumber(),
                suggestions.getSize(),
                suggestions.getTotalElements(),
                suggestions.getTotalPages()
        );
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {"foodProductById", "foodProductByBarcode", "foodProductSearch"}, allEntries = true)
    public ProductQualitySuggestionDto acceptSuggestion(Long suggestionId, String reviewedBy) {
        ProductQualitySuggestionEntity suggestion = findOpenSuggestion(suggestionId);
        ProductQualitySuggestionType suggestionType = suggestion.getSuggestionType();
        if (suggestionType == ProductQualitySuggestionType.NAME_CLEANUP) {
            applyNameCleanupSuggestion(suggestion, reviewedBy);
        } else if (suggestionType == ProductQualitySuggestionType.SEARCH_ALIAS) {
            applySearchAliasSuggestion(suggestion, reviewedBy);
        } else {
            throw new IllegalArgumentException("Unsupported product quality suggestion type: " + suggestionType);
        }
        closeSuggestion(suggestion, ProductQualitySuggestionStatus.ACCEPTED, reviewedBy);
        return toDto(productQualitySuggestionRepository.save(suggestion));
    }

    @Override
    @Transactional
    public ProductQualitySuggestionDto rejectSuggestion(Long suggestionId, String reviewedBy) {
        ProductQualitySuggestionEntity suggestion = findOpenSuggestion(suggestionId);
        closeSuggestion(suggestion, ProductQualitySuggestionStatus.REJECTED, reviewedBy);
        return toDto(productQualitySuggestionRepository.save(suggestion));
    }

    private ProductQualitySuggestionEntity findOpenSuggestion(Long suggestionId) {
        ProductQualitySuggestionEntity suggestion = productQualitySuggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new ResourceNotFoundException("Product quality suggestion not found: " + suggestionId));
        if (suggestion.getStatus() != ProductQualitySuggestionStatus.OPEN) {
            throw new IllegalArgumentException("Product quality suggestion is already reviewed.");
        }
        return suggestion;
    }

    private void applyNameCleanupSuggestion(ProductQualitySuggestionEntity suggestion, String reviewedBy) {
        FoodItemEntity product = suggestion.getFoodItem();
        String suggestedValue = trimToNull(suggestion.getSuggestedValue());
        if (suggestedValue == null) {
            throw new IllegalArgumentException("Name cleanup suggestion has no suggested value.");
        }
        String oldValue = product.getName();
        if (suggestedValue.equals(oldValue)) {
            return;
        }
        product.setName(suggestedValue);
        foodItemRepository.save(product);
        saveAudit(
                product,
                reviewedBy,
                FoodProductReviewAuditAction.REVIEW_UPDATE,
                "name",
                oldValue,
                suggestedValue,
                "product quality suggestion accepted: " + suggestion.getId()
        );
    }

    private void applySearchAliasSuggestion(ProductQualitySuggestionEntity suggestion, String reviewedBy) {
        FoodItemEntity product = suggestion.getFoodItem();
        String aliasText = trimToNull(suggestion.getSuggestedValue());
        if (aliasText == null) {
            throw new IllegalArgumentException("Search alias suggestion has no suggested value.");
        }
        String normalizedAlias = FoodProductNormalizationRules.normalizeSearchAlias(aliasText);
        if (normalizedAlias == null) {
            throw new IllegalArgumentException("Search alias suggestion has no searchable value.");
        }

        FoodItemSearchAliasEntity alias = foodItemSearchAliasRepository
                .findByFoodItemIdAndNormalizedAliasAndLanguage(product.getId(), normalizedAlias, PreferredLanguage.TR)
                .orElseGet(FoodItemSearchAliasEntity::new);
        boolean isNewAlias = alias.getId() == null;
        String oldValue = isNewAlias ? null : alias.getAlias() + "|" + alias.getActive();

        alias.setFoodItem(product);
        alias.setAlias(aliasText);
        alias.setNormalizedAlias(normalizedAlias);
        alias.setLanguage(PreferredLanguage.TR);
        alias.setAliasType(FoodSearchAliasType.TRANSLATION);
        alias.setSource(QUALITY_SUGGESTION_SOURCE);
        alias.setActive(true);
        FoodItemSearchAliasEntity savedAlias = foodItemSearchAliasRepository.save(alias);

        saveAudit(
                product,
                reviewedBy,
                FoodProductReviewAuditAction.SEARCH_ALIAS_CHANGE,
                "searchAlias",
                oldValue,
                savedAlias.getAlias() + "|" + savedAlias.getActive(),
                "product quality suggestion accepted: " + suggestion.getId()
        );
    }

    private void closeSuggestion(ProductQualitySuggestionEntity suggestion, ProductQualitySuggestionStatus status, String reviewedBy) {
        suggestion.setStatus(status);
        suggestion.setReviewedAt(LocalDateTime.now());
        suggestion.setReviewedBy(normalizeActor(reviewedBy));
    }

    private void saveAudit(
            FoodItemEntity product,
            String reviewedBy,
            FoodProductReviewAuditAction action,
            String fieldName,
            Object oldValue,
            Object newValue,
            String note
    ) {
        FoodProductReviewAuditEntity audit = new FoodProductReviewAuditEntity();
        audit.setFoodItem(product);
        audit.setReviewedBy(normalizeActor(reviewedBy));
        audit.setActionType(action);
        audit.setFieldName(fieldName);
        audit.setOldValue(oldValue == null ? null : String.valueOf(oldValue));
        audit.setNewValue(newValue == null ? null : String.valueOf(newValue));
        audit.setNote(note);
        foodProductReviewAuditRepository.save(audit);
    }

    private Specification<FoodItemEntity> buildCandidateSpecification(MarketRegion marketRegion) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.or(
                    criteriaBuilder.isNull(root.get("verificationStatus")),
                    criteriaBuilder.notEqual(root.get("verificationStatus"), VerificationStatus.REJECTED)
            ));
            predicates.add(criteriaBuilder.or(
                    criteriaBuilder.isNull(root.get("isCustom")),
                    criteriaBuilder.isFalse(root.get("isCustom"))
            ));
            if (marketRegion != null) {
                predicates.add(criteriaBuilder.equal(root.get("marketRegion"), marketRegion));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private List<ProductQualitySuggestionEntity> buildSuggestions(FoodItemEntity product) {
        List<ProductQualitySuggestionEntity> suggestions = new ArrayList<>();
        addNameCleanupSuggestion(product, suggestions);
        addSearchAliasSuggestions(product, suggestions);
        return suggestions;
    }

    private void addNameCleanupSuggestion(FoodItemEntity product, List<ProductQualitySuggestionEntity> suggestions) {
        String currentName = product.getName();
        String normalizedName = FoodProductNormalizationRules.normalizeProductDisplayName(currentName);
        if (isBlank(currentName) || isBlank(normalizedName) || currentName.equals(normalizedName)) {
            return;
        }
        suggestions.add(buildSuggestion(
                product,
                ProductQualitySuggestionType.NAME_CLEANUP,
                currentName,
                normalizedName,
                "Product display name can be standardized for cleaner user-facing search results.",
                85
        ));
    }

    private void addSearchAliasSuggestions(FoodItemEntity product, List<ProductQualitySuggestionEntity> suggestions) {
        String name = normalize(product.getName());
        if (isBlank(name)) {
            return;
        }
        if (isSafeMilkAliasCandidate(product, name)) {
            addAliasSuggestion(product, suggestions, "s\u00fct", "Turkish users should be able to find plain milk products with 'sut'.");
        }
        if (containsAny(name, "cheese")) {
            addAliasSuggestion(product, suggestions, "peynir", "Turkish users should be able to find cheese products with 'peynir'.");
        }
        if (containsAny(name, "yogurt", "yoghurt")) {
            addAliasSuggestion(product, suggestions, "yo\u011furt", "Turkish users should be able to find yogurt products with 'yogurt'.");
        }
        if (containsAny(name, "bread") && !containsAny(name, "shortbread")) {
            addAliasSuggestion(product, suggestions, "ekmek", "Turkish users should be able to find bread products with 'ekmek'.");
        }
        if (name.contains("chicken breast")) {
            addAliasSuggestion(product, suggestions, "tavuk g\u00f6\u011fs\u00fc", "Turkish users should be able to find chicken breast with 'tavuk gogsu'.");
        }
    }

    private boolean isSafeMilkAliasCandidate(FoodItemEntity product, String normalizedName) {
        if (!containsAny(normalizedName, "milk")) {
            return false;
        }
        if (containsAny(normalizedName, "chocolate", "cocoa", "spread", "raisin", "biscuit", "cookie", "bar", "coin", "flavour", "flavor", "powder", "shake")) {
            return false;
        }
        return product.getCalories() == null || product.getCalories() <= 150;
    }

    private void addAliasSuggestion(FoodItemEntity product, List<ProductQualitySuggestionEntity> suggestions, String alias, String reason) {
        String normalizedAlias = FoodProductNormalizationRules.normalizeSearchAlias(alias);
        if (isBlank(normalizedAlias) || hasActiveAlias(product, normalizedAlias)) {
            return;
        }
        suggestions.add(buildSuggestion(
                product,
                ProductQualitySuggestionType.SEARCH_ALIAS,
                null,
                alias,
                reason,
                80
        ));
    }

    private boolean hasActiveAlias(FoodItemEntity product, String normalizedAlias) {
        if (product.getSearchAliases() == null || product.getSearchAliases().isEmpty()) {
            return false;
        }
        for (FoodItemSearchAliasEntity alias : product.getSearchAliases()) {
            if (Boolean.TRUE.equals(alias.getActive()) && normalizedAlias.equals(alias.getNormalizedAlias())) {
                return true;
            }
        }
        return false;
    }

    private ProductQualitySuggestionEntity buildSuggestion(
            FoodItemEntity product,
            ProductQualitySuggestionType type,
            String currentValue,
            String suggestedValue,
            String reason,
            int confidenceScore
    ) {
        ProductQualitySuggestionEntity suggestion = new ProductQualitySuggestionEntity();
        suggestion.setFoodItem(product);
        suggestion.setSuggestionType(type);
        suggestion.setSource(ProductQualitySuggestionSource.RULE_BASED);
        suggestion.setStatus(ProductQualitySuggestionStatus.OPEN);
        suggestion.setCurrentValue(currentValue);
        suggestion.setSuggestedValue(suggestedValue);
        suggestion.setReason(reason);
        suggestion.setConfidenceScore(confidenceScore);
        return suggestion;
    }

    private ProductQualitySuggestionDto toDto(ProductQualitySuggestionEntity entity) {
        FoodItemEntity product = entity.getFoodItem();
        return new ProductQualitySuggestionDto(
                entity.getId(),
                product == null ? null : product.getId(),
                product == null ? null : product.getName(),
                product == null ? null : product.getBrand(),
                entity.getSuggestionType(),
                entity.getSource(),
                entity.getStatus(),
                entity.getConfidenceScore(),
                entity.getCurrentValue(),
                entity.getSuggestedValue(),
                entity.getReason(),
                entity.getCreatedAt(),
                entity.getReviewedAt(),
                entity.getReviewedBy()
        );
    }

    private boolean containsAny(String value, String... terms) {
        for (String term : terms) {
            if (value.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        String normalized = FoodProductNormalizationRules.normalizeSearchAlias(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalizeActor(String actor) {
        String normalized = trimToNull(actor);
        return normalized == null ? "system" : normalized;
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}