package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.FoodProductDuplicateGroupDto;
import com.grun.calorietracker.dto.FoodProductDuplicateGroupPageDto;
import com.grun.calorietracker.dto.FoodProductMergeRequestDto;
import com.grun.calorietracker.dto.FoodProductMergeResponseDto;
import com.grun.calorietracker.dto.FoodProductNutritionCorrectionImportResultDto;
import com.grun.calorietracker.dto.FoodProductQualityIssueBackfillResultDto;
import com.grun.calorietracker.dto.FoodProductQualityIssueDto;
import com.grun.calorietracker.dto.FoodProductReviewAuditDto;
import com.grun.calorietracker.dto.FoodProductReviewAuditPageDto;
import com.grun.calorietracker.dto.FoodProductReviewPageDto;
import com.grun.calorietracker.dto.FoodProductReviewRequestDto;
import com.grun.calorietracker.dto.FoodSearchAliasDto;
import com.grun.calorietracker.dto.FoodSearchAliasRequestDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodItemSearchAliasEntity;
import com.grun.calorietracker.entity.FoodProductQualityIssueEntity;
import com.grun.calorietracker.entity.FoodProductReviewAuditEntity;
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodDataSource;
import com.grun.calorietracker.enums.FoodProductQualityIssue;
import com.grun.calorietracker.enums.FoodPreparationState;
import com.grun.calorietracker.enums.FoodProductReviewAuditAction;
import com.grun.calorietracker.enums.FoodSearchAliasType;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.exception.ResourceNotFoundException;
import com.grun.calorietracker.mapper.FoodItemMapper;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodItemSearchAliasRepository;
import com.grun.calorietracker.repository.FoodLogsRepository;
import com.grun.calorietracker.repository.FoodProductQualityIssueRepository;
import com.grun.calorietracker.repository.FoodProductReviewAuditRepository;
import com.grun.calorietracker.repository.UserFavoriteRepository;
import com.grun.calorietracker.service.FoodProductReviewService;
import com.grun.calorietracker.service.support.FoodProductNormalizationRules;
import com.grun.calorietracker.service.support.FoodProductQualityIssueTracker;
import com.grun.calorietracker.service.support.FoodProductQualityRules;
import com.grun.calorietracker.service.support.NutritionValueNormalizer;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class FoodProductReviewServiceImpl implements FoodProductReviewService {

    private final FoodItemRepository foodItemRepository;
    private final FoodLogsRepository foodLogsRepository;
    private final UserFavoriteRepository userFavoriteRepository;
    private final FoodProductReviewAuditRepository foodProductReviewAuditRepository;
    private final FoodProductQualityIssueRepository foodProductQualityIssueRepository;
    private final FoodItemSearchAliasRepository foodItemSearchAliasRepository;
    private final FoodProductQualityIssueTracker foodProductQualityIssueTracker;

    public FoodProductReviewServiceImpl(
            FoodItemRepository foodItemRepository,
            FoodLogsRepository foodLogsRepository,
            UserFavoriteRepository userFavoriteRepository,
            FoodProductReviewAuditRepository foodProductReviewAuditRepository,
            FoodProductQualityIssueRepository foodProductQualityIssueRepository,
            FoodItemSearchAliasRepository foodItemSearchAliasRepository,
            FoodProductQualityIssueTracker foodProductQualityIssueTracker
    ) {
        this.foodItemRepository = foodItemRepository;
        this.foodLogsRepository = foodLogsRepository;
        this.userFavoriteRepository = userFavoriteRepository;
        this.foodProductReviewAuditRepository = foodProductReviewAuditRepository;
        this.foodProductQualityIssueRepository = foodProductQualityIssueRepository;
        this.foodItemSearchAliasRepository = foodItemSearchAliasRepository;
        this.foodProductQualityIssueTracker = foodProductQualityIssueTracker;
    }
    @Override
    @Transactional(readOnly = true)
    public List<FoodProductDto> getProductsForReview(VerificationStatus verificationStatus, ImageStatus imageStatus) {
        return getProductsForReview(verificationStatus, imageStatus, 0, 100).getContent();
    }

    @Override
    @Transactional(readOnly = true)
    public FoodProductReviewPageDto getProductsForReview(
            VerificationStatus verificationStatus,
            ImageStatus imageStatus,
            int page,
            int size
    ) {
        return getProductsForReview(verificationStatus, imageStatus, null, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public FoodProductReviewPageDto getProductsForReview(
            VerificationStatus verificationStatus,
            ImageStatus imageStatus,
            MarketRegion marketRegion,
            int page,
            int size
    ) {
        return getProductsForReview(verificationStatus, imageStatus, marketRegion, null, null, null, null, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public FoodProductReviewPageDto getProductsForReview(
            VerificationStatus verificationStatus,
            ImageStatus imageStatus,
            MarketRegion marketRegion,
            FoodCatalogType catalogType,
            int page,
            int size
    ) {
        return getProductsForReview(verificationStatus, imageStatus, marketRegion, catalogType, null, null, null, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public FoodProductReviewPageDto getProductsForReview(
            VerificationStatus verificationStatus,
            ImageStatus imageStatus,
            MarketRegion marketRegion,
            FoodCatalogType catalogType,
            FoodDataSource dataSource,
            int page,
            int size
    ) {
        return getProductsForReview(verificationStatus, imageStatus, marketRegion, catalogType, dataSource, null, null, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public FoodProductReviewPageDto getProductsForReview(
            VerificationStatus verificationStatus,
            ImageStatus imageStatus,
            MarketRegion marketRegion,
            FoodCatalogType catalogType,
            FoodDataSource dataSource,
            FoodProductQualityIssue qualityIssue,
            int page,
            int size
    ) {
        return getProductsForReview(
                verificationStatus,
                imageStatus,
                marketRegion,
                catalogType,
                dataSource,
                qualityIssue,
                null,
                page,
                size
        );
    }

    @Override
    @Transactional(readOnly = true)
    public FoodProductReviewPageDto getProductsForReview(
            VerificationStatus verificationStatus,
            ImageStatus imageStatus,
            MarketRegion marketRegion,
            FoodCatalogType catalogType,
            FoodDataSource dataSource,
            FoodProductQualityIssue qualityIssue,
            String searchQuery,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), normalizePageSize(size), buildReviewSort());
        Page<FoodItemEntity> products = foodItemRepository.findAll(
                buildReviewSpecification(verificationStatus, imageStatus, marketRegion, catalogType, dataSource, qualityIssue, searchQuery),
                pageable
        );
        return toPageDto(products);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {"foodProductById", "foodProductByBarcode", "foodProductSearch"}, allEntries = true)
    public FoodProductDto updateProductReview(Long id, FoodProductReviewRequestDto request, String reviewedBy) {
        if (request == null) {
            throw new IllegalArgumentException("Review request must not be empty.");
        }

        FoodItemEntity product = foodItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Food product not found with id: " + id));

        List<FoodProductReviewAuditEntity> audits = new ArrayList<>();
        String reviewNote = trimToNull(request.getReviewNote());
        validateRejectionNote(request, reviewNote);

        String productName = trimToNull(request.getProductName());
        if (productName != null) {
            addAuditIfChanged(
                    audits,
                    product,
                    reviewedBy,
                    FoodProductReviewAuditAction.REVIEW_UPDATE,
                    "productName",
                    product.getName(),
                    productName,
                    reviewNote
            );
            product.setName(productName);
        }

        String displayImageUrl = trimToNull(request.getDisplayImageUrl());
        if (displayImageUrl != null) {
            validateDisplayImageUrl(displayImageUrl);
            addAuditIfChanged(
                    audits,
                    product,
                    reviewedBy,
                    FoodProductReviewAuditAction.IMAGE_CHANGE,
                    "displayImageUrl",
                    product.getDisplayImageUrl(),
                    displayImageUrl,
                    reviewNote
            );
            product.setDisplayImageUrl(displayImageUrl);
        }

        if (request.getVerificationStatus() != null) {
            addAuditIfChanged(
                    audits,
                    product,
                    reviewedBy,
                    FoodProductReviewAuditAction.STATUS_CHANGE,
                    "verificationStatus",
                    product.getVerificationStatus(),
                    request.getVerificationStatus(),
                    reviewNote
            );
            product.setVerificationStatus(request.getVerificationStatus());
        }

        if (request.getImageSource() != null) {
            addAuditIfChanged(
                    audits,
                    product,
                    reviewedBy,
                    FoodProductReviewAuditAction.IMAGE_CHANGE,
                    "imageSource",
                    product.getImageSource(),
                    request.getImageSource(),
                    reviewNote
            );
            product.setImageSource(request.getImageSource());
        }

        if (request.getImageStatus() != null) {
            addAuditIfChanged(
                    audits,
                    product,
                    reviewedBy,
                    FoodProductReviewAuditAction.IMAGE_CHANGE,
                    "imageStatus",
                    product.getImageStatus(),
                    request.getImageStatus(),
                    reviewNote
            );
            product.setImageStatus(request.getImageStatus());
        }

        if (request.getMarketRegion() != null) {
            addAuditIfChanged(
                    audits,
                    product,
                    reviewedBy,
                    FoodProductReviewAuditAction.REVIEW_UPDATE,
                    "marketRegion",
                    product.getMarketRegion(),
                    request.getMarketRegion(),
                    reviewNote
            );
            product.setMarketRegion(request.getMarketRegion());
        }

        if (request.getCatalogType() != null) {
            addAuditIfChanged(
                    audits,
                    product,
                    reviewedBy,
                    FoodProductReviewAuditAction.REVIEW_UPDATE,
                    "catalogType",
                    product.getCatalogType(),
                    request.getCatalogType(),
                    reviewNote
            );
            product.setCatalogType(request.getCatalogType());
        }

        if (request.getPreparationState() != null) {
            addAuditIfChanged(
                    audits,
                    product,
                    reviewedBy,
                    FoodProductReviewAuditAction.REVIEW_UPDATE,
                    "preparationState",
                    product.getPreparationState(),
                    request.getPreparationState(),
                    reviewNote
            );
            product.setPreparationState(request.getPreparationState());
        }

        applyDoubleChange(audits, product, reviewedBy, "calories", product.getCalories(), NutritionValueNormalizer.calories(request.getCalories()), product::setCalories, reviewNote);
        applyDoubleChange(audits, product, reviewedBy, "protein", product.getProtein(), NutritionValueNormalizer.macro(request.getProtein()), product::setProtein, reviewNote);
        applyDoubleChange(audits, product, reviewedBy, "fat", product.getFat(), NutritionValueNormalizer.macro(request.getFat()), product::setFat, reviewNote);
        applyDoubleChange(audits, product, reviewedBy, "carbs", product.getCarbs(), NutritionValueNormalizer.macro(request.getCarbs()), product::setCarbs, reviewNote);
        applyDoubleChange(audits, product, reviewedBy, "fiber", product.getFiber(), NutritionValueNormalizer.macro(request.getFiber()), product::setFiber, reviewNote);
        applyDoubleChange(audits, product, reviewedBy, "sugar", product.getSugar(), NutritionValueNormalizer.macro(request.getSugar()), product::setSugar, reviewNote);
        applyDoubleChange(audits, product, reviewedBy, "sodium", product.getSodium(), NutritionValueNormalizer.sodium(request.getSodium()), product::setSodium, reviewNote);
        applyDoubleChange(audits, product, reviewedBy, "potassium", product.getPotassium(), NutritionValueNormalizer.micronutrient(request.getPotassium()), product::setPotassium, reviewNote);
        applyDoubleChange(audits, product, reviewedBy, "cholesterol", product.getCholesterol(), NutritionValueNormalizer.micronutrient(request.getCholesterol()), product::setCholesterol, reviewNote);
        applyDoubleChange(audits, product, reviewedBy, "calcium", product.getCalcium(), NutritionValueNormalizer.micronutrient(request.getCalcium()), product::setCalcium, reviewNote);
        applyDoubleChange(audits, product, reviewedBy, "iron", product.getIron(), NutritionValueNormalizer.micronutrient(request.getIron()), product::setIron, reviewNote);
        applyDoubleChange(audits, product, reviewedBy, "magnesium", product.getMagnesium(), NutritionValueNormalizer.micronutrient(request.getMagnesium()), product::setMagnesium, reviewNote);
        applyDoubleChange(audits, product, reviewedBy, "zinc", product.getZinc(), NutritionValueNormalizer.micronutrient(request.getZinc()), product::setZinc, reviewNote);
        applyDoubleChange(audits, product, reviewedBy, "vitaminA", product.getVitaminA(), NutritionValueNormalizer.micronutrient(request.getVitaminA()), product::setVitaminA, reviewNote);
        applyDoubleChange(audits, product, reviewedBy, "vitaminC", product.getVitaminC(), NutritionValueNormalizer.micronutrient(request.getVitaminC()), product::setVitaminC, reviewNote);
        applyDoubleChange(audits, product, reviewedBy, "vitaminD", product.getVitaminD(), NutritionValueNormalizer.micronutrient(request.getVitaminD()), product::setVitaminD, reviewNote);
        applyDoubleChange(audits, product, reviewedBy, "vitaminE", product.getVitaminE(), NutritionValueNormalizer.micronutrient(request.getVitaminE()), product::setVitaminE, reviewNote);
        applyDoubleChange(audits, product, reviewedBy, "vitaminB12", product.getVitaminB12(), NutritionValueNormalizer.micronutrient(request.getVitaminB12()), product::setVitaminB12, reviewNote);
        applyDoubleChange(audits, product, reviewedBy, "saturatedFat", product.getSaturatedFat(), NutritionValueNormalizer.macro(request.getSaturatedFat()), product::setSaturatedFat, reviewNote);
        applyDoubleChange(audits, product, reviewedBy, "transFat", product.getTransFat(), NutritionValueNormalizer.macro(request.getTransFat()), product::setTransFat, reviewNote);
        applyDoubleChange(audits, product, reviewedBy, "sugarAlcohol", product.getSugarAlcohol(), NutritionValueNormalizer.macro(request.getSugarAlcohol()), product::setSugarAlcohol, reviewNote);
        applyDoubleChange(
                audits,
                product,
                reviewedBy,
                "servingSizeGrams",
                product.getServingSizeGrams(),
                NutritionValueNormalizer.servingSize(request.getServingSizeGrams()),
                product::setServingSizeGrams,
                reviewNote
        );
        String servingUnit = trimToNull(request.getServingUnit());
        if (servingUnit != null) {
            addAuditIfChanged(
                    audits,
                    product,
                    reviewedBy,
                    FoodProductReviewAuditAction.REVIEW_UPDATE,
                    "servingUnit",
                    product.getServingUnit(),
                    servingUnit,
                    reviewNote
            );
            product.setServingUnit(servingUnit);
        }

        validateReviewState(product);
        product.setReviewedBy(trimToNull(reviewedBy) == null ? "unknown" : reviewedBy.trim());
        FoodProductQualityRules.markReviewed(product);

        FoodItemEntity savedProduct = foodItemRepository.save(product);
        if (!audits.isEmpty()) {
            foodProductReviewAuditRepository.saveAll(audits);
        }
        foodProductQualityIssueTracker.syncReviewIssues(savedProduct, reviewedBy);

        return FoodItemMapper.mapEntityToDto(savedProduct);
    }
    @Override
    @Transactional(readOnly = true)
    public List<FoodSearchAliasDto> getProductSearchAliases(Long productId, boolean activeOnly) {
        FoodItemEntity product = foodItemRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Food product not found: " + productId));
        List<FoodItemSearchAliasEntity> aliases = activeOnly
                ? foodItemSearchAliasRepository.findByFoodItemIdAndActiveTrueOrderByLanguageAscAliasAsc(product.getId())
                : foodItemSearchAliasRepository.findByFoodItemIdOrderByActiveDescLanguageAscAliasAsc(product.getId());
        return aliases.stream().map(this::toFoodSearchAliasDto).toList();
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {"foodProductById", "foodProductByBarcode", "foodProductSearch"}, allEntries = true)
    public FoodSearchAliasDto addProductSearchAlias(Long productId, FoodSearchAliasRequestDto request, String reviewedBy) {
        FoodItemEntity product = foodItemRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Food product not found: " + productId));
        String aliasText = trimToNull(request.getAlias());
        if (aliasText == null) {
            throw new IllegalArgumentException("Alias must not be empty.");
        }
        String normalizedAlias = FoodProductNormalizationRules.normalizeSearchAlias(aliasText);
        if (normalizedAlias == null) {
            throw new IllegalArgumentException("Alias must contain searchable text.");
        }

        FoodItemSearchAliasEntity alias = foodItemSearchAliasRepository
                .findByFoodItemIdAndNormalizedAliasAndLanguage(product.getId(), normalizedAlias, request.getLanguage())
                .orElseGet(FoodItemSearchAliasEntity::new);
        boolean isNewAlias = alias.getId() == null;
        String oldValue = isNewAlias ? null : alias.getAlias() + "|" + alias.getActive();

        alias.setFoodItem(product);
        alias.setAlias(aliasText);
        alias.setNormalizedAlias(normalizedAlias);
        alias.setLanguage(request.getLanguage());
        alias.setAliasType(request.getAliasType() == null ? FoodSearchAliasType.ADMIN_MANUAL : request.getAliasType());
        alias.setSource(trimToNull(request.getSource()) == null ? "admin" : request.getSource().trim());
        alias.setActive(request.getActive() == null || request.getActive());
        FoodItemSearchAliasEntity savedAlias = foodItemSearchAliasRepository.save(alias);

        List<FoodProductReviewAuditEntity> audits = new ArrayList<>();
        addAuditIfChanged(
                audits,
                product,
                reviewedBy,
                FoodProductReviewAuditAction.SEARCH_ALIAS_CHANGE,
                "searchAlias",
                oldValue,
                savedAlias.getAlias() + "|" + savedAlias.getActive(),
                isNewAlias ? "alias created" : "alias updated/reactivated"
        );
        foodProductReviewAuditRepository.saveAll(audits);
        return toFoodSearchAliasDto(savedAlias);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {"foodProductById", "foodProductByBarcode", "foodProductSearch"}, allEntries = true)
    public FoodSearchAliasDto updateProductSearchAliasStatus(Long productId, Long aliasId, boolean active, String reviewedBy) {
        FoodItemEntity product = foodItemRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Food product not found: " + productId));
        FoodItemSearchAliasEntity alias = foodItemSearchAliasRepository.findByIdAndFoodItemId(aliasId, product.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Food search alias not found: " + aliasId));
        Boolean oldValue = alias.getActive();
        alias.setActive(active);
        FoodItemSearchAliasEntity savedAlias = foodItemSearchAliasRepository.save(alias);

        List<FoodProductReviewAuditEntity> audits = new ArrayList<>();
        addAuditIfChanged(
                audits,
                product,
                reviewedBy,
                FoodProductReviewAuditAction.SEARCH_ALIAS_CHANGE,
                "searchAlias.active",
                oldValue,
                active,
                "alias status updated: " + savedAlias.getAlias()
        );
        foodProductReviewAuditRepository.saveAll(audits);
        return toFoodSearchAliasDto(savedAlias);
    }


    @Override
    @Transactional(readOnly = true)
    public FoodProductReviewAuditPageDto getProductReviewAudits(Long productId, int page, int size) {
        if (!foodItemRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Food product not found with id: " + productId);
        }

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                normalizePageSize(size),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        Page<FoodProductReviewAuditEntity> audits =
                foodProductReviewAuditRepository.findByFoodItemId(productId, pageable);

        FoodProductReviewAuditPageDto dto = new FoodProductReviewAuditPageDto();
        dto.setContent(audits.getContent().stream().map(this::toAuditDto).toList());
        dto.setPage(audits.getNumber());
        dto.setSize(audits.getSize());
        dto.setTotalElements(audits.getTotalElements());
        dto.setTotalPages(audits.getTotalPages());
        dto.setFirst(audits.isFirst());
        dto.setLast(audits.isLast());
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FoodProductQualityIssueDto> getProductQualityIssues(Long productId, boolean activeOnly) {
        if (!foodItemRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Food product not found with id: " + productId);
        }
        return (activeOnly
                ? foodProductQualityIssueRepository.findByFoodItemIdAndResolvedFalse(productId)
                : foodProductQualityIssueRepository.findByFoodItemIdOrderByResolvedAscLastDetectedAtDesc(productId))
                .stream()
                .map(this::toQualityIssueDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public FoodProductDuplicateGroupPageDto getDuplicateProductGroups(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), normalizePageSize(size));
        Page<String> duplicateBarcodes = foodItemRepository.findDuplicateNormalizedBarcodes(pageable);
        List<FoodProductDuplicateGroupDto> groups = buildDuplicateGroups(duplicateBarcodes.getContent());

        FoodProductDuplicateGroupPageDto dto = new FoodProductDuplicateGroupPageDto();
        dto.setContent(groups);
        dto.setPage(duplicateBarcodes.getNumber());
        dto.setSize(duplicateBarcodes.getSize());
        dto.setTotalElements(duplicateBarcodes.getTotalElements());
        dto.setTotalPages(duplicateBarcodes.getTotalPages());
        dto.setFirst(duplicateBarcodes.isFirst());
        dto.setLast(duplicateBarcodes.isLast());
        return dto;
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {"foodProductById", "foodProductByBarcode", "foodProductSearch"}, allEntries = true)
    public FoodProductMergeResponseDto mergeDuplicateProducts(FoodProductMergeRequestDto request, String reviewedBy) {
        validateMergeRequest(request);

        FoodItemEntity targetProduct = foodItemRepository.findById(request.getTargetProductId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Target food product not found with id: " + request.getTargetProductId()
                ));
        List<Long> duplicateProductIds = distinctDuplicateProductIds(request);
        if (duplicateProductIds.isEmpty()) {
            throw new IllegalArgumentException("Duplicate product ids must contain at least one valid id.");
        }
        List<FoodItemEntity> duplicateProducts = foodItemRepository.findAllById(duplicateProductIds);
        validateAllDuplicateProductsFound(duplicateProductIds, duplicateProducts);
        validateSameNormalizedBarcode(targetProduct, duplicateProducts);

        int removedFavoriteCount = userFavoriteRepository.deleteConflictingFavoritesBeforeMerge(
                targetProduct.getId(),
                duplicateProductIds
        );
        int reassignedFavoriteCount = userFavoriteRepository.reassignFoodItemReferences(
                targetProduct,
                duplicateProductIds
        );
        int reassignedFoodLogCount = foodLogsRepository.reassignFoodItemReferences(
                targetProduct,
                duplicateProductIds
        );

        targetProduct.setUsageCount(calculateMergedUsageCount(targetProduct, duplicateProducts));
        FoodProductQualityRules.updateQualityAndReviewPriority(targetProduct);
        FoodItemEntity savedTargetProduct = foodItemRepository.save(targetProduct);
        foodProductQualityIssueTracker.syncReviewIssues(savedTargetProduct, reviewedBy);
        foodProductReviewAuditRepository.save(buildMergeAudit(
                savedTargetProduct,
                duplicateProductIds,
                reviewedBy,
                reassignedFoodLogCount,
                reassignedFavoriteCount,
                removedFavoriteCount
        ));

        foodItemRepository.deleteAll(duplicateProducts);

        return new FoodProductMergeResponseDto(
                FoodItemMapper.mapEntityToDto(savedTargetProduct),
                duplicateProductIds,
                reassignedFoodLogCount,
                reassignedFavoriteCount,
                removedFavoriteCount
        );
    }

    @Override
    @Transactional
    public FoodProductQualityIssueBackfillResultDto backfillQualityIssues(int pageSize, String triggeredBy) {
        int safePageSize = Math.max(1, Math.min(pageSize, 1000));
        long scannedProducts = 0L;
        int processedBatches = 0;
        Page<FoodItemEntity> page;

        do {
            page = foodItemRepository.findAll(PageRequest.of(processedBatches, safePageSize, Sort.by("id").ascending()));
            if (!page.hasContent()) {
                break;
            }
            page.getContent().forEach(product -> {
                FoodProductQualityRules.updateQualityAndReviewPriority(product);
                foodProductQualityIssueTracker.syncReviewIssues(product, triggeredBy);
            });
            scannedProducts += page.getNumberOfElements();
            processedBatches++;
        } while (page.hasNext());

        return new FoodProductQualityIssueBackfillResultDto(scannedProducts, processedBatches, safePageSize);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {"foodProductById", "foodProductByBarcode", "foodProductSearch"}, allEntries = true)
    public FoodProductNutritionCorrectionImportResultDto importNutritionCorrections(MultipartFile file, String reviewedBy, boolean dryRun, boolean markVerified) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("CSV file is required.");
        }

        int totalRows = 0;
        int updatedRows = 0;
        int candidateRows = 0;
        List<String> errors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.trim().isEmpty()) {
                throw new IllegalArgumentException("CSV header row is required.");
            }
            char delimiter = detectDelimiter(headerLine);
            Map<String, Integer> headers = indexHeaders(parseDelimitedLine(headerLine, delimiter));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                totalRows++;
                CsvCorrectionRow row = new CsvCorrectionRow(totalRows + 1, headers, parseDelimitedLine(line, delimiter));
                try {
                    FoodItemEntity product = resolveCorrectionProduct(row);
                    FoodProductReviewRequestDto request = toCorrectionRequest(row);
                    if (markVerified && request.getVerificationStatus() == null) {
                        request.setVerificationStatus(VerificationStatus.VERIFIED);
                    }
                    if (dryRun) {
                        candidateRows++;
                    } else {
                        updateProductReview(product.getId(), request, reviewedBy);
                        updatedRows++;
                    }
                } catch (RuntimeException ex) {
                    if (errors.size() < 50) {
                        errors.add("Row " + row.rowNumber() + ": " + ex.getMessage());
                    }
                }
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("CSV file could not be read.");
        }

        int successfulRows = dryRun ? candidateRows : updatedRows;
        return new FoodProductNutritionCorrectionImportResultDto(totalRows, updatedRows, totalRows - successfulRows, errors, dryRun, candidateRows);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportProductsForReview(
            VerificationStatus verificationStatus,
            ImageStatus imageStatus,
            MarketRegion marketRegion,
            FoodCatalogType catalogType,
            FoodDataSource dataSource,
            FoodProductQualityIssue qualityIssue,
            String searchQuery,
            int limit
    ) {
        int safeLimit = Math.max(1, Math.min(limit, 10000));
        Page<FoodItemEntity> products = foodItemRepository.findAll(
                buildReviewSpecification(verificationStatus, imageStatus, marketRegion, catalogType, dataSource, qualityIssue, searchQuery),
                PageRequest.of(0, safeLimit, buildReviewSort())
        );
        StringBuilder csv = new StringBuilder();
        csv.append(String.join(",",
                "id",
                "barcode",
                "source_key",
                "product_name",
                "brand",
                "market_region",
                "catalog_type",
                "preparation_state",
                "verification_status",
                "image_status",
                "image_source",
                "display_image_url",
                "calories",
                "protein",
                "fat",
                "carbs",
                "fiber",
                "sugar",
                "sodium",
                "potassium",
                "cholesterol",
                "calcium",
                "iron",
                "magnesium",
                "zinc",
                "vitamin_a",
                "vitamin_c",
                "vitamin_d",
                "vitamin_e",
                "vitamin_b12",
                "saturated_fat",
                "trans_fat",
                "sugar_alcohol",
                "serving_size_grams",
                "serving_unit"
        )).append('\n');
        products.getContent().forEach(product -> appendExportRow(csv, product));
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void appendExportRow(StringBuilder csv, FoodItemEntity product) {
        csv.append(csv(product.getId()))
                .append(',').append(csv(product.getBarcode()))
                .append(',').append(csv(product.getSourceKey()))
                .append(',').append(csv(product.getName()))
                .append(',').append(csv(product.getBrand()))
                .append(',').append(csv(product.getMarketRegion()))
                .append(',').append(csv(product.getCatalogType()))
                .append(',').append(csv(product.getPreparationState()))
                .append(',').append(csv(product.getVerificationStatus()))
                .append(',').append(csv(product.getImageStatus()))
                .append(',').append(csv(product.getImageSource()))
                .append(',').append(csv(product.getDisplayImageUrl()))
                .append(',').append(csv(product.getCalories()))
                .append(',').append(csv(product.getProtein()))
                .append(',').append(csv(product.getFat()))
                .append(',').append(csv(product.getCarbs()))
                .append(',').append(csv(product.getFiber()))
                .append(',').append(csv(product.getSugar()))
                .append(',').append(csv(product.getSodium()))
                .append(',').append(csv(product.getPotassium()))
                .append(',').append(csv(product.getCholesterol()))
                .append(',').append(csv(product.getCalcium()))
                .append(',').append(csv(product.getIron()))
                .append(',').append(csv(product.getMagnesium()))
                .append(',').append(csv(product.getZinc()))
                .append(',').append(csv(product.getVitaminA()))
                .append(',').append(csv(product.getVitaminC()))
                .append(',').append(csv(product.getVitaminD()))
                .append(',').append(csv(product.getVitaminE()))
                .append(',').append(csv(product.getVitaminB12()))
                .append(',').append(csv(product.getSaturatedFat()))
                .append(',').append(csv(product.getTransFat()))
                .append(',').append(csv(product.getSugarAlcohol()))
                .append(',').append(csv(product.getServingSizeGrams()))
                .append(',').append(csv(product.getServingUnit()))
                .append('\n');
    }

    private String csv(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private void validateReviewState(FoodItemEntity product) {
        if (product.getVerificationStatus() == VerificationStatus.VERIFIED && trimToNull(product.getName()) == null) {
            throw new IllegalArgumentException("Verified product must have a product name.");
        }

    }

    private void validateRejectionNote(FoodProductReviewRequestDto request, String reviewNote) {
        boolean rejectsProduct = request.getVerificationStatus() == VerificationStatus.REJECTED;
        if (rejectsProduct && reviewNote == null) {
            throw new IllegalArgumentException("Review note is required when rejecting product data.");
        }
    }

    private void validateDisplayImageUrl(String displayImageUrl) {
        try {
            URI uri = new URI(displayImageUrl);
            String scheme = uri.getScheme();
            if (scheme == null || uri.getHost() == null ||
                    (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException("Display image URL must be an absolute HTTP or HTTPS URL.");
            }
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Display image URL must be a valid URL.");
        }
    }

    private void applyDoubleChange(
            List<FoodProductReviewAuditEntity> audits,
            FoodItemEntity product,
            String reviewedBy,
            String fieldName,
            Double oldValue,
            Double newValue,
            java.util.function.Consumer<Double> setter,
            String note
    ) {
        if (newValue == null) {
            return;
        }
        if (newValue < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative.");
        }
        addAuditIfChanged(
                audits,
                product,
                reviewedBy,
                FoodProductReviewAuditAction.REVIEW_UPDATE,
                fieldName,
                oldValue,
                newValue,
                note
        );
        setter.accept(newValue);
    }

    private void addAuditIfChanged(
            List<FoodProductReviewAuditEntity> audits,
            FoodItemEntity product,
            String reviewedBy,
            FoodProductReviewAuditAction action,
            String fieldName,
            Object oldValue,
            Object newValue,
            String note
    ) {
        if (Objects.equals(oldValue, newValue)) {
            return;
        }

        FoodProductReviewAuditEntity audit = new FoodProductReviewAuditEntity();
        audit.setFoodItem(product);
        audit.setReviewedBy(trimToNull(reviewedBy) == null ? "unknown" : reviewedBy.trim());
        audit.setActionType(action);
        audit.setFieldName(fieldName);
        audit.setOldValue(toAuditValue(oldValue));
        audit.setNewValue(toAuditValue(newValue));
        audit.setNote(note);
        audits.add(audit);
    }

    private String toAuditValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }


    private FoodSearchAliasDto toFoodSearchAliasDto(FoodItemSearchAliasEntity alias) {
        return new FoodSearchAliasDto(
                alias.getId(),
                alias.getFoodItem().getId(),
                alias.getAlias(),
                alias.getNormalizedAlias(),
                alias.getLanguage(),
                alias.getAliasType(),
                alias.getSource(),
                alias.getActive(),
                alias.getCreatedAt() == null ? null : alias.getCreatedAt().toString()
        );
    }
    private FoodProductReviewAuditDto toAuditDto(FoodProductReviewAuditEntity audit) {
        FoodProductReviewAuditDto dto = new FoodProductReviewAuditDto();
        dto.setId(audit.getId());
        dto.setFoodItemId(audit.getFoodItem().getId());
        dto.setReviewedBy(audit.getReviewedBy());
        dto.setActionType(audit.getActionType());
        dto.setFieldName(audit.getFieldName());
        dto.setOldValue(audit.getOldValue());
        dto.setNewValue(audit.getNewValue());
        dto.setNote(audit.getNote());
        dto.setCreatedAt(audit.getCreatedAt());
        return dto;
    }

    private FoodProductQualityIssueDto toQualityIssueDto(com.grun.calorietracker.entity.FoodProductQualityIssueEntity issue) {
        return new FoodProductQualityIssueDto(
                issue.getId(),
                issue.getFoodItem().getId(),
                issue.getIssueType(),
                issue.getIdentifier(),
                issue.getReason(),
                issue.getResolved(),
                issue.getFirstDetectedAt(),
                issue.getLastDetectedAt(),
                issue.getResolvedAt(),
                issue.getResolvedBy()
        );
    }

    private void validateMergeRequest(FoodProductMergeRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Merge request must not be empty.");
        }
        if (request.getTargetProductId() == null) {
            throw new IllegalArgumentException("Target product id must not be empty.");
        }
        if (request.getDuplicateProductIds() == null || request.getDuplicateProductIds().isEmpty()) {
            throw new IllegalArgumentException("Duplicate product ids must not be empty.");
        }
        if (request.getDuplicateProductIds().contains(request.getTargetProductId())) {
            throw new IllegalArgumentException("Target product id must not be included in duplicate product ids.");
        }
    }

    private FoodProductReviewAuditEntity buildMergeAudit(
            FoodItemEntity targetProduct,
            List<Long> duplicateProductIds,
            String reviewedBy,
            int reassignedFoodLogCount,
            int reassignedFavoriteCount,
            int removedFavoriteCount
    ) {
        FoodProductReviewAuditEntity audit = new FoodProductReviewAuditEntity();
        audit.setFoodItem(targetProduct);
        audit.setReviewedBy(trimToNull(reviewedBy) == null ? "unknown" : reviewedBy.trim());
        audit.setActionType(FoodProductReviewAuditAction.MERGE);
        audit.setFieldName("duplicateProductIds");
        audit.setOldValue(duplicateProductIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
        audit.setNewValue(String.valueOf(targetProduct.getId()));
        audit.setNote("reassignedFoodLogs=" + reassignedFoodLogCount
                + "; reassignedFavorites=" + reassignedFavoriteCount
                + "; removedConflictingFavorites=" + removedFavoriteCount);
        return audit;
    }

    private List<Long> distinctDuplicateProductIds(FoodProductMergeRequestDto request) {
        return request.getDuplicateProductIds().stream()
                .filter(id -> id != null && !id.equals(request.getTargetProductId()))
                .distinct()
                .toList();
    }

    private FoodItemEntity resolveCorrectionProduct(CsvCorrectionRow row) {
        String id = firstText(row, "id", "fooditemid", "food_item_id", "productid", "product_id");
        if (id != null) {
            try {
                return foodItemRepository.findById(Long.parseLong(id))
                        .orElseThrow(() -> new ResourceNotFoundException("Food product not found with id: " + id));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Product id must be numeric.");
            }
        }

        String sourceKey = firstText(row, "sourcekey", "source_key");
        if (sourceKey != null) {
            return foodItemRepository.findBySourceKey(sourceKey)
                    .orElseThrow(() -> new ResourceNotFoundException("Food product not found with source key: " + sourceKey));
        }

        String barcode = firstText(row, "barcode", "normalizedbarcode", "normalized_barcode", "code", "gtin", "ean", "upc");
        String normalizedBarcode = FoodProductNormalizationRules.normalizeBarcode(barcode);
        if (normalizedBarcode != null) {
            return foodItemRepository.findByNormalizedBarcode(normalizedBarcode)
                    .or(() -> foodItemRepository.findByBarcode(normalizedBarcode))
                    .orElseThrow(() -> new ResourceNotFoundException("Food product not found with barcode: " + normalizedBarcode));
        }

        throw new IllegalArgumentException("Correction row must contain id, source_key, or barcode.");
    }

    private FoodProductReviewRequestDto toCorrectionRequest(CsvCorrectionRow row) {
        FoodProductReviewRequestDto request = new FoodProductReviewRequestDto();
        request.setProductName(firstText(row, "product_name", "productname", "name"));
        request.setCalories(parseDouble(row, "calories", "energy_kcal_100g", "energy_kcal"));
        request.setProtein(parseDouble(row, "protein", "proteins_100g", "protein_100g"));
        request.setFat(parseDouble(row, "fat", "fat_100g"));
        request.setCarbs(parseDouble(row, "carbs", "carbohydrates", "carbohydrates_100g", "carbohydrate_100g"));
        request.setFiber(parseDouble(row, "fiber", "fiber_100g"));
        request.setSugar(parseDouble(row, "sugar", "sugars_100g"));
        request.setSodium(parseDouble(row, "sodium", "sodium_100g"));
        request.setPotassium(parseDouble(row, "potassium", "potassium_100g"));
        request.setCholesterol(parseDouble(row, "cholesterol", "cholesterol_100g"));
        request.setCalcium(parseDouble(row, "calcium", "calcium_100g"));
        request.setIron(parseDouble(row, "iron", "iron_100g"));
        request.setMagnesium(parseDouble(row, "magnesium", "magnesium_100g"));
        request.setZinc(parseDouble(row, "zinc", "zinc_100g"));
        request.setVitaminA(parseDouble(row, "vitamin_a", "vitamina", "vitamin_a_100g", "vitamina_100g"));
        request.setVitaminC(parseDouble(row, "vitamin_c", "vitaminc", "vitamin_c_100g", "vitaminc_100g"));
        request.setVitaminD(parseDouble(row, "vitamin_d", "vitamind", "vitamin_d_100g", "vitamind_100g"));
        request.setVitaminE(parseDouble(row, "vitamin_e", "vitamine", "vitamin_e_100g", "vitamine_100g"));
        request.setVitaminB12(parseDouble(row, "vitamin_b12", "vitaminb12", "vitamin_b12_100g", "vitaminb12_100g"));
        request.setSaturatedFat(parseDouble(row, "saturated_fat", "saturatedfat", "saturated_fat_100g", "saturatedfat_100g"));
        request.setTransFat(parseDouble(row, "trans_fat", "transfat", "trans_fat_100g", "transfat_100g"));
        request.setSugarAlcohol(parseDouble(row, "sugar_alcohol", "sugaralcohol", "sugar_alcohol_100g", "sugaralcohol_100g"));
        request.setServingSizeGrams(parseDouble(row, "servingsizegrams", "serving_size_grams", "serving_size", "serving_quantity"));
        request.setServingUnit(firstText(row, "servingunit", "serving_unit", "serving_quantity_unit"));
        request.setDisplayImageUrl(firstText(row, "displayimageurl", "display_image_url", "curated_image_url"));
        request.setMarketRegion(parseEnum(MarketRegion.class, firstText(row, "market_region", "marketregion", "region"), "market_region"));
        request.setCatalogType(parseEnum(FoodCatalogType.class, firstText(row, "catalog_type", "catalogtype", "type"), "catalog_type"));
        request.setPreparationState(parseEnum(FoodPreparationState.class, firstText(row, "preparation_state", "preparationstate", "prep_state", "cooking_state", "state"), "preparation_state"));
        request.setVerificationStatus(parseEnum(VerificationStatus.class, firstText(row, "verification_status", "verificationstatus"), "verification_status"));
        request.setImageStatus(parseEnum(ImageStatus.class, firstText(row, "image_status", "imagestatus"), "image_status"));
        String reviewNote = firstText(row, "reviewnote", "review_note", "note");
        request.setReviewNote(reviewNote == null ? "Bulk nutrition correction import." : reviewNote);
        return request;
    }

    private Double parseDouble(CsvCorrectionRow row, String... columns) {
        String value = firstText(row, columns);
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(value.replace(',', '.'));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid numeric value: " + value);
        }
    }

    private <E extends Enum<E>> E parseEnum(Class<E> enumClass, String value, String fieldName) {
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(enumClass, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(fieldName + " has unsupported value: " + value);
        }
    }

    private String firstText(CsvCorrectionRow row, String... columns) {
        for (String column : columns) {
            String value = trimToNull(row.value(column));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Map<String, Integer> indexHeaders(List<String> headers) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String normalized = normalizeHeader(headers.get(i));
            if (normalized != null) {
                index.put(normalized, i);
            }
        }
        return index;
    }

    private String normalizeHeader(String header) {
        String value = trimToNull(header);
        return value == null ? null : value.toLowerCase(Locale.ROOT)
                .replace("-", "_")
                .replace(" ", "_")
                .replace(".", "_");
    }

    private char detectDelimiter(String headerLine) {
        return count(headerLine, '\t') > count(headerLine, ',') ? '\t' : ',';
    }

    private int count(String value, char expected) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == expected) {
                count++;
            }
        }
        return count;
    }

    private List<String> parseDelimitedLine(String line, char delimiter) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == delimiter && !quoted) {
                values.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        values.add(current.toString().trim());
        return values;
    }

    private void validateAllDuplicateProductsFound(List<Long> duplicateProductIds, List<FoodItemEntity> duplicateProducts) {
        if (duplicateProducts.size() == duplicateProductIds.size()) {
            return;
        }

        List<Long> foundIds = duplicateProducts.stream()
                .map(FoodItemEntity::getId)
                .toList();
        List<Long> missingIds = duplicateProductIds.stream()
                .filter(id -> !foundIds.contains(id))
                .toList();
        throw new ResourceNotFoundException("Duplicate food products not found with ids: " + missingIds);
    }

    private void validateSameNormalizedBarcode(FoodItemEntity targetProduct, List<FoodItemEntity> duplicateProducts) {
        String targetNormalizedBarcode = FoodProductNormalizationRules.normalizeBarcode(targetProduct.getNormalizedBarcode());
        if (targetNormalizedBarcode == null) {
            throw new IllegalArgumentException("Target product must have a normalized barcode before merge.");
        }

        boolean allSameBarcode = duplicateProducts.stream()
                .map(FoodItemEntity::getNormalizedBarcode)
                .map(FoodProductNormalizationRules::normalizeBarcode)
                .allMatch(targetNormalizedBarcode::equals);
        if (!allSameBarcode) {
            throw new IllegalArgumentException("All merged products must share the same normalized barcode.");
        }
    }

    private long calculateMergedUsageCount(FoodItemEntity targetProduct, List<FoodItemEntity> duplicateProducts) {
        long targetUsageCount = targetProduct.getUsageCount() == null ? 0L : targetProduct.getUsageCount();
        long duplicateUsageCount = duplicateProducts.stream()
                .map(FoodItemEntity::getUsageCount)
                .mapToLong(value -> value == null ? 0L : value)
                .sum();
        return targetUsageCount + duplicateUsageCount;
    }

    private Specification<FoodItemEntity> buildReviewSpecification(
            VerificationStatus verificationStatus,
            ImageStatus imageStatus,
            MarketRegion marketRegion,
            FoodCatalogType catalogType,
            FoodDataSource dataSource,
            FoodProductQualityIssue qualityIssue,
            String searchQuery
    ) {
        VerificationStatus effectiveVerificationStatus = verificationStatus == null
                ? VerificationStatus.RAW_IMPORTED
                : verificationStatus;
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("verificationStatus"), effectiveVerificationStatus));
            if (imageStatus != null) {
                predicates.add(criteriaBuilder.equal(root.get("imageStatus"), imageStatus));
            }
            if (marketRegion != null) {
                predicates.add(criteriaBuilder.equal(root.get("marketRegion"), marketRegion));
            }
            if (catalogType != null) {
                predicates.add(criteriaBuilder.equal(root.get("catalogType"), catalogType));
            }
            if (dataSource != null) {
                predicates.add(criteriaBuilder.equal(root.get("dataSource"), dataSource));
            }
            Predicate qualityPredicate = buildQualityIssuePredicate(root, query, criteriaBuilder, qualityIssue);
            if (qualityPredicate != null) {
                predicates.add(qualityPredicate);
            }
            String normalizedSearchQuery = trimToNull(searchQuery);
            if (normalizedSearchQuery != null) {
                String like = "%" + normalizedSearchQuery.toLowerCase(Locale.ROOT) + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), like),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("brand")), like),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("barcode")), like),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("normalizedBarcode")), like),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("sourceKey")), like)
                ));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Predicate buildQualityIssuePredicate(
            Root<FoodItemEntity> root,
            CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
            FoodProductQualityIssue qualityIssue
    ) {
        if (qualityIssue == null) {
            return null;
        }

        Predicate activeIssuePredicate = activeQualityIssuePredicate(root, query, criteriaBuilder, qualityIssue);
        Predicate derivedPredicate = switch (qualityIssue) {
            case LOW_QUALITY -> criteriaBuilder.or(
                    criteriaBuilder.isNull(root.get("qualityScore")),
                    criteriaBuilder.lessThan(root.get("qualityScore"), 60)
            );
            case MISSING_IMAGE -> null;
            case MISSING_CALORIES -> criteriaBuilder.isNull(root.get("calories"));
            case MISSING_MACROS -> criteriaBuilder.and(
                    criteriaBuilder.isNull(root.get("protein")),
                    criteriaBuilder.isNull(root.get("fat")),
                    criteriaBuilder.isNull(root.get("carbs"))
            );
            case MISSING_SERVING_SIZE -> criteriaBuilder.isNull(root.get("servingSizeGrams"));
            case MISSING_REGION -> criteriaBuilder.isNull(root.get("marketRegion"));
            case UNSUPPORTED_REGION -> null;
            case MISSING_BARCODE -> criteriaBuilder.and(
                    criteriaBuilder.or(
                            criteriaBuilder.isNull(root.get("catalogType")),
                            criteriaBuilder.equal(root.get("catalogType"), FoodCatalogType.BRANDED_PRODUCT)
                    ),
                    isBlank(root, criteriaBuilder, "barcode"),
                    isBlank(root, criteriaBuilder, "normalizedBarcode")
            );
            case INVALID_BARCODE_FORMAT -> null;
            case SUSPICIOUS_CALORIES -> criteriaBuilder.greaterThan(root.get("calories"), 1000.0);
            case SUSPICIOUS_MACROS -> criteriaBuilder.or(
                    criteriaBuilder.greaterThan(root.get("protein"), 100.0),
                    criteriaBuilder.greaterThan(root.get("fat"), 100.0),
                    criteriaBuilder.greaterThan(root.get("carbs"), 100.0)
            );
            case MISSING_MICRONUTRIENTS -> criteriaBuilder.and(
                    criteriaBuilder.isNull(root.get("potassium")),
                    criteriaBuilder.isNull(root.get("calcium")),
                    criteriaBuilder.isNull(root.get("iron")),
                    criteriaBuilder.isNull(root.get("magnesium")),
                    criteriaBuilder.isNull(root.get("zinc")),
                    criteriaBuilder.isNull(root.get("vitaminA")),
                    criteriaBuilder.isNull(root.get("vitaminC")),
                    criteriaBuilder.isNull(root.get("vitaminD")),
                    criteriaBuilder.isNull(root.get("vitaminE")),
                    criteriaBuilder.isNull(root.get("vitaminB12"))
            );
            case MISSING_NUTRIENT_QUALITY_FIELDS -> criteriaBuilder.and(
                    criteriaBuilder.isNull(root.get("fiber")),
                    criteriaBuilder.isNull(root.get("sugar")),
                    criteriaBuilder.isNull(root.get("sodium")),
                    criteriaBuilder.isNull(root.get("saturatedFat")),
                    criteriaBuilder.isNull(root.get("transFat"))
            );
            case SUSPICIOUS_NUTRIENT_QUALITY -> criteriaBuilder.or(
                    criteriaBuilder.lessThan(root.get("fiber"), 0.0),
                    criteriaBuilder.lessThan(root.get("sugar"), 0.0),
                    criteriaBuilder.lessThan(root.get("sodium"), 0.0),
                    criteriaBuilder.lessThan(root.get("saturatedFat"), 0.0),
                    criteriaBuilder.lessThan(root.get("transFat"), 0.0),
                    criteriaBuilder.greaterThan(root.get("sugar"), root.get("carbs")),
                    criteriaBuilder.greaterThan(root.get("saturatedFat"), root.get("fat")),
                    criteriaBuilder.greaterThan(root.get("transFat"), root.get("fat")),
                    criteriaBuilder.greaterThan(root.get("sodium"), 10.0)
            );
        };
        return derivedPredicate == null
                ? activeIssuePredicate
                : criteriaBuilder.or(activeIssuePredicate, derivedPredicate);
    }

    private Predicate activeQualityIssuePredicate(
            Root<FoodItemEntity> root,
            CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
            FoodProductQualityIssue qualityIssue
    ) {
        var subquery = query.subquery(Long.class);
        Root<FoodProductQualityIssueEntity> issueRoot = subquery.from(FoodProductQualityIssueEntity.class);
        subquery.select(issueRoot.get("id"));
        subquery.where(
                criteriaBuilder.equal(issueRoot.get("foodItem"), root),
                criteriaBuilder.equal(issueRoot.get("issueType"), qualityIssue),
                criteriaBuilder.isFalse(issueRoot.get("resolved"))
        );
        return criteriaBuilder.exists(subquery);
    }

    private Predicate isBlank(
            jakarta.persistence.criteria.Root<FoodItemEntity> root,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder,
            String fieldName
    ) {
        return criteriaBuilder.or(
                criteriaBuilder.isNull(root.get(fieldName)),
                criteriaBuilder.equal(criteriaBuilder.trim(root.get(fieldName)), "")
        );
    }

    private Sort buildReviewSort() {
        return Sort.by(
                Sort.Order.desc("reviewPriority"),
                Sort.Order.desc("usageCount"),
                Sort.Order.asc("id")
        );
    }

    private int normalizePageSize(int size) {
        if (size < 1) {
            return 25;
        }
        return Math.min(size, 100);
    }

    private FoodProductReviewPageDto toPageDto(Page<FoodItemEntity> products) {
        FoodProductReviewPageDto dto = new FoodProductReviewPageDto();
        dto.setContent(FoodItemMapper.mapEntityListToDtoList(products.getContent()));
        dto.setPage(products.getNumber());
        dto.setSize(products.getSize());
        dto.setTotalElements(products.getTotalElements());
        dto.setTotalPages(products.getTotalPages());
        dto.setFirst(products.isFirst());
        dto.setLast(products.isLast());
        return dto;
    }

    private List<FoodProductDuplicateGroupDto> buildDuplicateGroups(List<String> normalizedBarcodes) {
        if (normalizedBarcodes.isEmpty()) {
            return List.of();
        }

        Sort productSort = Sort.by(
                Sort.Order.asc("normalizedBarcode"),
                Sort.Order.desc("qualityScore"),
                Sort.Order.desc("usageCount"),
                Sort.Order.asc("id")
        );
        List<FoodItemEntity> duplicateProducts = foodItemRepository.findByNormalizedBarcodeIn(
                normalizedBarcodes,
                productSort
        );

        Map<String, List<FoodItemEntity>> productsByBarcode = duplicateProducts.stream()
                .collect(Collectors.groupingBy(
                        FoodItemEntity::getNormalizedBarcode,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        return normalizedBarcodes.stream()
                .map(normalizedBarcode -> toDuplicateGroup(normalizedBarcode, productsByBarcode.get(normalizedBarcode)))
                .filter(group -> group.getProductCount() > 1)
                .toList();
    }

    private FoodProductDuplicateGroupDto toDuplicateGroup(String normalizedBarcode, List<FoodItemEntity> products) {
        List<FoodProductDto> productDtos = FoodItemMapper.mapEntityListToDtoList(
                products == null ? List.of() : products
        );
        return new FoodProductDuplicateGroupDto(normalizedBarcode, productDtos.size(), productDtos);
    }

    private record CsvCorrectionRow(int rowNumber, Map<String, Integer> headers, List<String> values) {
        private String value(String column) {
            Integer index = headers.get(column);
            if (index == null || index >= values.size()) {
                return null;
            }
            return values.get(index);
        }
    }
}
