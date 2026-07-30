package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodProductReviewCaseEntity;
import com.grun.calorietracker.entity.FoodProductSourceEvidenceEntity;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.repository.FoodProductSourceEvidenceRepository;
import com.grun.calorietracker.service.impl.FoodProductReviewCaseEvidenceServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FoodProductReviewCaseEvidenceServiceImplTest {
    private final FoodProductSourceEvidenceRepository repository = mock(FoodProductSourceEvidenceRepository.class);
    private final FoodProductReviewCaseEvidenceServiceImpl service = new FoodProductReviewCaseEvidenceServiceImpl(repository, new ObjectMapper());

    @Test
    void acceptedUserCaseCreatesImmutableReviewedLabelEvidenceWithoutMutatingProduct() {
        FoodItemEntity product = new FoodItemEntity();
        product.setId(44L);
        product.setCalories(10.0);
        product.setPublicationStatus(CatalogPublicationStatus.INTERNAL_REVIEW);
        FoodProductReviewCaseEntity reviewCase = approvedCase(product);
        when(repository.findExistingFingerprints(any())).thenReturn(List.of());
        when(repository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        int inserted = service.recordAcceptedEvidence(reviewCase);

        assertEquals(3, inserted);
        @SuppressWarnings("unchecked") ArgumentCaptor<List<FoodProductSourceEvidenceEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        assertTrue(captor.getValue().stream().allMatch(value -> value.getProvider() == FoodDataSource.USER_SUBMITTED_LABEL));
        assertTrue(captor.getValue().stream().allMatch(value -> "REVIEW_CASE:91".equals(value.getExternalId())));
        assertTrue(captor.getValue().stream().allMatch(value -> "reviewer@grun.app".equals(value.getReviewerIdentity())));
        assertTrue(captor.getValue().stream().allMatch(value -> value.getBasis() == FoodEvidenceBasis.LABEL && value.getConfidenceScore() == 100));
        assertEquals(10.0, product.getCalories());
        assertEquals(CatalogPublicationStatus.INTERNAL_REVIEW, product.getPublicationStatus());
    }

    @Test
    void deterministicFingerprintsMakeRepeatedApprovalIdempotent() {
        FoodProductReviewCaseEntity reviewCase = approvedCase(product(45L));
        when(repository.findExistingFingerprints(any())).thenAnswer(invocation -> invocation.getArgument(0));

        int inserted = service.recordAcceptedEvidence(reviewCase);

        assertEquals(0, inserted);
        verify(repository).saveAll(List.of());
    }

    @Test
    void unapprovedCaseCannotCreateEvidence() {
        FoodProductReviewCaseEntity reviewCase = approvedCase(product(46L));
        reviewCase.setStatus(FoodProductReviewCaseStatus.SUBMITTED);
        assertThrows(IllegalStateException.class, () -> service.recordAcceptedEvidence(reviewCase));
        verifyNoInteractions(repository);
    }

    private FoodProductReviewCaseEntity approvedCase(FoodItemEntity product) {
        FoodProductReviewCaseEntity value = new FoodProductReviewCaseEntity();
        value.setId(91L);
        value.setFoodItem(product);
        value.setSource(FoodProductReviewCaseSource.USER_OCR);
        value.setStatus(FoodProductReviewCaseStatus.APPROVED);
        value.setReviewedBy("reviewer@grun.app");
        value.setReviewedAt(LocalDateTime.of(2026, 7, 30, 12, 0));
        value.setSchemaVersion(1);
        value.setSubmittedValuesJson("{\"calories\":\"539\",\"protein\":6.3,\"sodium\":\"42.5\",\"fat\":\"bad\"}");
        return value;
    }

    private FoodItemEntity product(long id) {
        FoodItemEntity product = new FoodItemEntity();
        product.setId(id);
        product.setPublicationStatus(CatalogPublicationStatus.INTERNAL_REVIEW);
        return product;
    }
}