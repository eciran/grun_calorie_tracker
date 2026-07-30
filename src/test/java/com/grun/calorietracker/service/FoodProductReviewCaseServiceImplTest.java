package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodProductReviewCaseEntity;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.exception.RequestConflictException;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodProductReviewCaseRepository;
import com.grun.calorietracker.service.impl.FoodProductReviewCaseServiceImpl;
import com.grun.calorietracker.service.model.FoodProductReviewCaseCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FoodProductReviewCaseServiceImplTest {

    @Mock
    private FoodProductReviewCaseRepository reviewCaseRepository;
    @Mock
    private FoodItemRepository foodItemRepository;
    @InjectMocks
    private FoodProductReviewCaseServiceImpl service;

    @Test
    void duplicateFinalizeReturnsExistingCaseWithoutCreatingCandidate() {
        FoodProductReviewCaseEntity existing = new FoodProductReviewCaseEntity();
        existing.setId(10L);
        when(reviewCaseRepository.findByIdempotencyKey("idem-1")).thenReturn(Optional.of(existing));

        FoodProductReviewCaseEntity result = service.finalizeCase(command("idem-1"));

        assertSame(existing, result);
        verify(foodItemRepository, never()).saveAndFlush(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void newBarcodeCreatesOneInternalCandidateAndLinksCase() {
        when(reviewCaseRepository.findByIdempotencyKey("idem-2")).thenReturn(Optional.empty());
        when(foodItemRepository.findOne(any(Specification.class))).thenReturn(Optional.empty());
        when(foodItemRepository.saveAndFlush(any(FoodItemEntity.class))).thenAnswer(invocation -> {
            FoodItemEntity candidate = invocation.getArgument(0);
            candidate.setId(20L);
            return candidate;
        });
        when(reviewCaseRepository.saveAndFlush(any(FoodProductReviewCaseEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FoodProductReviewCaseEntity result = service.finalizeCase(command("idem-2"));

        assertEquals(FoodProductResolutionMode.NEW_CANDIDATE, result.getResolutionMode());
        assertEquals(CatalogPublicationStatus.INTERNAL_REVIEW, result.getFoodItem().getPublicationStatus());
        assertEquals(VerificationStatus.NEEDS_REVIEW, result.getFoodItem().getVerificationStatus());
    }

    @Test
    void approvalDoesNotPublishLinkedProduct() {
        FoodItemEntity product = new FoodItemEntity();
        product.setPublicationStatus(CatalogPublicationStatus.INTERNAL_REVIEW);
        FoodProductReviewCaseEntity reviewCase = new FoodProductReviewCaseEntity();
        reviewCase.setId(30L);
        reviewCase.setStatus(FoodProductReviewCaseStatus.IN_REVIEW);
        reviewCase.setFoodItem(product);
        when(reviewCaseRepository.findById(30L)).thenReturn(Optional.of(reviewCase));
        when(reviewCaseRepository.save(reviewCase)).thenReturn(reviewCase);

        FoodProductReviewCaseEntity result = service.transition(
                30L,
                FoodProductReviewCaseStatus.APPROVED,
                "reviewer@grun.local",
                "Evidence accepted"
        );

        assertEquals(FoodProductReviewCaseStatus.APPROVED, result.getStatus());
        assertEquals(CatalogPublicationStatus.INTERNAL_REVIEW, product.getPublicationStatus());
    }

    @Test
    void invalidTransitionIsRejected() {
        FoodProductReviewCaseEntity reviewCase = new FoodProductReviewCaseEntity();
        reviewCase.setId(31L);
        reviewCase.setStatus(FoodProductReviewCaseStatus.SUBMITTED);
        when(reviewCaseRepository.findById(31L)).thenReturn(Optional.of(reviewCase));

        assertThrows(
                RequestConflictException.class,
                () -> service.transition(
                        31L,
                        FoodProductReviewCaseStatus.APPLIED,
                        "reviewer@grun.local",
                        null
                )
        );
    }

    private FoodProductReviewCaseCommand command(String idempotencyKey) {
        return new FoodProductReviewCaseCommand(
                idempotencyKey,
                FoodProductReviewCaseSource.USER_OCR,
                null,
                null,
                "3017620422003",
                MarketRegion.UK_IE,
                null,
                "Hazelnut spread",
                "Example",
                539.0,
                6.3,
                30.9,
                57.5,
                null,
                null,
                null,
                FoodNutritionBasis.SOURCE_REPORTED,
                FoodProductReviewRiskLevel.MEDIUM,
                1,
                "{\"productName\":\"Hazelnut spread\"}",
                null,
                null,
                "v1",
                true,
                false
        );
    }
}
