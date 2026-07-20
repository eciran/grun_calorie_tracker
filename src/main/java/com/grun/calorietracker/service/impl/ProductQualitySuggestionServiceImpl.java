package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.config.AiProperties;
import com.grun.calorietracker.dto.AdminProductQualityAiValidationRequestDto;
import com.grun.calorietracker.dto.AdminProductQualityAiValidationResultDto;
import com.grun.calorietracker.dto.AiProductQualityValidationRequestDto;
import com.grun.calorietracker.dto.AiProductQualityValidationResponseDto;
import com.grun.calorietracker.dto.ProductQualityScanRunDto;
import com.grun.calorietracker.dto.ProductQualityAiSettingsDto;
import com.grun.calorietracker.dto.ProductQualityAiSettingsUpdateRequestDto;
import com.grun.calorietracker.repository.ProductQualityScanRunItemRepository;
import com.grun.calorietracker.enums.ProductQualityScanItemStatus;
import com.grun.calorietracker.entity.ProductQualityScanRunItemEntity;
import com.grun.calorietracker.dto.ProductQualityScanRunItemDto;
import com.grun.calorietracker.dto.ProductQualityScanRunDetailDto;
import com.grun.calorietracker.dto.ProductQualityScanRunPageDto;
import com.grun.calorietracker.dto.ProductQualitySuggestionDto;
import com.grun.calorietracker.dto.ProductQualitySuggestionPageDto;
import com.grun.calorietracker.dto.ProductQualitySuggestionScanResultDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodItemLocalizationEntity;
import com.grun.calorietracker.entity.FoodItemServingOptionEntity;
import com.grun.calorietracker.entity.FoodItemServingOptionLocalizationEntity;
import com.grun.calorietracker.entity.FoodProductQualityIssueEntity;
import com.grun.calorietracker.entity.FoodItemSearchAliasEntity;
import com.grun.calorietracker.entity.FoodProductReviewAuditEntity;
import com.grun.calorietracker.entity.ProductQualityScanRunEntity;
import com.grun.calorietracker.entity.ProductQualityAiSettingsEntity;
import com.grun.calorietracker.entity.ProductQualitySuggestionEntity;
import com.grun.calorietracker.enums.FoodProductReviewAuditAction;
import com.grun.calorietracker.enums.AdminAuditActionType;
import com.grun.calorietracker.enums.AdminAuditTargetType;
import com.grun.calorietracker.enums.FoodSearchAliasType;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.ProductQualityScanStatus;
import com.grun.calorietracker.enums.ProductQualityScanTriggerType;
import com.grun.calorietracker.enums.ProductQualitySuggestionSource;
import com.grun.calorietracker.enums.ProductQualitySuggestionStatus;
import com.grun.calorietracker.enums.ProductQualitySuggestionType;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodItemLocalizationRepository;
import com.grun.calorietracker.repository.FoodItemServingOptionRepository;
import com.grun.calorietracker.repository.FoodItemServingOptionLocalizationRepository;
import com.grun.calorietracker.repository.FoodProductQualityIssueRepository;
import com.grun.calorietracker.repository.FoodCanonicalResolutionRepository;
import com.grun.calorietracker.repository.FoodItemSearchAliasRepository;
import com.grun.calorietracker.repository.FoodProductReviewAuditRepository;
import com.grun.calorietracker.repository.ProductQualityScanRunRepository;
import com.grun.calorietracker.repository.ProductQualityAiSettingsRepository;
import com.grun.calorietracker.repository.ProductQualitySuggestionRepository;
import com.grun.calorietracker.service.AiMealDraftProviderClient;
import com.grun.calorietracker.service.AdminAuditService;
import com.grun.calorietracker.service.ProductQualitySuggestionService;
import com.grun.calorietracker.service.FoodProductEvidenceService;
import com.grun.calorietracker.service.support.FoodProductNormalizationRules;
import com.grun.calorietracker.service.support.ProductQualitySuggestionReconciliationService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProductQualitySuggestionServiceImpl implements ProductQualitySuggestionService {

    private static final int MAX_MANUAL_SCAN_LIMIT = 500;
    private static final int MAX_SCHEDULED_SCAN_LIMIT = 250;
    private static final int MAX_AI_SELECTED_VALIDATION_LIMIT = 25;
    private static final String AI_PRODUCT_CONTEXT_SCHEMA_VERSION = "product_quality_context_v2";
    private static final String AI_PRODUCT_PROMPT_VERSION = "product_quality_prompt_v2";
    private static final String AI_PRODUCT_RESPONSE_SCHEMA_VERSION = "product_quality_response_v2";
    private static final String QUALITY_SUGGESTION_SOURCE = "quality_suggestion";
    private static final String QUALITY_VALIDATION_NOTE = "No open rule-based quality suggestions were produced for the current validation rules.";
    private static final Set<String> SAFE_NUTRITION_FIELDS = Set.of(
            "calories", "protein", "fat", "carbs", "fiber", "sugar", "sodium", "potassium",
            "cholesterol", "calcium", "iron", "magnesium", "zinc", "vitamina", "vitaminc",
            "vitamind", "vitamine", "vitaminb12", "saturatedfat", "transfat", "sugaralcohol"
    );
    private static final Set<String> SAFE_SERVING_FIELDS = Set.of("servingsizegrams", "servingunit");

    private final FoodItemRepository foodItemRepository;
    private final FoodItemSearchAliasRepository foodItemSearchAliasRepository;
    private final FoodItemLocalizationRepository foodItemLocalizationRepository;
    private final FoodItemServingOptionRepository foodItemServingOptionRepository;
    private final FoodItemServingOptionLocalizationRepository foodItemServingOptionLocalizationRepository;
    private final FoodProductQualityIssueRepository foodProductQualityIssueRepository;
    private final FoodCanonicalResolutionRepository foodCanonicalResolutionRepository;
    private final FoodProductEvidenceService foodProductEvidenceService;
    private final ProductQualitySuggestionReconciliationService suggestionReconciliationService;
    private final FoodProductReviewAuditRepository foodProductReviewAuditRepository;
    private final ProductQualitySuggestionRepository productQualitySuggestionRepository;
    private final ProductQualityAiSettingsRepository productQualityAiSettingsRepository;
    private final ProductQualityScanRunRepository productQualityScanRunRepository;
    private final ProductQualityScanRunItemRepository productQualityScanRunItemRepository;
    private final AdminAuditService adminAuditService;
    private final AiProperties properties;
    private final List<AiMealDraftProviderClient> aiMealDraftProviderClients;

    @Override
    @Transactional
    public ProductQualitySuggestionScanResultDto scanSuggestions(
            MarketRegion marketRegion,
            int limit,
            boolean forceRescan,
            ProductQualityScanTriggerType triggerType,
            String triggeredBy
    ) {
        ProductQualityScanTriggerType resolvedTriggerType = triggerType == null ? ProductQualityScanTriggerType.MANUAL : triggerType;
        int resolvedLimit = resolveScanLimit(limit, resolvedTriggerType);
        String actor = normalizeActor(triggeredBy);

        ProductQualityScanRunEntity run = new ProductQualityScanRunEntity();
        run.setSource(ProductQualitySuggestionSource.RULE_BASED);
        run.setTriggerType(resolvedTriggerType);
        run.setStatus(ProductQualityScanStatus.RUNNING);
        run.setMarketRegion(marketRegion);
        run.setRequestedLimit(limit);
        run.setEffectiveLimit(resolvedLimit);
        run.setForceRescan(forceRescan);
        run.setTriggeredBy(actor);
        run = productQualityScanRunRepository.save(run);

        Page<FoodItemEntity> candidates = foodItemRepository.findAll(
                buildCandidateSpecification(marketRegion, forceRescan),
                PageRequest.of(0, resolvedLimit, Sort.by(Sort.Order.asc("id")))
        );

        int created = 0;
        int skippedExisting = 0;
        int validatedProducts = 0;
        List<ProductQualitySuggestionEntity> suggestionsToSave = new ArrayList<>();
                List<ProductQualityScanRunItemEntity> runItemsToSave = new ArrayList<>();
List<FoodItemEntity> productsToMarkValidated = new ArrayList<>();

        List<FoodItemEntity> candidateProducts = new ArrayList<>(candidates.getContent());
        for (FoodItemEntity product : candidateProducts) {
            List<ProductQualitySuggestionEntity> suggestions = buildSuggestions(product);
            boolean hasOpenOrNewSuggestion = false;
            for (ProductQualitySuggestionEntity suggestion : suggestions) {
                boolean exists = productQualitySuggestionRepository.existsOpenDedupe(
                        product.getId(),
                        suggestion.getSuggestionType(),
                        suggestion.getFieldName(),
                        suggestion.getSuggestedValue(),
                        ProductQualitySuggestionStatus.OPEN
                );
                if (exists) {
                    skippedExisting++;
                    runItemsToSave.add(toRunItem(run, product, suggestion, ProductQualityScanItemStatus.SKIPPED_EXISTING, "Matching open suggestion already exists."));
                    hasOpenOrNewSuggestion = true;
                    continue;
                }
                suggestionsToSave.add(suggestion);
                runItemsToSave.add(toRunItem(run, product, suggestion, ProductQualityScanItemStatus.SUGGESTION_CREATED, "New quality suggestion created."));
                created++;
                hasOpenOrNewSuggestion = true;
            }
            if (!hasOpenOrNewSuggestion) {
                markProductQualityValidated(product, actor);
                productsToMarkValidated.add(product);
                runItemsToSave.add(toValidatedRunItem(run, product, ProductQualityScanItemStatus.VALIDATED, QUALITY_VALIDATION_NOTE));
                validatedProducts++;
            }
        }

        if (!suggestionsToSave.isEmpty()) {
            productQualitySuggestionRepository.saveAll(suggestionsToSave);
        }
        if (!productsToMarkValidated.isEmpty()) {
            foodItemRepository.saveAll(productsToMarkValidated);
        }
        if (!runItemsToSave.isEmpty()) {
            productQualityScanRunItemRepository.saveAll(runItemsToSave);
        }

        run.setStatus(ProductQualityScanStatus.COMPLETED);
        run.setScannedProducts(candidateProducts.size());
        run.setCreatedSuggestions(created);
        run.setSkippedExistingSuggestions(skippedExisting);
        run.setSkippedPreviouslyValidatedProducts(0);
        run.setValidatedProducts(validatedProducts);
        run.setCompletedAt(LocalDateTime.now());
        productQualityScanRunRepository.save(run);

        return new ProductQualitySuggestionScanResultDto(
                run.getId(),
                candidateProducts.size(),
                created,
                skippedExisting,
                0,
                validatedProducts,
                resolvedLimit,
                forceRescan
        );
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
    @Transactional(readOnly = true)
    public ProductQualityScanRunPageDto getScanRuns(int page, int size) {
        int resolvedPage = Math.max(page, 0);
        int resolvedSize = Math.max(1, Math.min(size, 100));
        Page<ProductQualityScanRunEntity> runs = productQualityScanRunRepository.findAllByOrderByStartedAtDesc(
                PageRequest.of(resolvedPage, resolvedSize)
        );
        return new ProductQualityScanRunPageDto(
                runs.getContent().stream().map(this::toScanRunDto).toList(),
                runs.getNumber(),
                runs.getSize(),
                runs.getTotalElements(),
                runs.getTotalPages()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public ProductQualityScanRunDetailDto getScanRunDetail(Long scanRunId) {
        ProductQualityScanRunEntity run = productQualityScanRunRepository.findById(scanRunId)
                .orElseThrow(() -> new ResourceNotFoundException("Product quality scan run not found: " + scanRunId));
        List<ProductQualityScanRunItemDto> items = productQualityScanRunItemRepository
                .findByScanRunIdOrderByIdAsc(scanRunId)
                .stream()
                .map(this::toRunItemDto)
                .toList();
        if (items.isEmpty()) {
            items = fallbackValidatedItemsForLegacyRun(run);
        }
        return new ProductQualityScanRunDetailDto(toScanRunDto(run), items);
    }
    @Override
    @Transactional
    public AdminProductQualityAiValidationResultDto validateSelectedWithAi(
            AdminProductQualityAiValidationRequestDto request,
            String triggeredBy
    ) {
        String actor = normalizeActor(triggeredBy);
        boolean forceRescan = Boolean.TRUE.equals(request == null ? null : request.getForceRescan());
        ProductQualityAiSettingsEntity settings = loadAiSettings();
        if (!settings.isEnabled()) {
            throw new IllegalArgumentException("AI product quality validation is disabled by admin settings.");
        }
        if (forceRescan && !settings.isForceRescanAllowed()) {
            throw new IllegalArgumentException("Force rescan is disabled by admin AI quality settings.");
        }
        List<Long> productIds = resolveSelectedProductIds(request, settings.getMaxProductsPerRun());
        int requestedProducts = productIds.size();
        int quotaRemaining = Math.min(dailyRemaining(settings), monthlyRemaining(settings));
        if (quotaRemaining <= 0) {
            throw new IllegalArgumentException("AI product quality validation quota is exhausted for the current period.");
        }
        int requestedLimit = request == null || request.getLimit() == null
                ? settings.getMaxProductsPerRun()
                : request.getLimit();
        int effectiveLimit = Math.max(1, Math.min(Math.min(requestedLimit, settings.getMaxProductsPerRun()), quotaRemaining));

        List<FoodItemEntity> products = foodItemRepository.findAllById(productIds).stream()
                .filter(product -> product.getVerificationStatus() != VerificationStatus.REJECTED)
                .filter(product -> product.getIsCustom() == null || Boolean.FALSE.equals(product.getIsCustom()))
                .limit(effectiveLimit)
                .toList();

        ProductQualityScanRunEntity run = new ProductQualityScanRunEntity();
        run.setSource(ProductQualitySuggestionSource.AI_ASSISTED);
        run.setTriggerType(ProductQualityScanTriggerType.MANUAL);
        run.setStatus(ProductQualityScanStatus.RUNNING);
        run.setRequestedLimit(requestedProducts);
        run.setEffectiveLimit(effectiveLimit);
        run.setForceRescan(forceRescan);
        run.setTriggeredBy(actor);
        run = productQualityScanRunRepository.save(run);

        int created = 0;
        int skippedExisting = 0;
        int skippedValidated = 0;
        int validated = 0;
        List<ProductQualitySuggestionEntity> suggestionsToSave = new ArrayList<>();
                List<ProductQualityScanRunItemEntity> runItemsToSave = new ArrayList<>();
List<FoodItemEntity> productsToMarkValidated = new ArrayList<>();

        for (FoodItemEntity product : products) {
            if (!forceRescan
                    && product.getQualityValidatedAt() != null
                    && (product.getLastReviewedAt() == null || !product.getLastReviewedAt().isAfter(product.getQualityValidatedAt()))) {
                skippedValidated++;
                runItemsToSave.add(toValidatedRunItem(run, product, ProductQualityScanItemStatus.SKIPPED_PREVIOUSLY_VALIDATED, "Product was already quality validated and forceRescan=false."));
                continue;
            }

            AiProductQualityValidationRequestDto validationRequest = toAiValidationRequest(product);
            AiProductQualityValidationResponseDto response = activeProvider().validateProductQuality(validationRequest);
            validateAiResponse(response);
            List<AiProductQualityValidationResponseDto.AiProductQualityIssueDto> issues =
                    response.getIssues() == null ? List.of() : response.getIssues();
            boolean hasIssue = false;

            for (AiProductQualityValidationResponseDto.AiProductQualityIssueDto issue : issues) {
                ProductQualitySuggestionEntity suggestion = buildAiSuggestion(
                        product,
                        issue,
                        validationRequest.getEvidenceComparisons()
                );
                if (suggestion == null) {
                    continue;
                }
                boolean exists = productQualitySuggestionRepository.existsOpenDedupe(
                        product.getId(),
                        suggestion.getSuggestionType(),
                        suggestion.getFieldName(),
                        suggestion.getSuggestedValue(),
                        ProductQualitySuggestionStatus.OPEN
                );
                if (exists) {
                    skippedExisting++;
                    runItemsToSave.add(toRunItem(run, product, suggestion, ProductQualityScanItemStatus.SKIPPED_EXISTING, "Matching open suggestion already exists."));
                    hasIssue = true;
                    continue;
                }
                suggestionsToSave.add(suggestion);
                runItemsToSave.add(toRunItem(run, product, suggestion, ProductQualityScanItemStatus.SUGGESTION_CREATED, "New AI-assisted quality suggestion created."));
                created++;
                hasIssue = true;
            }

            if (!hasIssue) {
                product.setQualityValidatedAt(LocalDateTime.now());
                product.setQualityValidatedBy(actor);
                product.setQualityValidationSource(ProductQualitySuggestionSource.AI_ASSISTED);
                product.setQualityValidationNotes(response.getSummary() == null
                        ? "AI-assisted validation found no reviewable issue."
                        : response.getSummary());
                productsToMarkValidated.add(product);
                runItemsToSave.add(toValidatedRunItem(run, product, ProductQualityScanItemStatus.VALIDATED, product.getQualityValidationNotes()));
                validated++;
            }
        }

        if (!suggestionsToSave.isEmpty()) {
            productQualitySuggestionRepository.saveAll(suggestionsToSave);
        }
        if (!productsToMarkValidated.isEmpty()) {
            foodItemRepository.saveAll(productsToMarkValidated);
        }
        if (!runItemsToSave.isEmpty()) {
            productQualityScanRunItemRepository.saveAll(runItemsToSave);
        }

        run.setStatus(ProductQualityScanStatus.COMPLETED);
        run.setScannedProducts(products.size());
        run.setCreatedSuggestions(created);
        run.setSkippedExistingSuggestions(skippedExisting);
        run.setSkippedPreviouslyValidatedProducts(skippedValidated);
        run.setValidatedProducts(validated);
        run.setCompletedAt(LocalDateTime.now());
        productQualityScanRunRepository.save(run);

        return new AdminProductQualityAiValidationResultDto(
                run.getId(),
                requestedProducts,
                validated,
                created,
                skippedExisting,
                skippedValidated,
                effectiveLimit
        );
    }
    @Override
    @Transactional(readOnly = true)
    public ProductQualityAiSettingsDto getAiSettings() {
        return toAiSettingsDto(loadAiSettings());
    }

    @Override
    @Transactional
    public ProductQualityAiSettingsDto updateAiSettings(ProductQualityAiSettingsUpdateRequestDto request, String updatedBy) {
        ProductQualityAiSettingsEntity settings = loadAiSettings();
        Map<String, Object> before = toAiSettingsAuditMap(settings);
        if (request != null) {
            if (request.getEnabled() != null) {
                settings.setEnabled(request.getEnabled());
            }
            if (request.getMaxProductsPerRun() != null) {
                settings.setMaxProductsPerRun(Math.max(1, Math.min(request.getMaxProductsPerRun(), MAX_AI_SELECTED_VALIDATION_LIMIT)));
            }
            if (request.getDailyProductLimit() != null) {
                settings.setDailyProductLimit(Math.max(1, request.getDailyProductLimit()));
            }
            if (request.getMonthlyProductLimit() != null) {
                settings.setMonthlyProductLimit(Math.max(1, request.getMonthlyProductLimit()));
            }
            if (request.getForceRescanAllowed() != null) {
                settings.setForceRescanAllowed(request.getForceRescanAllowed());
            }
            settings.setAdminNote(trimToMax(request.getAdminNote(), 1000));
        }
        if (settings.getMonthlyProductLimit() < settings.getDailyProductLimit()) {
            throw new IllegalArgumentException("Monthly AI product quality limit must be greater than or equal to the daily limit.");
        }
        String actor = normalizeActor(updatedBy);
        settings.setUpdatedBy(actor);
        ProductQualityAiSettingsEntity saved = productQualityAiSettingsRepository.save(settings);
        adminAuditService.record(
                actor,
                AdminAuditActionType.PRODUCT_QUALITY_AI_SETTINGS_UPDATE,
                AdminAuditTargetType.PRODUCT_QUALITY_AI_SETTINGS,
                String.valueOf(ProductQualityAiSettingsEntity.SINGLETON_ID),
                before,
                toAiSettingsAuditMap(saved),
                null
        );
        return toAiSettingsDto(saved);
    }
    @Override
    @Transactional
    @CacheEvict(cacheNames = {"foodProductById", "foodProductByBarcode", "foodProductSearch"}, allEntries = true)
    public ProductQualitySuggestionDto acceptSuggestion(Long suggestionId, String reviewedBy) {
        ProductQualitySuggestionEntity suggestion = findOpenSuggestion(suggestionId);
        ProductQualitySuggestionType suggestionType = suggestion.getSuggestionType();
        validateSuggestionApplication(suggestion);
        String previousCanonicalKey = suggestion.getFoodItem().getCanonicalFoodKey();
        if (suggestionType == ProductQualitySuggestionType.NAME_CLEANUP) {
            applyNameCleanupSuggestion(suggestion, reviewedBy);
        } else if (suggestionType == ProductQualitySuggestionType.DISPLAY_NAME) {
            applyDisplayNameSuggestion(suggestion, reviewedBy);
        } else if (suggestionType == ProductQualitySuggestionType.LOCALIZATION) {
            applyLocalizationSuggestion(suggestion, reviewedBy);
        } else if (suggestionType == ProductQualitySuggestionType.SEARCH_ALIAS) {
            applySearchAliasSuggestion(suggestion, reviewedBy);
        } else {
            applyFieldSuggestion(suggestion, reviewedBy);
        }
        suggestionReconciliationService.reconcile(
                suggestion.getFoodItem(),
                previousCanonicalKey,
                normalizeActor(reviewedBy),
                suggestion.getId()
        );
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

    @Override
    @Transactional(readOnly = true)
    public com.grun.calorietracker.dto.AdminProductQualityWorkbenchDto getProductWorkbench(Long productId) {
        FoodItemEntity product = foodItemRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Food product not found: " + productId));

        List<FoodItemLocalizationEntity> localizationEntities =
                foodItemLocalizationRepository.findByFoodItemIdOrderByLanguageAsc(productId);
        List<FoodItemServingOptionEntity> servingEntities =
                foodItemServingOptionRepository.findByFoodItemIdInOrderByFoodItemIdAscIsDefaultDescLabelAsc(List.of(productId));
        List<Long> servingIds = servingEntities.stream().map(FoodItemServingOptionEntity::getId).toList();
        List<FoodItemServingOptionLocalizationEntity> servingLocalizations = servingIds.isEmpty()
                ? List.of()
                : foodItemServingOptionLocalizationRepository.findByServingOptionIdIn(servingIds);

        return new com.grun.calorietracker.dto.AdminProductQualityWorkbenchDto(
                com.grun.calorietracker.mapper.FoodItemMapper.mapEntityToDto(product),
                localizationEntities.stream().map(value ->
                        new com.grun.calorietracker.dto.AdminProductQualityWorkbenchDto.LocalizationDto(
                                value.getId(), value.getLanguage(), value.getDisplayName(), value.getShortDisplayName(),
                                value.getSource(), value.getActive()
                        )).toList(),
                foodItemSearchAliasRepository.findByFoodItemIdOrderByActiveDescLanguageAscAliasAsc(productId)
                        .stream().map(this::toAliasDto).toList(),
                servingEntities.stream().map(value -> toWorkbenchServing(value, servingLocalizations)).toList(),
                foodProductEvidenceService.buildContext(product),
                foodProductQualityIssueRepository.findByFoodItemIdOrderByResolvedAscLastDetectedAtDesc(productId)
                        .stream().map(this::toQualityIssueDto).toList(),
                productQualitySuggestionRepository.findByFoodItemIdOrderByCreatedAtDesc(productId)
                        .stream().map(this::toDto).toList(),
                loadCanonicalContext(product),
                foodProductReviewAuditRepository.findByFoodItemId(
                                productId, PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "createdAt")))
                        .stream().map(this::toReviewAuditDto).toList()
        );
    }

    private com.grun.calorietracker.dto.FoodSearchAliasDto toAliasDto(FoodItemSearchAliasEntity value) {
        return new com.grun.calorietracker.dto.FoodSearchAliasDto(
                value.getId(), value.getFoodItem().getId(), value.getAlias(), value.getNormalizedAlias(),
                value.getLanguage(), value.getAliasType(), value.getSource(), value.getActive(),
                value.getCreatedAt() == null ? null : value.getCreatedAt().toString()
        );
    }

    private com.grun.calorietracker.dto.AdminProductQualityWorkbenchDto.ServingOptionDto toWorkbenchServing(
            FoodItemServingOptionEntity value,
            List<FoodItemServingOptionLocalizationEntity> localizations
    ) {
        List<com.grun.calorietracker.dto.AdminProductQualityWorkbenchDto.ServingLocalizationDto> localized =
                localizations.stream()
                        .filter(item -> item.getServingOption().getId().equals(value.getId()))
                        .map(item -> new com.grun.calorietracker.dto.AdminProductQualityWorkbenchDto.ServingLocalizationDto(
                                item.getId(), item.getLanguage(), item.getLabel(), item.getSource(), item.getActive()
                        ))
                        .toList();
        return new com.grun.calorietracker.dto.AdminProductQualityWorkbenchDto.ServingOptionDto(
                value.getId(), value.getLabel(), value.getUnitType(), value.getQuantity(), value.getGramWeight(),
                value.getMlVolume(), value.getIsDefault(), value.getSource(), value.getQualityStatus(), localized
        );
    }

    private com.grun.calorietracker.dto.FoodProductQualityIssueDto toQualityIssueDto(FoodProductQualityIssueEntity value) {
        return new com.grun.calorietracker.dto.FoodProductQualityIssueDto(
                value.getId(), value.getFoodItem().getId(), value.getIssueType(), value.getIdentifier(), value.getReason(),
                value.getResolved(), value.getFirstDetectedAt(), value.getLastDetectedAt(), value.getResolvedAt(), value.getResolvedBy()
        );
    }

    private com.grun.calorietracker.dto.FoodProductReviewAuditDto toReviewAuditDto(FoodProductReviewAuditEntity value) {
        com.grun.calorietracker.dto.FoodProductReviewAuditDto dto = new com.grun.calorietracker.dto.FoodProductReviewAuditDto();
        dto.setId(value.getId());
        dto.setFoodItemId(value.getFoodItem().getId());
        dto.setReviewedBy(value.getReviewedBy());
        dto.setActionType(value.getActionType());
        dto.setFieldName(value.getFieldName());
        dto.setOldValue(value.getOldValue());
        dto.setNewValue(value.getNewValue());
        dto.setNote(value.getNote());
        dto.setCreatedAt(value.getCreatedAt());
        return dto;
    }

    private ProductQualityAiSettingsEntity loadAiSettings() {
        return productQualityAiSettingsRepository.findById(ProductQualityAiSettingsEntity.SINGLETON_ID)
                .orElseGet(ProductQualityAiSettingsEntity::new);
    }

    private Map<String, Object> toAiSettingsAuditMap(ProductQualityAiSettingsEntity settings) {
        return Map.of(
                "enabled", settings.isEnabled(),
                "maxProductsPerRun", settings.getMaxProductsPerRun(),
                "dailyProductLimit", settings.getDailyProductLimit(),
                "monthlyProductLimit", settings.getMonthlyProductLimit(),
                "forceRescanAllowed", settings.isForceRescanAllowed(),
                "adminNote", settings.getAdminNote() == null ? "" : settings.getAdminNote()
        );
    }
    private ProductQualityAiSettingsDto toAiSettingsDto(ProductQualityAiSettingsEntity settings) {
        int usedToday = aiScannedSince(LocalDate.now().atStartOfDay());
        int usedThisMonth = aiScannedSince(LocalDate.now().withDayOfMonth(1).atStartOfDay());
        return new ProductQualityAiSettingsDto(
                settings.isEnabled(),
                settings.getMaxProductsPerRun(),
                settings.getDailyProductLimit(),
                settings.getMonthlyProductLimit(),
                settings.isForceRescanAllowed(),
                usedToday,
                usedThisMonth,
                Math.max(0, settings.getDailyProductLimit() - usedToday),
                Math.max(0, settings.getMonthlyProductLimit() - usedThisMonth),
                settings.getAdminNote(),
                settings.getUpdatedAt(),
                settings.getUpdatedBy()
        );
    }

    private int dailyRemaining(ProductQualityAiSettingsEntity settings) {
        return Math.max(0, settings.getDailyProductLimit() - aiScannedSince(LocalDate.now().atStartOfDay()));
    }

    private int monthlyRemaining(ProductQualityAiSettingsEntity settings) {
        return Math.max(0, settings.getMonthlyProductLimit() - aiScannedSince(LocalDate.now().withDayOfMonth(1).atStartOfDay()));
    }

    private int aiScannedSince(LocalDateTime from) {
        long used = productQualityScanRunRepository.sumScannedProductsSince(
                ProductQualitySuggestionSource.AI_ASSISTED,
                ProductQualityScanStatus.COMPLETED,
                from
        );
        return used > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) used;
    }
    private AiMealDraftProviderClient activeProvider() {
        return aiMealDraftProviderClients.stream()
                .filter(client -> client.provider() == properties.getProvider())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Configured AI provider client is not available."));
    }
    private int resolveScanLimit(int limit, ProductQualityScanTriggerType triggerType) {
        int max = triggerType == ProductQualityScanTriggerType.SCHEDULED ? MAX_SCHEDULED_SCAN_LIMIT : MAX_MANUAL_SCAN_LIMIT;
        return Math.max(1, Math.min(limit, max));
    }

    private void markProductQualityValidated(FoodItemEntity product, String actor) {
        product.setQualityValidatedAt(LocalDateTime.now());
        product.setQualityValidatedBy(actor);
        product.setQualityValidationSource(ProductQualitySuggestionSource.RULE_BASED);
        product.setQualityValidationNotes(QUALITY_VALIDATION_NOTE);
    }


    private List<Long> resolveSelectedProductIds(AdminProductQualityAiValidationRequestDto request, int maxProductsPerRun) {
        Set<Long> ids = new LinkedHashSet<>();
        if (request != null && request.getProductIds() != null) {
            request.getProductIds().stream().filter(id -> id != null && id > 0).forEach(ids::add);
        }
        if (request != null && request.getSuggestionIds() != null) {
            for (Long suggestionId : request.getSuggestionIds()) {
                if (suggestionId == null || suggestionId <= 0) {
                    continue;
                }
                productQualitySuggestionRepository.findForUpdateById(suggestionId)
                        .map(ProductQualitySuggestionEntity::getFoodItem)
                        .map(FoodItemEntity::getId)
                        .ifPresent(ids::add);
            }
        }
        int max = Math.max(1, Math.min(maxProductsPerRun, MAX_AI_SELECTED_VALIDATION_LIMIT));
        return ids.stream().limit(max).toList();
    }

    private AiProductQualityValidationRequestDto toAiValidationRequest(FoodItemEntity product) {
        AiProductQualityValidationRequestDto request = new AiProductQualityValidationRequestDto();
        request.setSchemaVersion(AI_PRODUCT_CONTEXT_SCHEMA_VERSION);
        request.setPromptVersion(AI_PRODUCT_PROMPT_VERSION);
        request.setProductId(product.getId());
        request.setName(product.getName());
        request.setDisplayName(product.getDisplayName());
        request.setShortDisplayName(product.getShortDisplayName());
        request.setBrand(product.getBrand());
        request.setBarcode(product.getBarcode());
        request.setSourceKey(product.getSourceKey());
        request.setCanonicalFoodKey(product.getCanonicalFoodKey());
        request.setMarketRegion(product.getMarketRegion());
        request.setDataSource(product.getDataSource());
        request.setCatalogType(product.getCatalogType());
        request.setVerificationStatus(product.getVerificationStatus());
        request.setImageStatus(product.getImageStatus());
        request.setPreparationState(product.getPreparationState());
        request.setAllergens(product.getAllergens());
        request.setNutriScore(product.getNutriScore());
        request.setCalories(product.getCalories());
        request.setProtein(product.getProtein());
        request.setFat(product.getFat());
        request.setCarbs(product.getCarbs());
        request.setFiber(product.getFiber());
        request.setSugar(product.getSugar());
        request.setSodium(product.getSodium());
        request.setPotassium(product.getPotassium());
        request.setCholesterol(product.getCholesterol());
        request.setCalcium(product.getCalcium());
        request.setIron(product.getIron());
        request.setMagnesium(product.getMagnesium());
        request.setZinc(product.getZinc());
        request.setVitaminA(product.getVitaminA());
        request.setVitaminC(product.getVitaminC());
        request.setVitaminD(product.getVitaminD());
        request.setVitaminE(product.getVitaminE());
        request.setVitaminB12(product.getVitaminB12());
        request.setSaturatedFat(product.getSaturatedFat());
        request.setTransFat(product.getTransFat());
        request.setSugarAlcohol(product.getSugarAlcohol());
        request.setServingSizeGrams(product.getServingSizeGrams());
        request.setServingUnit(product.getServingUnit());
        request.setQualityScore(product.getQualityScore());
        request.setConfidenceScore(product.getConfidenceScore());
        request.setUsageCount(product.getUsageCount());
        request.setLastReviewedAt(product.getLastReviewedAt());
        request.setQualityValidatedAt(product.getQualityValidatedAt());
        request.setLocalizations(loadLocalizationContext(product.getId()));
        request.setSearchAliases(loadAliasContext(product.getId()));
        request.setServingOptions(loadServingContext(product.getId()));
        request.setActiveQualityIssues(loadQualityIssueContext(product.getId()));
        request.setCanonicalDuplicate(loadCanonicalContext(product));
        com.grun.calorietracker.dto.FoodProductEvidenceContextDto evidenceContext = foodProductEvidenceService.buildContext(product);
        request.setSourceEvidence(evidenceContext == null ? List.of() : evidenceContext.getEvidence());
        request.setEvidenceComparisons(evidenceContext == null ? List.of() : evidenceContext.getComparisons());
        return request;
    }

    private List<AiProductQualityValidationRequestDto.LocalizationContext> loadLocalizationContext(Long productId) {
        return foodItemLocalizationRepository.findByFoodItemIdInAndLanguageIn(
                        List.of(productId), List.of(PreferredLanguage.EN, PreferredLanguage.TR))
                .stream()
                .map(value -> new AiProductQualityValidationRequestDto.LocalizationContext(
                        value.getId(), enumName(value.getLanguage()), value.getDisplayName(),
                        value.getShortDisplayName(), value.getSource(), value.getActive()))
                .toList();
    }

    private List<AiProductQualityValidationRequestDto.SearchAliasContext> loadAliasContext(Long productId) {
        return foodItemSearchAliasRepository.findByFoodItemIdOrderByActiveDescLanguageAscAliasAsc(productId)
                .stream()
                .map(value -> new AiProductQualityValidationRequestDto.SearchAliasContext(
                        value.getId(), value.getAlias(), value.getNormalizedAlias(), enumName(value.getLanguage()),
                        enumName(value.getAliasType()), value.getSource(), value.getActive()))
                .toList();
    }

    private List<AiProductQualityValidationRequestDto.ServingOptionContext> loadServingContext(Long productId) {
        List<FoodItemServingOptionEntity> options = foodItemServingOptionRepository
                .findByFoodItemIdInOrderByFoodItemIdAscIsDefaultDescLabelAsc(List.of(productId));
        Map<Long, List<FoodItemServingOptionLocalizationEntity>> localizations = options.isEmpty()
                ? Map.of()
                : foodItemServingOptionLocalizationRepository.findByServingOptionIdIn(
                                options.stream().map(FoodItemServingOptionEntity::getId).toList())
                        .stream()
                        .collect(java.util.stream.Collectors.groupingBy(value -> value.getServingOption().getId()));
        return options.stream()
                .map(value -> new AiProductQualityValidationRequestDto.ServingOptionContext(
                        value.getId(), value.getLabel(), enumName(value.getUnitType()), value.getQuantity(),
                        value.getGramWeight(), value.getMlVolume(), value.getIsDefault(), enumName(value.getSource()),
                        enumName(value.getQualityStatus()), localizations.getOrDefault(value.getId(), List.of()).stream()
                        .map(localization -> new AiProductQualityValidationRequestDto.ServingLocalizationContext(
                                localization.getId(), enumName(localization.getLanguage()), localization.getLabel(),
                                localization.getSource(), localization.getActive()))
                        .toList()))
                .toList();
    }

    private List<AiProductQualityValidationRequestDto.QualityIssueContext> loadQualityIssueContext(Long productId) {
        return foodProductQualityIssueRepository.findByFoodItemIdAndResolvedFalse(productId).stream()
                .map(value -> new AiProductQualityValidationRequestDto.QualityIssueContext(
                        value.getId(), enumName(value.getIssueType()), value.getIdentifier(), value.getReason(),
                        value.getFirstDetectedAt(), value.getLastDetectedAt()))
                .toList();
    }

    private AiProductQualityValidationRequestDto.CanonicalDuplicateContext loadCanonicalContext(FoodItemEntity product) {
        String canonicalKey = trimToNull(product.getCanonicalFoodKey());
        if (canonicalKey == null) {
            return null;
        }
        List<FoodItemEntity> candidates = foodItemRepository.findByCanonicalFoodKeyIn(
                List.of(canonicalKey), Sort.by(Sort.Order.desc("qualityScore"), Sort.Order.desc("usageCount"), Sort.Order.asc("id")));
        Long resolvedPrimaryId = foodCanonicalResolutionRepository.findById(canonicalKey)
                .map(value -> value.getPrimaryFoodItem().getId())
                .orElse(null);
        return new AiProductQualityValidationRequestDto.CanonicalDuplicateContext(
                canonicalKey,
                resolvedPrimaryId,
                candidates.stream().map(value -> new AiProductQualityValidationRequestDto.CanonicalCandidateContext(
                        value.getId(), value.getDisplayName(), value.getBrand(), enumName(value.getDataSource()),
                        enumName(value.getMarketRegion()), enumName(value.getPreparationState()),
                        enumName(value.getVerificationStatus()), value.getQualityScore(), value.getConfidenceScore(),
                        value.getUsageCount())).toList());
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private void validateAiResponse(AiProductQualityValidationResponseDto response) {
        if (response == null) {
            throw new IllegalArgumentException("AI product quality provider returned no response.");
        }
        if (!AI_PRODUCT_RESPONSE_SCHEMA_VERSION.equals(response.getSchemaVersion())) {
            throw new IllegalArgumentException("Unsupported AI product quality response schema version.");
        }
    }

    private boolean isAllowedAiIssue(AiProductQualityValidationResponseDto.AiProductQualityIssueDto issue) {
        String field = trimToNull(issue.getFieldName());
        if (field == null) {
            return false;
        }
        return switch (issue.getSuggestionType()) {
            case NAME_CLEANUP -> field.equals("name");
            case DISPLAY_NAME -> field.equals("displayName") || field.equals("shortDisplayName");
            case LOCALIZATION -> field.matches("localizations\\.(EN|TR)\\.(displayName|shortDisplayName)");
            case SEARCH_ALIAS -> field.matches("searchAliases\\.(EN|TR)");
            case SERVING_OPTION -> field.matches("servingOptions(\\.[A-Za-z0-9_-]+)+");
            case CANONICAL_DUPLICATE_REVIEW -> field.equals("canonicalFoodKey");
            case MISSING_MACRO_DATA, MISSING_MICRO_DATA, SUSPICIOUS_CALORIE_VALUE,
                    MACRO_CALORIE_MISMATCH, SUSPICIOUS_SODIUM_VALUE -> Set.of(
                    "macros", "micronutrients", "calories", "protein", "fat", "carbs", "fiber", "sugar", "sodium", "potassium",
                    "cholesterol", "calcium", "iron", "magnesium", "zinc", "vitaminA", "vitaminC",
                    "vitaminD", "vitaminE", "vitaminB12", "saturatedFat", "transFat", "sugarAlcohol")
                    .contains(field);
            case MISSING_SERVING_SIZE -> Set.of("servingSizeGrams", "servingUnit").contains(field);
            case SOURCE_CONFLICT -> Set.of("sourceKey", "dataSource").contains(field);
            case IMAGE_REVIEW_REQUIRED -> Set.of("imageStatus", "imageUrl").contains(field);
            case REGION_MISMATCH -> field.equals("marketRegion");
            case LABEL_REVIEW_REQUIRED -> Set.of("allergens", "nutriScore", "name", "brand").contains(field);
        };
    }
    private ProductQualitySuggestionEntity buildAiSuggestion(
            FoodItemEntity product,
            AiProductQualityValidationResponseDto.AiProductQualityIssueDto issue
    ) {
        return buildAiSuggestion(product, issue, List.of());
    }

    private ProductQualitySuggestionEntity buildAiSuggestion(
            FoodItemEntity product,
            AiProductQualityValidationResponseDto.AiProductQualityIssueDto issue,
            List<com.grun.calorietracker.dto.FoodProductEvidenceComparisonDto> evidenceComparisons
    ) {
        if (issue == null || issue.getSuggestionType() == null) {
            return null;
        }
        if (!isAllowedAiIssue(issue)) {
            throw new IllegalArgumentException("Unsupported AI product quality suggestion field: " + issue.getFieldName());
        }
        ProductQualitySuggestionEntity suggestion = new ProductQualitySuggestionEntity();
        suggestion.setFoodItem(product);
        suggestion.setSuggestionType(issue.getSuggestionType());
        suggestion.setSource(ProductQualitySuggestionSource.AI_ASSISTED);
        suggestion.setStatus(ProductQualitySuggestionStatus.OPEN);
        suggestion.setFieldName(trimToNull(issue.getFieldName()));
        suggestion.setCurrentValue(trimToMax(issue.getCurrentValue(), 1000));
        boolean exactValueAllowed = exactNutritionSuggestionAllowed(issue, evidenceComparisons);
        suggestion.setSuggestedValue(exactValueAllowed ? trimToMax(issue.getSuggestedValue(), 1000) : null);
        String evidenceReason = exactValueAllowed
                ? issue.getReason()
                : appendEvidenceRestriction(issue.getReason());
        suggestion.setReason(trimToMax(evidenceReason, 1000));
        suggestion.setConfidenceScore(issue.getConfidenceScore() == null ? 50 : Math.max(0, Math.min(issue.getConfidenceScore(), 100)));
        return suggestion;
    }

    private boolean exactNutritionSuggestionAllowed(
            AiProductQualityValidationResponseDto.AiProductQualityIssueDto issue,
            List<com.grun.calorietracker.dto.FoodProductEvidenceComparisonDto> comparisons
    ) {
        if (trimToNull(issue.getSuggestedValue()) == null || !isNutritionSuggestion(issue.getSuggestionType())) {
            return true;
        }
        com.grun.calorietracker.enums.FoodEvidenceField evidenceField = evidenceField(issue.getFieldName());
        if (evidenceField == null || comparisons == null) {
            return false;
        }
        return comparisons.stream().anyMatch(comparison ->
                comparison.getFieldName() == evidenceField
                        && comparison.getState() == com.grun.calorietracker.enums.FoodEvidenceComparisonState.MATCH);
    }

    private boolean isNutritionSuggestion(ProductQualitySuggestionType type) {
        return type == ProductQualitySuggestionType.MISSING_MACRO_DATA
                || type == ProductQualitySuggestionType.MISSING_MICRO_DATA
                || type == ProductQualitySuggestionType.SUSPICIOUS_CALORIE_VALUE
                || type == ProductQualitySuggestionType.MACRO_CALORIE_MISMATCH
                || type == ProductQualitySuggestionType.SUSPICIOUS_SODIUM_VALUE;
    }

    private com.grun.calorietracker.enums.FoodEvidenceField evidenceField(String fieldName) {
        String field = trimToNull(fieldName);
        if (field == null || field.equals("macros") || field.equals("micronutrients")) {
            return null;
        }
        String enumName = field.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
        try {
            return com.grun.calorietracker.enums.FoodEvidenceField.valueOf(enumName);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String appendEvidenceRestriction(String reason) {
        String prefix = trimToNull(reason);
        String restriction = "Exact suggested value removed because current source evidence is missing, stale, single-source, or conflicting.";
        return prefix == null ? restriction : prefix + " " + restriction;
    }
    private String trimToMax(String value, int maxLength) {
        String normalized = trimToNull(value);
        if (normalized == null || normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }

    private ProductQualitySuggestionEntity findOpenSuggestion(Long suggestionId) {
        ProductQualitySuggestionEntity suggestion = productQualitySuggestionRepository.findForUpdateById(suggestionId)
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
        product.setLastReviewedAt(LocalDateTime.now());
        product.setQualityValidatedAt(null);
        product.setQualityValidatedBy(null);
        product.setQualityValidationSource(null);
        product.setQualityValidationNotes("Quality validation reset after accepted name cleanup suggestion.");
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

    private void applyDisplayNameSuggestion(ProductQualitySuggestionEntity suggestion, String reviewedBy) {
        FoodItemEntity product = suggestion.getFoodItem();
        String suggestedValue = FoodProductNormalizationRules.normalizeProductDisplayName(suggestion.getSuggestedValue());
        if (suggestedValue == null) {
            throw new IllegalArgumentException("Display name suggestion has no suggested value.");
        }
        String field = trimToNull(suggestion.getFieldName());
        String oldValue;
        if ("shortDisplayName".equals(field)) {
            oldValue = product.getShortDisplayName();
            product.setShortDisplayName(suggestedValue);
        } else if ("displayName".equals(field)) {
            oldValue = product.getDisplayName();
            product.setDisplayName(suggestedValue);
        } else {
            throw new IllegalArgumentException("Display name suggestion has an unsupported field.");
        }
        product.setLastReviewedAt(LocalDateTime.now());
        product.setReviewedBy(normalizeActor(reviewedBy));
        foodItemRepository.save(product);
        saveAudit(product, reviewedBy, FoodProductReviewAuditAction.REVIEW_UPDATE, field, oldValue, suggestedValue,
                "product quality suggestion accepted: " + suggestion.getId());
    }

    private void applyLocalizationSuggestion(ProductQualitySuggestionEntity suggestion, String reviewedBy) {
        String field = trimToNull(suggestion.getFieldName());
        String suggestedValue = FoodProductNormalizationRules.normalizeProductDisplayName(suggestion.getSuggestedValue());
        if (field == null || suggestedValue == null || !field.matches("localizations\\.(EN|TR)\\.(displayName|shortDisplayName)")) {
            throw new IllegalArgumentException("Localization suggestion is incomplete or unsupported.");
        }
        String[] parts = field.split("\\.");
        PreferredLanguage language = PreferredLanguage.valueOf(parts[1]);
        FoodItemEntity product = suggestion.getFoodItem();
        FoodItemLocalizationEntity localization = foodItemLocalizationRepository
                .findByFoodItemIdAndLanguage(product.getId(), language)
                .orElseGet(FoodItemLocalizationEntity::new);
        String oldValue = "displayName".equals(parts[2]) ? localization.getDisplayName() : localization.getShortDisplayName();
        localization.setFoodItem(product);
        localization.setLanguage(language);
        if ("displayName".equals(parts[2])) {
            localization.setDisplayName(suggestedValue);
        } else {
            localization.setShortDisplayName(suggestedValue);
            if (trimToNull(localization.getDisplayName()) == null) {
                localization.setDisplayName(suggestedValue);
            }
        }
        localization.setSource(QUALITY_SUGGESTION_SOURCE);
        localization.setActive(true);
        localization.setUpdatedAt(LocalDateTime.now());
        foodItemLocalizationRepository.save(localization);
        product.setLastReviewedAt(LocalDateTime.now());
        product.setReviewedBy(normalizeActor(reviewedBy));
        foodItemRepository.save(product);
        saveAudit(product, reviewedBy, FoodProductReviewAuditAction.REVIEW_UPDATE, field, oldValue, suggestedValue,
                "product quality suggestion accepted: " + suggestion.getId());
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

        PreferredLanguage language = resolveAliasSuggestionLanguage(suggestion);
        FoodItemSearchAliasEntity alias = foodItemSearchAliasRepository
                .findByFoodItemIdAndNormalizedAliasAndLanguage(product.getId(), normalizedAlias, language)
                .orElseGet(FoodItemSearchAliasEntity::new);
        boolean isNewAlias = alias.getId() == null;
        String oldValue = isNewAlias ? null : alias.getAlias() + "|" + alias.getActive();

        alias.setFoodItem(product);
        alias.setAlias(aliasText);
        alias.setNormalizedAlias(normalizedAlias);
        alias.setLanguage(language);
        alias.setAliasType(FoodSearchAliasType.TRANSLATION);
        alias.setSource(QUALITY_SUGGESTION_SOURCE);
        alias.setActive(true);
        FoodItemSearchAliasEntity savedAlias = foodItemSearchAliasRepository.save(alias);

        product.setLastReviewedAt(LocalDateTime.now());
        foodItemRepository.save(product);

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
    private void validateSuggestionApplication(ProductQualitySuggestionEntity suggestion) {
        ProductQualitySuggestionType type = suggestion.getSuggestionType();
        boolean directlySupported = type == ProductQualitySuggestionType.NAME_CLEANUP
                || type == ProductQualitySuggestionType.DISPLAY_NAME
                || type == ProductQualitySuggestionType.LOCALIZATION
                || type == ProductQualitySuggestionType.SEARCH_ALIAS
                || isSupportedFieldSuggestion(suggestion);
        if (!directlySupported) {
            throw new IllegalArgumentException(
                    "Product quality suggestion is review-only and cannot be applied automatically."
            );
        }

        String suggestedValue = trimToNull(suggestion.getSuggestedValue());
        if (suggestedValue == null) {
            throw new IllegalArgumentException("Product quality suggestion has no suggested value.");
        }
        if (isNutritionSuggestion(type)
                && suggestion.getSource() == ProductQualitySuggestionSource.AI_ASSISTED
                && (suggestion.getConfidenceScore() == null || suggestion.getConfidenceScore() < 80)) {
            throw new IllegalArgumentException(
                    "AI nutrition suggestion confidence must be at least 80 before it can be accepted."
            );
        }

        validateSuggestionHasNotDrifted(suggestion);
        switch (type) {
            case NAME_CLEANUP -> validateTextLength(suggestedValue, "name", 255);
            case DISPLAY_NAME -> {
                if (!Set.of("displayName", "shortDisplayName").contains(trimToNull(suggestion.getFieldName()))) {
                    throw new IllegalArgumentException("Display name suggestion has an unsupported field.");
                }
                validateTextLength(suggestedValue, suggestion.getFieldName(), 255);
            }
            case LOCALIZATION -> {
                String field = trimToNull(suggestion.getFieldName());
                if (field == null || !field.matches("localizations\\.(EN|TR)\\.(displayName|shortDisplayName)")) {
                    throw new IllegalArgumentException("Localization suggestion is incomplete or unsupported.");
                }
                validateTextLength(suggestedValue, field, 255);
            }
            case SEARCH_ALIAS -> {
                String field = trimToNull(suggestion.getFieldName());
                if (field != null && !field.matches("searchAliases\\.(EN|TR)")) {
                    throw new IllegalArgumentException("Search alias suggestion has an unsupported language field.");
                }
                validateTextLength(suggestedValue, "searchAlias", 255);
            }
            default -> validateSupportedFieldValue(suggestion.getFoodItem(), suggestion.getFieldName(), suggestedValue);
        }
    }

    private void validateSuggestionHasNotDrifted(ProductQualitySuggestionEntity suggestion) {
        String expectedCurrentValue = trimToNull(suggestion.getCurrentValue());
        if (expectedCurrentValue == null) {
            return;
        }
        FoodItemEntity product = suggestion.getFoodItem();
        String actualCurrentValue = switch (suggestion.getSuggestionType()) {
            case NAME_CLEANUP -> product.getName();
            case DISPLAY_NAME -> "shortDisplayName".equals(suggestion.getFieldName())
                    ? product.getShortDisplayName()
                    : product.getDisplayName();
            case LOCALIZATION -> readLocalizationValue(product.getId(), suggestion.getFieldName());
            default -> isSupportedFieldSuggestion(suggestion)
                    ? readFieldValue(product, normalizeFieldName(suggestion.getFieldName()))
                    : expectedCurrentValue;
        };
        if (!equivalentSuggestionValues(expectedCurrentValue, actualCurrentValue)) {
            throw new IllegalArgumentException(
                    "Product changed after this suggestion was created. Run validation again before accepting it."
            );
        }
    }

    private String readLocalizationValue(Long productId, String fieldName) {
        String field = trimToNull(fieldName);
        if (productId == null || field == null
                || !field.matches("localizations\\.(EN|TR)\\.(displayName|shortDisplayName)")) {
            return null;
        }
        String[] parts = field.split("\\.");
        PreferredLanguage language = PreferredLanguage.valueOf(parts[1]);
        return foodItemLocalizationRepository.findByFoodItemIdAndLanguage(productId, language)
                .map(localization -> "displayName".equals(parts[2])
                        ? localization.getDisplayName()
                        : localization.getShortDisplayName())
                .orElse(null);
    }

    private boolean equivalentSuggestionValues(String expected, String actual) {
        String normalizedExpected = trimToNull(expected);
        String normalizedActual = trimToNull(actual);
        if (Objects.equals(normalizedExpected, normalizedActual)) {
            return true;
        }
        if (normalizedExpected == null || normalizedActual == null) {
            return false;
        }
        try {
            return new BigDecimal(normalizedExpected).compareTo(new BigDecimal(normalizedActual)) == 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private void validateSupportedFieldValue(FoodItemEntity product, String rawFieldName, String suggestedValue) {
        String fieldName = normalizeFieldName(rawFieldName);
        switch (fieldName) {
            case "calories" -> parseBoundedDouble(suggestedValue, "calories", 0.0, 900.0);
            case "protein", "fiber" -> parseBoundedDouble(suggestedValue, fieldName, 0.0, 100.0);
            case "carbs" -> {
                double value = parseBoundedDouble(suggestedValue, "carbs", 0.0, 100.0);
                requireNotBelow(value, product.getSugar(), "carbs", "sugar");
            }
            case "fat" -> {
                double value = parseBoundedDouble(suggestedValue, "fat", 0.0, 100.0);
                requireNotBelow(value, product.getSaturatedFat(), "fat", "saturatedFat");
                requireNotBelow(value, product.getTransFat(), "fat", "transFat");
            }
            case "sugar" -> {
                double value = parseBoundedDouble(suggestedValue, "sugar", 0.0, 100.0);
                requireNotAbove(value, product.getCarbs(), "sugar", "carbs");
            }
            case "saturatedfat" -> {
                double value = parseBoundedDouble(suggestedValue, "saturatedFat", 0.0, 100.0);
                requireNotAbove(value, product.getFat(), "saturatedFat", "fat");
            }
            case "transfat" -> {
                double value = parseBoundedDouble(suggestedValue, "transFat", 0.0, 100.0);
                requireNotAbove(value, product.getFat(), "transFat", "fat");
            }
            case "sugaralcohol" -> parseBoundedDouble(suggestedValue, "sugarAlcohol", 0.0, 100.0);
            case "sodium" -> parseBoundedDouble(suggestedValue, "sodium", 0.0, 10.0);
            case "potassium", "cholesterol", "calcium", "iron", "magnesium", "zinc",
                    "vitamina", "vitaminc", "vitamind", "vitamine", "vitaminb12" ->
                    parseBoundedDouble(suggestedValue, fieldName, 0.0, 100.0);
            case "servingsizegrams" -> parseBoundedDouble(suggestedValue, "servingSizeGrams", 0.001, 100000.0);
            case "servingunit" -> validateTextLength(suggestedValue, "servingUnit", 50);
            default -> throw new IllegalArgumentException(
                    "Product quality suggestion field is review-only: " + rawFieldName
            );
        }
    }

    private double parseBoundedDouble(
            String value,
            String fieldName,
            double minimum,
            double maximum
    ) {
        double parsed = parseNonNegativeDouble(value, fieldName);
        if (parsed < minimum || parsed > maximum) {
            throw new IllegalArgumentException(
                    fieldName + " must be between " + minimum + " and " + maximum + "."
            );
        }
        return parsed;
    }

    private void requireNotAbove(double value, Double limit, String fieldName, String limitField) {
        if (limit != null && value > limit) {
            throw new IllegalArgumentException(fieldName + " must not exceed " + limitField + ".");
        }
    }

    private void requireNotBelow(double value, Double minimum, String fieldName, String minimumField) {
        if (minimum != null && value < minimum) {
            throw new IllegalArgumentException(fieldName + " must not be below " + minimumField + ".");
        }
    }

    private void validateTextLength(String value, String fieldName, int maximumLength) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " must not be blank.");
        }
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(fieldName + " must not exceed " + maximumLength + " characters.");
        }
    }
    private PreferredLanguage resolveAliasSuggestionLanguage(ProductQualitySuggestionEntity suggestion) {
        String field = trimToNull(suggestion.getFieldName());
        if (field != null && field.matches("searchAliases\\.(EN|TR)")) {
            return PreferredLanguage.valueOf(field.substring(field.lastIndexOf('.') + 1));
        }
        return PreferredLanguage.TR;
    }
    private boolean isSupportedFieldSuggestion(ProductQualitySuggestionEntity suggestion) {
        String fieldName = normalizeFieldName(suggestion.getFieldName());
        if (fieldName.isEmpty() || trimToNull(suggestion.getSuggestedValue()) == null) {
            return false;
        }
        if (isNutritionSuggestion(suggestion.getSuggestionType())) {
            return SAFE_NUTRITION_FIELDS.contains(fieldName);
        }
        return suggestion.getSuggestionType() == ProductQualitySuggestionType.MISSING_SERVING_SIZE
                && SAFE_SERVING_FIELDS.contains(fieldName);
    }

    private void applyFieldSuggestion(ProductQualitySuggestionEntity suggestion, String reviewedBy) {
        FoodItemEntity product = suggestion.getFoodItem();
        String fieldName = normalizeFieldName(suggestion.getFieldName());
        String suggestedValue = trimToNull(suggestion.getSuggestedValue());
        if (suggestedValue == null) {
            throw new IllegalArgumentException("Product quality suggestion has no suggested value.");
        }

        String oldValue = readFieldValue(product, fieldName);
        writeFieldValue(product, fieldName, suggestedValue);
        String newValue = readFieldValue(product, fieldName);
        if (String.valueOf(oldValue).equals(String.valueOf(newValue))) {
            return;
        }

        product.setLastReviewedAt(LocalDateTime.now());
        product.setReviewedBy(normalizeActor(reviewedBy));
        product.setQualityValidatedAt(null);
        product.setQualityValidatedBy(null);
        product.setQualityValidationSource(null);
        product.setQualityValidationNotes("Quality validation reset after accepted field-level suggestion.");
        foodItemRepository.save(product);

        saveAudit(
                product,
                reviewedBy,
                auditActionForField(fieldName),
                suggestion.getFieldName(),
                oldValue,
                newValue,
                "product quality suggestion accepted: " + suggestion.getId()
        );
    }

    private String readFieldValue(FoodItemEntity product, String fieldName) {
        Object value = switch (fieldName) {
            case "calories" -> product.getCalories();
            case "protein" -> product.getProtein();
            case "fat" -> product.getFat();
            case "carbs" -> product.getCarbs();
            case "fiber" -> product.getFiber();
            case "sugar" -> product.getSugar();
            case "sodium" -> product.getSodium();
            case "potassium" -> product.getPotassium();
            case "cholesterol" -> product.getCholesterol();
            case "calcium" -> product.getCalcium();
            case "iron" -> product.getIron();
            case "magnesium" -> product.getMagnesium();
            case "zinc" -> product.getZinc();
            case "vitamina" -> product.getVitaminA();
            case "vitaminc" -> product.getVitaminC();
            case "vitamind" -> product.getVitaminD();
            case "vitamine" -> product.getVitaminE();
            case "vitaminb12" -> product.getVitaminB12();
            case "saturatedfat" -> product.getSaturatedFat();
            case "transfat" -> product.getTransFat();
            case "sugaralcohol" -> product.getSugarAlcohol();
            case "servingsizegrams" -> product.getServingSizeGrams();
            case "servingunit" -> product.getServingUnit();
            default -> null;
        };
        return value == null ? null : String.valueOf(value);
    }

    private void writeFieldValue(FoodItemEntity product, String fieldName, String suggestedValue) {
        switch (fieldName) {
            case "calories" -> product.setCalories(parseNonNegativeDouble(suggestedValue, "calories"));
            case "protein" -> product.setProtein(parseNonNegativeDouble(suggestedValue, "protein"));
            case "fat" -> product.setFat(parseNonNegativeDouble(suggestedValue, "fat"));
            case "carbs" -> product.setCarbs(parseNonNegativeDouble(suggestedValue, "carbs"));
            case "fiber" -> product.setFiber(parseNonNegativeDouble(suggestedValue, "fiber"));
            case "sugar" -> product.setSugar(parseNonNegativeDouble(suggestedValue, "sugar"));
            case "sodium" -> product.setSodium(parseNonNegativeDouble(suggestedValue, "sodium"));
            case "potassium" -> product.setPotassium(parseNonNegativeDouble(suggestedValue, "potassium"));
            case "cholesterol" -> product.setCholesterol(parseNonNegativeDouble(suggestedValue, "cholesterol"));
            case "calcium" -> product.setCalcium(parseNonNegativeDouble(suggestedValue, "calcium"));
            case "iron" -> product.setIron(parseNonNegativeDouble(suggestedValue, "iron"));
            case "magnesium" -> product.setMagnesium(parseNonNegativeDouble(suggestedValue, "magnesium"));
            case "zinc" -> product.setZinc(parseNonNegativeDouble(suggestedValue, "zinc"));
            case "vitamina" -> product.setVitaminA(parseNonNegativeDouble(suggestedValue, "vitaminA"));
            case "vitaminc" -> product.setVitaminC(parseNonNegativeDouble(suggestedValue, "vitaminC"));
            case "vitamind" -> product.setVitaminD(parseNonNegativeDouble(suggestedValue, "vitaminD"));
            case "vitamine" -> product.setVitaminE(parseNonNegativeDouble(suggestedValue, "vitaminE"));
            case "vitaminb12" -> product.setVitaminB12(parseNonNegativeDouble(suggestedValue, "vitaminB12"));
            case "saturatedfat" -> product.setSaturatedFat(parseNonNegativeDouble(suggestedValue, "saturatedFat"));
            case "transfat" -> product.setTransFat(parseNonNegativeDouble(suggestedValue, "transFat"));
            case "sugaralcohol" -> product.setSugarAlcohol(parseNonNegativeDouble(suggestedValue, "sugarAlcohol"));
            case "servingsizegrams" -> product.setServingSizeGrams(parseNonNegativeDouble(suggestedValue, "servingSizeGrams"));
            case "servingunit" -> product.setServingUnit(suggestedValue);
            default -> throw new IllegalArgumentException("Unsupported product quality suggestion field: " + fieldName);
        }
    }

    private double parseNonNegativeDouble(String value, String fieldName) {
        try {
            double parsed = Double.parseDouble(value.trim());
            if (!Double.isFinite(parsed) || parsed < 0) {
                throw new IllegalArgumentException(fieldName + " must be a finite, non-negative value.");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + " must be a numeric value.");
        }
    }

    private <E extends Enum<E>> E parseEnum(Class<E> enumClass, String value, String fieldName) {
        try {
            return Enum.valueOf(enumClass, value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(fieldName + " has unsupported value: " + value);
        }
    }

    private FoodProductReviewAuditAction auditActionForField(String fieldName) {
        if (fieldName.contains("image")) {
            return FoodProductReviewAuditAction.IMAGE_CHANGE;
        }
        if ("verificationstatus".equals(fieldName)) {
            return FoodProductReviewAuditAction.STATUS_CHANGE;
        }
        return FoodProductReviewAuditAction.REVIEW_UPDATE;
    }

    private String normalizeFieldName(String fieldName) {
        String value = trimToNull(fieldName);
        return value == null ? "" : value.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
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

    private Specification<FoodItemEntity> buildCandidateSpecification(MarketRegion marketRegion, boolean forceRescan) {
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
            if (!forceRescan) {
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.isNull(root.get("qualityValidatedAt")),
                        criteriaBuilder.and(
                                criteriaBuilder.isNotNull(root.get("lastReviewedAt")),
                                criteriaBuilder.greaterThan(root.get("lastReviewedAt"), root.get("qualityValidatedAt"))
                        )
                ));
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
                "name",
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
            addAliasSuggestion(product, suggestions, "sut", "Turkish users should be able to find plain milk products with 'sut'.");
        }
        if (containsAny(name, "cheese")) {
            addAliasSuggestion(product, suggestions, "peynir", "Turkish users should be able to find cheese products with 'peynir'.");
        }
        if (containsAny(name, "yogurt", "yoghurt")) {
            addAliasSuggestion(product, suggestions, "yogurt", "Turkish users should be able to find yogurt products with 'yogurt'.");
        }
        if (containsAny(name, "bread") && !containsAny(name, "shortbread")) {
            addAliasSuggestion(product, suggestions, "ekmek", "Turkish users should be able to find bread products with 'ekmek'.");
        }
        if (name.contains("chicken breast")) {
            addAliasSuggestion(product, suggestions, "tavuk gogsu", "Turkish users should be able to find chicken breast with 'tavuk gogsu'.");
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
                "searchAlias",
                null,
                alias,
                reason,
                80
        ));
    }

    private boolean hasActiveAlias(FoodItemEntity product, String normalizedAlias) {
        if (product.getId() == null) {
            return false;
        }
        return foodItemSearchAliasRepository.existsByFoodItemIdAndNormalizedAliasAndActiveTrue(product.getId(), normalizedAlias);
    }

    private ProductQualitySuggestionEntity buildSuggestion(
            FoodItemEntity product,
            ProductQualitySuggestionType type,
            String fieldName,
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
        suggestion.setFieldName(fieldName);
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
                entity.getFieldName(),
                entity.getCurrentValue(),
                entity.getSuggestedValue(),
                entity.getReason(),
                entity.getCreatedAt(),
                entity.getReviewedAt(),
                entity.getReviewedBy()
        );
    }

    private ProductQualityScanRunDto toScanRunDto(ProductQualityScanRunEntity entity) {
        return new ProductQualityScanRunDto(
                entity.getId(),
                entity.getSource(),
                entity.getTriggerType(),
                entity.getStatus(),
                entity.getMarketRegion(),
                entity.getRequestedLimit(),
                entity.getEffectiveLimit(),
                entity.isForceRescan(),
                entity.getScannedProducts(),
                entity.getCreatedSuggestions(),
                entity.getSkippedExistingSuggestions(),
                entity.getSkippedPreviouslyValidatedProducts(),
                entity.getValidatedProducts(),
                entity.getTriggeredBy(),
                entity.getStartedAt(),
                entity.getCompletedAt(),
                entity.getErrorMessage()
        );
    }

    private List<ProductQualityScanRunItemDto> fallbackValidatedItemsForLegacyRun(ProductQualityScanRunEntity run) {
        if (run.getValidatedProducts() <= 0
                || run.getStartedAt() == null
                || run.getCompletedAt() == null) {
            return List.of();
        }
        int fallbackLimit = Math.max(1, Math.min(run.getValidatedProducts(), MAX_MANUAL_SCAN_LIMIT));
        LocalDateTime startedAt = run.getStartedAt().minusSeconds(1);
        LocalDateTime completedAt = run.getCompletedAt().plusSeconds(1);
        Pageable fallbackPage = PageRequest.of(0, fallbackLimit, Sort.by(Sort.Order.asc("id")));
        List<FoodItemEntity> fallbackProducts = foodItemRepository.findQualityValidatedDuringScan(
                startedAt,
                completedAt,
                run.getSource(),
                run.getMarketRegion(),
                fallbackPage
        );
        if (fallbackProducts.isEmpty()) {
            fallbackProducts = foodItemRepository.findQualityValidatedDuringScan(
                    startedAt,
                    completedAt,
                    null,
                    run.getMarketRegion(),
                    fallbackPage
            );
        }
        if (fallbackProducts.isEmpty()) {
            fallbackProducts = foodItemRepository.findQualityValidatedDuringScan(
                    startedAt,
                    completedAt,
                    null,
                    null,
                    fallbackPage
            );
        }
        return fallbackProducts.stream()
                .map(product -> new ProductQualityScanRunItemDto(
                        null,
                        product.getId(),
                        product.getName(),
                        product.getBrand(),
                        ProductQualityScanItemStatus.VALIDATED,
                        null,
                        null,
                        null,
                        null,
                        product.getQualityScore(),
                        product.getQualityValidationNotes()
                ))
                .toList();
    }
    private ProductQualityScanRunItemEntity toRunItem(
            ProductQualityScanRunEntity run,
            FoodItemEntity product,
            ProductQualitySuggestionEntity suggestion,
            ProductQualityScanItemStatus status,
            String note
    ) {
        ProductQualityScanRunItemEntity item = new ProductQualityScanRunItemEntity();
        item.setScanRun(run);
        item.setFoodItem(product);
        item.setStatus(status);
        item.setSuggestionType(suggestion.getSuggestionType());
        item.setFieldName(suggestion.getFieldName());
        item.setSuggestedValue(suggestion.getSuggestedValue());
        item.setReason(suggestion.getReason());
        item.setConfidenceScore(suggestion.getConfidenceScore());
        item.setProductNameSnapshot(product.getName());
        item.setBrandSnapshot(product.getBrand());
        item.setNote(note);
        return item;
    }

    private ProductQualityScanRunItemEntity toValidatedRunItem(
            ProductQualityScanRunEntity run,
            FoodItemEntity product,
            ProductQualityScanItemStatus status,
            String note
    ) {
        ProductQualityScanRunItemEntity item = new ProductQualityScanRunItemEntity();
        item.setScanRun(run);
        item.setFoodItem(product);
        item.setStatus(status);
        item.setProductNameSnapshot(product.getName());
        item.setBrandSnapshot(product.getBrand());
        item.setNote(note);
        return item;
    }

    private ProductQualityScanRunItemDto toRunItemDto(ProductQualityScanRunItemEntity entity) {
        FoodItemEntity product = entity.getFoodItem();
        return new ProductQualityScanRunItemDto(
                entity.getId(),
                product == null ? null : product.getId(),
                entity.getProductNameSnapshot(),
                entity.getBrandSnapshot(),
                entity.getStatus(),
                entity.getSuggestionType(),
                entity.getFieldName(),
                entity.getSuggestedValue(),
                entity.getReason(),
                entity.getConfidenceScore(),
                entity.getNote()
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
