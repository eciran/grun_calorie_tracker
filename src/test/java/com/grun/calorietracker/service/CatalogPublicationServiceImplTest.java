package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.enums.AdminAuditActionType;
import com.grun.calorietracker.enums.AdminAuditTargetType;
import com.grun.calorietracker.enums.CatalogPublicationStatus;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.service.impl.CatalogPublicationServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogPublicationServiceImplTest {

    @Mock
    private FoodItemRepository foodItemRepository;
    @Mock
    private AdminAuditService adminAuditService;
    @InjectMocks
    private CatalogPublicationServiceImpl service;

    @Test
    void publishAppliesGuardPersistsAndAudits() {
        FoodItemEntity product = candidate();
        product.setId(7L);
        when(foodItemRepository.findById(7L)).thenReturn(Optional.of(product));
        when(foodItemRepository.save(product)).thenReturn(product);

        FoodItemEntity result = service.publish(7L, "owner@grun.local", "Review approved", "cid-1");

        assertEquals(CatalogPublicationStatus.PUBLISHED, result.getPublicationStatus());
        verify(adminAuditService).record(
                "owner@grun.local",
                AdminAuditActionType.CATALOG_REVIEW_ASSIGNMENT,
                AdminAuditTargetType.CATALOG_REVIEW_ITEM,
                "7",
                java.util.Map.of("publicationStatus", "INTERNAL_REVIEW"),
                java.util.Map.of("publicationStatus", "PUBLISHED", "reason", "Review approved"),
                "cid-1"
        );
    }

    @Test
    void publishRejectsPrivateUserProduct() {
        FoodItemEntity product = candidate();
        product.setId(8L);
        product.setIsCustom(true);
        product.setPublicationStatus(CatalogPublicationStatus.PRIVATE_USER);
        when(foodItemRepository.findById(8L)).thenReturn(Optional.of(product));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.publish(8L, "owner@grun.local", "Invalid attempt", null)
        );
    }

    private FoodItemEntity candidate() {
        FoodItemEntity product = new FoodItemEntity();
        product.setName("Candidate");
        product.setCalories(120.0);
        product.setVerificationStatus(VerificationStatus.VERIFIED);
        product.setPublicationStatus(CatalogPublicationStatus.INTERNAL_REVIEW);
        product.setIsCustom(false);
        return product;
    }
}
