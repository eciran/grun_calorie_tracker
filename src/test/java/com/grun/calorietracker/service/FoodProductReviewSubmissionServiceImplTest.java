package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.config.FoodContributionStorageProperties;
import com.grun.calorietracker.dto.FoodProductOcrExtractionDto;
import com.grun.calorietracker.dto.FoodProductReviewSubmitRequestDto;
import com.grun.calorietracker.entity.FoodProductReviewCaseAssetEntity;
import com.grun.calorietracker.entity.FoodProductReviewCaseEntity;
import com.grun.calorietracker.entity.FoodProductUploadSessionEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.FoodNutritionBasis;
import com.grun.calorietracker.enums.FoodProductAssetUploadState;
import com.grun.calorietracker.enums.FoodProductReviewCaseStatus;
import com.grun.calorietracker.enums.FoodProductUploadSessionStatus;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.repository.FoodProductReviewCaseAssetRepository;
import com.grun.calorietracker.repository.FoodProductReviewCaseExtractionRepository;
import com.grun.calorietracker.repository.FoodProductReviewCaseRepository;
import com.grun.calorietracker.repository.FoodProductUploadSessionRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.FoodProductReviewSubmissionServiceImpl;
import com.grun.calorietracker.service.model.FoodProductReviewCaseCommand;
import com.grun.calorietracker.service.support.ProductIntakeRolloutPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FoodProductReviewSubmissionServiceImplTest {
    private final UserRepository users = mock(UserRepository.class);
    private final FoodProductUploadSessionRepository sessions = mock(FoodProductUploadSessionRepository.class);
    private final FoodProductReviewCaseAssetRepository assets = mock(FoodProductReviewCaseAssetRepository.class);
    private final FoodProductReviewCaseRepository reviews = mock(FoodProductReviewCaseRepository.class);
    private final FoodProductReviewCaseExtractionRepository extractions = mock(FoodProductReviewCaseExtractionRepository.class);
    private final FoodProductReviewCaseService cases = mock(FoodProductReviewCaseService.class);
    private final ProductIntakeRolloutPolicy rollout = mock(ProductIntakeRolloutPolicy.class);
    private FoodProductReviewSubmissionServiceImpl service;
    private UserEntity user;
    private FoodProductUploadSessionEntity session;

    @BeforeEach
    void setUp() {
        service = new FoodProductReviewSubmissionServiceImpl(users, sessions, assets, reviews, extractions,
                cases, new ObjectMapper(), rollout, new FoodContributionStorageProperties());
        user = new UserEntity();
        user.setId(12L);
        user.setEmail("user@grun.test");
        session = new FoodProductUploadSessionEntity();
        session.setId("session-1");
        session.setCreatedBy(user);
        session.setStatus(FoodProductUploadSessionStatus.FINALIZED);
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(sessions.findById(session.getId())).thenReturn(Optional.of(session));
        when(assets.findAllByUploadSessionIdOrderByAssetTypeAsc(session.getId()))
                .thenReturn(List.of(verifiedAsset(), verifiedAsset()));
    }

    @Test
    void persistsVersionedOcrExtractionForOwnedIdempotentSubmission() {
        FoodProductReviewCaseEntity review = review(user, session.getId());
        when(cases.finalizeCase(any(FoodProductReviewCaseCommand.class))).thenReturn(review);
        when(extractions.findByReviewCaseId(90L)).thenReturn(Optional.empty());

        service.submit(user.getEmail(), session.getId(), request());

        ArgumentCaptor<com.grun.calorietracker.entity.FoodProductReviewCaseExtractionEntity> saved =
                ArgumentCaptor.forClass(com.grun.calorietracker.entity.FoodProductReviewCaseExtractionEntity.class);
        verify(extractions).save(saved.capture());
        assertEquals("ML_KIT", saved.getValue().getEngine());
        assertEquals("nutrition-parser-v1", saved.getValue().getParserVersion());
        assertEquals(90L, saved.getValue().getReviewCase().getId());
    }

    @Test
    void rejectsIdempotencyCollisionOwnedByAnotherUser() {
        UserEntity other = new UserEntity();
        other.setId(99L);
        FoodProductReviewCaseEntity review = review(other, session.getId());
        when(cases.finalizeCase(any(FoodProductReviewCaseCommand.class))).thenReturn(review);

        assertThrows(InvalidCredentialsException.class,
                () -> service.submit(user.getEmail(), session.getId(), request()));

        verify(extractions, never()).save(any());
    }

    @Test
    void withdrawsOnlyOwnedOpenCase() {
        FoodProductReviewCaseEntity review = review(user, session.getId());
        review.setStatus(FoodProductReviewCaseStatus.SUBMITTED);
        when(reviews.findById(90L)).thenReturn(Optional.of(review));
        FoodProductReviewCaseEntity withdrawn = review(user, session.getId());
        withdrawn.setStatus(FoodProductReviewCaseStatus.WITHDRAWN);
        when(cases.transition(90L, FoodProductReviewCaseStatus.WITHDRAWN,
                user.getEmail(), "Withdrawn by submitter")).thenReturn(withdrawn);

        var response = service.withdraw(user.getEmail(), 90L);

        assertEquals(FoodProductReviewCaseStatus.WITHDRAWN, response.status());
    }

    private FoodProductReviewSubmitRequestDto request() {
        return new FoodProductReviewSubmitRequestDto("idem-1", "5012345678900", MarketRegion.UK_IE,
                "Test product", "Brand", 100.0, 5.0, 2.0, 10.0, 1.0, 3.0, 0.2,
                FoodNutritionBasis.SOURCE_REPORTED, null, Map.of("calories", 100), Map.of("calories", 0.95),
                Map.of("calories", "confirmed"), "consent-v1", true, false,
                new FoodProductOcrExtractionDto("ML_KIT", "17", "nutrition-parser-v1", "en-GB",
                        List.of("Energy 100 kcal"), Map.of("calories", 100), List.of()));
    }

    private FoodProductReviewCaseEntity review(UserEntity owner, String sourceReference) {
        FoodProductReviewCaseEntity review = new FoodProductReviewCaseEntity();
        review.setId(90L);
        review.setSubmittedBy(owner);
        review.setSourceReference(sourceReference);
        review.setStatus(FoodProductReviewCaseStatus.SUBMITTED);
        return review;
    }

    private FoodProductReviewCaseAssetEntity verifiedAsset() {
        FoodProductReviewCaseAssetEntity asset = new FoodProductReviewCaseAssetEntity();
        asset.setUploadState(FoodProductAssetUploadState.VERIFIED);
        return asset;
    }
}
