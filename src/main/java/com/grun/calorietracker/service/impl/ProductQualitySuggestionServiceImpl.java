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
import com.grun.calorietracker.entity.FoodItemSearchAliasEntity;
import com.grun.calorietracker.entity.FoodProductReviewAuditEntity;
import com.grun.calorietracker.entity.ProductQualityScanRunEntity;
import com.grun.calorietracker.entity.ProductQualityAiSettingsEntity;
import com.grun.calorietracker.entity.ProductQualitySuggestionEntity;
import com.grun.calorietracker.enums.FoodProductReviewAuditAction;
import com.grun.calorietracker.enums.AdminAuditActionType;
import com.grun.calorietracker.enums.AdminAuditTargetType;
import com.grun.calorietracker.enums.ImageSource;
import com.grun.calorietracker.enums.ImageStatus;
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
import com.grun.calorietracker.repository.FoodItemSearchAliasRepository;
import com.grun.calorietracker.repository.FoodProductReviewAuditRepository;
import com.grun.calorietracker.repository.ProductQualityScanRunRepository;
import com.grun.calorietracker.repository.ProductQualityAiSettingsRepository;
import com.grun.calorietracker.repository.ProductQualitySuggestionRepository;
import com.grun.calorietracker.service.AiMealDraftProviderClient;
import com.grun.calorietracker.service.AdminAuditService;
import com.grun.calorietracker.service.ProductQualitySuggestionService;
import com.grun.calorietracker.service.support.FoodProductNormalizationRules;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProductQualitySuggestionServiceImpl implements ProductQualitySuggestionService {

    private static final int MAX_MANUAL_SCAN_LIMIT = 500;
    private static final int MAX_SCHEDULED_SCAN_LIMIT = 250;
    private static final int MAX_AI_SELECTED_VALIDATION_LIMIT = 25;
    private static final String QUALITY_SUGGESTION_SOURCE = "quality_suggestion";
    private static final String QUALITY_VALIDATION_NOTE = "No open rule-based quality suggestions were produced for the current validation rules.";

    private final FoodItemRepository foodItemRepository;
    private final FoodItemSearchAliasRepository foodItemSearchAliasRepository;
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

            AiProductQualityValidationResponseDto response = activeProvider().validateProductQuality(toAiValidationRequest(product));
            List<AiProductQualityValidationResponseDto.AiProductQualityIssueDto> issues =
                    response.getIssues() == null ? List.of() : response.getIssues();
            boolean hasIssue = false;

            for (AiProductQualityValidationResponseDto.AiProductQualityIssueDto issue : issues) {
                ProductQualitySuggestionEntity suggestion = buildAiSuggestion(product, issue);
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
        if (suggestionType == ProductQualitySuggestionType.NAME_CLEANUP) {
            applyNameCleanupSuggestion(suggestion, reviewedBy);
        } else if (suggestionType == ProductQualitySuggestionType.SEARCH_ALIAS) {
            applySearchAliasSuggestion(suggestion, reviewedBy);
        } else if (isSupportedFieldSuggestion(suggestion)) {
            applyFieldSuggestion(suggestion, reviewedBy);
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
                productQualitySuggestionRepository.findById(suggestionId)
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
        request.setProductId(product.getId());
        request.setName(product.getName());
        request.setBrand(product.getBrand());
        request.setBarcode(product.getBarcode());
        request.setSourceKey(product.getSourceKey());
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
        return request;
    }

    private ProductQualitySuggestionEntity buildAiSuggestion(FoodItemEntity product, AiProductQualityValidationResponseDto.AiProductQualityIssueDto issue) {
        if (issue == null || issue.getSuggestionType() == null) {
            return null;
        }
        ProductQualitySuggestionEntity suggestion = new ProductQualitySuggestionEntity();
        suggestion.setFoodItem(product);
        suggestion.setSuggestionType(issue.getSuggestionType());
        suggestion.setSource(ProductQualitySuggestionSource.AI_ASSISTED);
        suggestion.setStatus(ProductQualitySuggestionStatus.OPEN);
        suggestion.setFieldName(trimToNull(issue.getFieldName()));
        suggestion.setCurrentValue(trimToMax(issue.getCurrentValue(), 1000));
        suggestion.setSuggestedValue(trimToMax(issue.getSuggestedValue(), 1000));
        suggestion.setReason(trimToMax(issue.getReason(), 1000));
        suggestion.setConfidenceScore(issue.getConfidenceScore() == null ? 50 : Math.max(0, Math.min(issue.getConfidenceScore(), 100)));
        return suggestion;
    }

    private String trimToMax(String value, int maxLength) {
        String normalized = trimToNull(value);
        if (normalized == null || normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
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
    private boolean isSupportedFieldSuggestion(ProductQualitySuggestionEntity suggestion) {
        return trimToNull(suggestion.getFieldName()) != null
                && trimToNull(suggestion.getSuggestedValue()) != null
                && isSupportedFieldName(suggestion.getFieldName());
    }

    private boolean isSupportedFieldName(String fieldName) {
        return switch (normalizeFieldName(fieldName)) {
            case "calories", "protein", "fat", "carbs", "fiber", "sugar", "sodium", "potassium",
                    "cholesterol", "calcium", "iron", "magnesium", "zinc", "vitamina", "vitaminc",
                    "vitamind", "vitamine", "vitaminb12", "saturatedfat", "transfat", "sugaralcohol",
                    "servingsizegrams", "servingunit", "imagesource", "imagestatus", "imageurl",
                    "externalimageurl", "displayimageurl", "verificationstatus", "marketregion",
                    "preparationstate", "nutriscore", "allergens" -> true;
            default -> false;
        };
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
            case "imagesource" -> product.getImageSource();
            case "imagestatus" -> product.getImageStatus();
            case "imageurl" -> product.getImageUrl();
            case "externalimageurl" -> product.getExternalImageUrl();
            case "displayimageurl" -> product.getDisplayImageUrl();
            case "verificationstatus" -> product.getVerificationStatus();
            case "marketregion" -> product.getMarketRegion();
            case "preparationstate" -> product.getPreparationState();
            case "nutriscore" -> product.getNutriScore();
            case "allergens" -> product.getAllergens();
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
            case "imagesource" -> product.setImageSource(parseEnum(ImageSource.class, suggestedValue, "imageSource"));
            case "imagestatus" -> product.setImageStatus(parseEnum(ImageStatus.class, suggestedValue, "imageStatus"));
            case "imageurl" -> product.setImageUrl(suggestedValue);
            case "externalimageurl" -> product.setExternalImageUrl(suggestedValue);
            case "displayimageurl" -> product.setDisplayImageUrl(suggestedValue);
            case "verificationstatus" -> product.setVerificationStatus(parseEnum(VerificationStatus.class, suggestedValue, "verificationStatus"));
            case "marketregion" -> product.setMarketRegion(parseEnum(MarketRegion.class, suggestedValue, "marketRegion"));
            case "preparationstate" -> product.setPreparationState(parseEnum(com.grun.calorietracker.enums.FoodPreparationState.class, suggestedValue, "preparationState"));
            case "nutriscore" -> product.setNutriScore(suggestedValue);
            case "allergens" -> product.setAllergens(suggestedValue);
            default -> throw new IllegalArgumentException("Unsupported product quality suggestion field: " + fieldName);
        }
    }

    private double parseNonNegativeDouble(String value, String fieldName) {
        try {
            double parsed = Double.parseDouble(value.trim());
            if (parsed < 0) {
                throw new IllegalArgumentException(fieldName + " must not be negative.");
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












