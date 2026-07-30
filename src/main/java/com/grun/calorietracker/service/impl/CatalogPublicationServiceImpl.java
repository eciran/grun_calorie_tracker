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
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class CatalogPublicationServiceImpl implements CatalogPublicationService {

    private final FoodItemRepository foodItemRepository;
    private final AdminAuditService adminAuditService;

    @Override
    @Transactional
    public FoodItemEntity publishNew(
            FoodItemEntity product,
            String actor,
            String reason,
            String correlationId
    ) {
        if (product.getId() != null) {
            throw new IllegalArgumentException("New publication cannot reference a persisted product");
        }
        return publishGuarded(product, actor, reason, correlationId);
    }

    @Override
    @Transactional
    public FoodItemEntity publish(
            Long productId,
            String actor,
            String reason,
            String correlationId
    ) {
        FoodItemEntity product = foodItemRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException("Food item not found"));
        return publishGuarded(product, actor, reason, correlationId);
    }

    private FoodItemEntity publishGuarded(
            FoodItemEntity product,
            String actor,
            String reason,
            String correlationId
    ) {
        requireText(actor, "Publication actor is required");
        requireText(reason, "Publication reason is required");
        if (Boolean.TRUE.equals(product.getIsCustom())
                || product.getPublicationStatus() == CatalogPublicationStatus.PRIVATE_USER) {
            throw new IllegalArgumentException("Private user products cannot be published directly");
        }
        if (product.getVerificationStatus() == VerificationStatus.REJECTED) {
            throw new IllegalArgumentException("Rejected products cannot be published");
        }
        requireText(product.getName(), "Product name is required for publication");
        if (product.getCalories() == null || product.getCalories() < 0.0 || product.getCalories() > 900.0) {
            throw new IllegalArgumentException("Valid calories are required for publication");
        }

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
