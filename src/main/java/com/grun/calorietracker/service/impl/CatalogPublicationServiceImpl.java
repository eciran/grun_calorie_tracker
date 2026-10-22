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
import com.grun.calorietracker.service.support.FoodProductQualityRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import com.grun.calorietracker.service.support.ExternalFoodImportFence;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
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
        if (Boolean.TRUE.equals(product.getIsCustom())
                || product.getPublicationStatus() == CatalogPublicationStatus.PRIVATE_USER) {
            throw new IllegalArgumentException("Private user products cannot be published directly");
        }
        if (product.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new IllegalArgumentException("Only verified products can be published");
        }
        requireText(product.getName(), "Product name is required for publication");
        if (FoodProductQualityRules.hasCriticalIssue(product)
                || product.getNutritionReferenceUnit() == null) {
            throw new IllegalArgumentException("Complete, finite, non-negative core nutrition and an explicit nutrition reference unit are required for publication");
        }
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
