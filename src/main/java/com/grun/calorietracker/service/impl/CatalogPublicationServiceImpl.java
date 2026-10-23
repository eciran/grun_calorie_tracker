package com.grun.calorietracker.service.impl;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.enums.AdminAuditActionType;
import com.grun.calorietracker.enums.AdminAuditTargetType;
import com.grun.calorietracker.enums.CatalogPublicationStatus;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.exception.ProductNotFoundException;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.service.AdminAuditService;
import com.grun.calorietracker.service.CatalogPublicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import com.grun.calorietracker.service.support.ExternalFoodImportFence;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CatalogPublicationServiceImpl implements CatalogPublicationService {

    private final FoodItemRepository foodItemRepository;
    private final AdminAuditService adminAuditService;
    private ExternalFoodImportFence externalFoodImportFence;

    @Autowired
    void setExternalFoodImportFence(ExternalFoodImportFence fence) { this.externalFoodImportFence = fence; }

    private void acquireCatalogWriteFence() {
        if (externalFoodImportFence != null) externalFoodImportFence.acquire();
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {"foodProductById", "foodProductByBarcode", "foodProductSearch"}, allEntries = true, cacheResolver = "foodProductCacheResolver")
    public FoodItemEntity publishNew(
            FoodItemEntity product,
            String actor,
            String reason,
            String correlationId
    ) {
        if (product.getId() != null) {
            throw new IllegalArgumentException("New publication cannot reference a persisted product");
        }
        acquireCatalogWriteFence();
        return publishGuarded(product, actor, reason, correlationId);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {"foodProductById", "foodProductByBarcode", "foodProductSearch"}, allEntries = true, cacheResolver = "foodProductCacheResolver")
    public FoodItemEntity publish(
            Long productId,
            String actor,
            String reason,
            String correlationId
    ) {
        acquireCatalogWriteFence();
        FoodItemEntity product = foodItemRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Food item not found"));
        return publishGuarded(product, actor, reason, correlationId);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {"foodProductById", "foodProductByBarcode", "foodProductSearch"}, allEntries = true, cacheResolver = "foodProductCacheResolver")
    public List<FoodItemEntity> publishBatch(
            List<Long> productIds,
            String actor,
            String reason,
            String correlationId
    ) {
        requireText(actor, "Publication actor is required");
        requireText(reason, "Publication reason is required");
        if (productIds == null || productIds.isEmpty() || productIds.size() > 100) {
            throw new IllegalArgumentException("Between 1 and 100 product ids are required");
        }
        List<Long> uniqueIds = List.copyOf(new LinkedHashSet<>(productIds));
        if (uniqueIds.size() != productIds.size()) {
            throw new IllegalArgumentException("Duplicate product ids are not allowed");
        }

        acquireCatalogWriteFence();
        List<FoodItemEntity> products = foodItemRepository.findAllById(uniqueIds);
        if (products.size() != uniqueIds.size()) {
            throw new ProductNotFoundException("One or more food items were not found");
        }
        Map<Long, FoodItemEntity> byId = products.stream()
                .collect(java.util.stream.Collectors.toMap(FoodItemEntity::getId, product -> product));
        List<FoodItemEntity> ordered = uniqueIds.stream().map(byId::get).toList();
        ordered.forEach(product -> validatePublishable(product, actor, reason));
        return ordered.stream()
                .map(product -> publishValidated(product, actor, reason, correlationId))
                .toList();
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = {"foodProductById", "foodProductByBarcode", "foodProductSearch"}, allEntries = true, cacheResolver = "foodProductCacheResolver")
    public List<FoodItemEntity> verifyAndPublishBatch(
            List<Long> productIds,
            String actor,
            String reason,
            String correlationId
    ) {
        requireText(actor, "Publication actor is required");
        requireText(reason, "Publication reason is required");
        if (productIds == null || productIds.isEmpty() || productIds.size() > 100) {
            throw new IllegalArgumentException("Between 1 and 100 product ids are required");
        }
        List<Long> uniqueIds = List.copyOf(new LinkedHashSet<>(productIds));
        if (uniqueIds.size() != productIds.size()) {
            throw new IllegalArgumentException("Duplicate product ids are not allowed");
        }

        acquireCatalogWriteFence();
        List<FoodItemEntity> products = foodItemRepository.findAllById(uniqueIds);
        if (products.size() != uniqueIds.size()) {
            throw new ProductNotFoundException("One or more food items were not found");
        }
        Map<Long, FoodItemEntity> byId = products.stream()
                .collect(java.util.stream.Collectors.toMap(FoodItemEntity::getId, product -> product));
        List<FoodItemEntity> ordered = uniqueIds.stream().map(byId::get).toList();

        List<String> validationFailures = ordered.stream()
                .flatMap(product -> publicationIssues(product, false).stream()
                        .map(issue -> describeProduct(product) + ": " + issue))
                .toList();
        if (!validationFailures.isEmpty()) {
            throw new IllegalArgumentException(
                    "Publication validation failed: " + String.join("; ", validationFailures)
            );
        }
        return ordered.stream().map(product -> {
            VerificationStatus previousVerification = product.getVerificationStatus();
            if (previousVerification != VerificationStatus.VERIFIED) {
                product.setVerificationStatus(VerificationStatus.VERIFIED);
                adminAuditService.record(
                        actor,
                        AdminAuditActionType.CATALOG_REVIEW_ASSIGNMENT,
                        AdminAuditTargetType.CATALOG_REVIEW_ITEM,
                        product.getId() == null ? product.getSourceKey() : product.getId().toString(),
                        Map.of("verificationStatus", String.valueOf(previousVerification)),
                        Map.of("verificationStatus", VerificationStatus.VERIFIED.name(), "reason", reason),
                        correlationId
                );
            }
            return publishValidated(product, actor, reason, correlationId);
        }).toList();
    }

    private FoodItemEntity publishGuarded(
            FoodItemEntity product,
            String actor,
            String reason,
            String correlationId
    ) {
        validatePublishable(product, actor, reason);
        return publishValidated(product, actor, reason, correlationId);
    }

    private void validatePublishable(FoodItemEntity product, String actor, String reason) {
        requireText(actor, "Publication actor is required");
        requireText(reason, "Publication reason is required");
        validatePublication(product, true);
    }

    private void validatePublication(FoodItemEntity product, boolean requireVerified) {
        List<String> issues = publicationIssues(product, requireVerified);
        if (!issues.isEmpty()) {
            throw new IllegalArgumentException(
                    "Publication validation failed: " + describeProduct(product) + ": " + String.join(", ", issues)
            );
        }
    }

    private List<String> publicationIssues(FoodItemEntity product, boolean requireVerified) {
        List<String> issues = new ArrayList<>();
        if (product == null) {
            issues.add("product is missing");
            return issues;
        }
        if (Boolean.TRUE.equals(product.getIsCustom())
                || product.getPublicationStatus() == CatalogPublicationStatus.PRIVATE_USER) {
            issues.add("publicationStatus: private user products cannot be published directly");
        }
        if (product.getName() == null || product.getName().isBlank()) {
            issues.add("name: required");
        }
        addNutritionIssue(issues, "calories", product.getCalories(), 1000.0);
        addNutritionIssue(issues, "protein", product.getProtein(), 100.0);
        addNutritionIssue(issues, "fat", product.getFat(), 100.0);
        addNutritionIssue(issues, "carbs", product.getCarbs(), 100.0);
        addOptionalNonNegativeIssue(issues, "fiber", product.getFiber());
        addOptionalNonNegativeIssue(issues, "sugar", product.getSugar());
        addOptionalNonNegativeIssue(issues, "sodium", product.getSodium());
        if (product.getSugar() != null && product.getCarbs() != null && product.getSugar() > product.getCarbs()) {
            issues.add("sugar: cannot exceed carbs");
        }
        if (product.getSaturatedFat() != null && product.getFat() != null && product.getSaturatedFat() > product.getFat()) {
            issues.add("saturatedFat: cannot exceed fat");
        }
        if (product.getTransFat() != null && product.getFat() != null && product.getTransFat() > product.getFat()) {
            issues.add("transFat: cannot exceed fat");
        }
        if (product.getSodium() != null && Double.isFinite(product.getSodium()) && product.getSodium() > 10.0) {
            issues.add("sodium: must be at most 10");
        }
        if (product.getNutritionReferenceUnit() == null) {
            issues.add("nutritionReferenceUnit: required (PER_100G, PER_100ML or PER_SERVING)");
        }
        if (requireVerified && product.getVerificationStatus() != VerificationStatus.VERIFIED) {
            issues.add("verificationStatus: must be VERIFIED");
        } else if (!requireVerified && product.getVerificationStatus() == VerificationStatus.REJECTED) {
            issues.add("verificationStatus: rejected products require individual review");
        }
        if (!requireVerified && product.getPublicationStatus() == CatalogPublicationStatus.PUBLISHED) {
            issues.add("publicationStatus: product is already PUBLISHED");
        }
        return issues;
    }

    private void addNutritionIssue(List<String> issues, String field, Double value, double maximum) {
        if (value == null) {
            issues.add(field + ": required");
        } else if (!Double.isFinite(value)) {
            issues.add(field + ": must be a finite number");
        } else if (value < 0.0) {
            issues.add(field + ": must be non-negative");
        } else if (value > maximum) {
            issues.add(field + ": must be at most " + (int) maximum);
        }
    }

    private void addOptionalNonNegativeIssue(List<String> issues, String field, Double value) {
        if (value != null && (!Double.isFinite(value) || value < 0.0)) {
            issues.add(field + ": must be a finite non-negative number when provided");
        }
    }

    private String describeProduct(FoodItemEntity product) {
        if (product == null) return "Product <missing>";
        String identity = product.getId() == null ? product.getSourceKey() : "#" + product.getId();
        if (identity == null || identity.isBlank()) identity = "<unsaved>";
        String name = product.getName() == null || product.getName().isBlank() ? "unnamed" : product.getName();
        return "Product " + identity + " (" + name + ")";
    }

    private FoodItemEntity publishValidated(
            FoodItemEntity product,
            String actor,
            String reason,
            String correlationId
    ) {
        CatalogPublicationStatus oldStatus = product.getPublicationStatus();
        product.setPublicationStatus(CatalogPublicationStatus.PUBLISHED);
        FoodItemEntity saved = foodItemRepository.save(product);
        adminAuditService.record(
                actor,
                AdminAuditActionType.CATALOG_REVIEW_ASSIGNMENT,
                AdminAuditTargetType.CATALOG_REVIEW_ITEM,
                saved.getId() == null ? saved.getSourceKey() : saved.getId().toString(),
                Map.of("publicationStatus", String.valueOf(oldStatus)),
                Map.of(
                        "publicationStatus", CatalogPublicationStatus.PUBLISHED.name(),
                        "reason", reason
                ),
                correlationId
        );
        return saved;
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
