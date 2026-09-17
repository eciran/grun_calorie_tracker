package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.config.FoodContributionStorageProperties;
import com.grun.calorietracker.dto.FoodProductOcrExtractionDto;
import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.FoodProductReviewSubmitRequestDto;
import com.grun.calorietracker.entity.FoodProductReviewCaseAssetEntity;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodProductReviewCaseEntity;
import com.grun.calorietracker.entity.FoodProductUploadSessionEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.FoodNutritionBasis;
import com.grun.calorietracker.enums.FoodProductAssetUploadState;
import com.grun.calorietracker.enums.FoodProductAssetDeletionState;
import com.grun.calorietracker.enums.FoodProductReviewCaseStatus;
import com.grun.calorietracker.enums.FoodProductUploadSessionStatus;
import com.grun.calorietracker.enums.FoodProductReviewAssetType;
import com.grun.calorietracker.service.model.ProductNutritionOcrShadowRequestedEvent;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.exception.InvalidCredentialsException;
import com.grun.calorietracker.repository.FoodProductReviewCaseAssetRepository;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodProductReviewCaseExtractionRepository;
import com.grun.calorietracker.repository.FoodProductReviewCaseRepository;
import com.grun.calorietracker.repository.FoodProductUploadSessionRepository;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.impl.FoodProductReviewSubmissionServiceImpl;
import com.grun.calorietracker.service.model.FoodProductReviewCaseCommand;
import com.grun.calorietracker.service.support.ProductIntakeRolloutPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.times;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.LocalDateTime;

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
    private final UserProductLibraryService userProductLibrary = mock(UserProductLibraryService.class);
    private final FoodItemRepository foodItems = mock(FoodItemRepository.class);
    private final ProductIntakeRolloutPolicy rollout = mock(ProductIntakeRolloutPolicy.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private FoodProductReviewSubmissionServiceImpl service;
    private UserEntity user;
    private FoodProductUploadSessionEntity session;

    @BeforeEach
    void setUp() {
        service = new FoodProductReviewSubmissionServiceImpl(users, sessions, assets, reviews, extractions,
                cases, userProductLibrary, foodItems, new ObjectMapper(), rollout,
                new FoodContributionStorageProperties(), events);
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
                .thenReturn(List.of(verifiedAsset(1L, FoodProductReviewAssetType.FRONT_PACKAGE),
                        verifiedAsset(2L, FoodProductReviewAssetType.NUTRITION_LABEL)));
        FoodProductDto customFood = new FoodProductDto();
        customFood.setId(501L);
        customFood.setProductName("Test product");
        FoodItemEntity customEntity = new FoodItemEntity();
        customEntity.setId(501L);
        customEntity.setName("Test product");
        when(userProductLibrary.createCustomFood(any(), any())).thenReturn(customFood);
        when(foodItems.getReferenceById(501L)).thenReturn(customEntity);
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
        assertEquals(501L, review.getUserCustomFood().getId());
        verify(userProductLibrary).createCustomFood(any(), any());
    }

    @Test
    void randomShortBarcodeRejectsReviewBeforePrivateFoodAndReturns400() throws Exception {
        var realCases = new com.grun.calorietracker.service.impl.FoodProductReviewCaseServiceImpl(reviews, foodItems);
        var realSubmission = new FoodProductReviewSubmissionServiceImpl(users, sessions, assets, reviews, extractions,
                realCases, userProductLibrary, foodItems, new ObjectMapper(), rollout,
                new FoodContributionStorageProperties(), events);
        var mapper = new ObjectMapper();
        var payload = mapper.valueToTree(request());
        ((com.fasterxml.jackson.databind.node.ObjectNode) payload).put("barcode", "848484");
        var invalid = mapper.treeToValue(payload, FoodProductReviewSubmitRequestDto.class);

        var failure = assertThrows(IllegalArgumentException.class,
                () -> realSubmission.submit(user.getEmail(), session.getId(), invalid));

        assertEquals("Barcode has an invalid GTIN check digit", failure.getMessage());
        verify(reviews, never()).saveAndFlush(any());
        verify(foodItems, never()).saveAndFlush(any());
        verify(userProductLibrary, never()).createCustomFood(any(), any());
        var handler = new com.grun.calorietracker.exception.GlobalExceptionHandler(
                new org.springframework.context.support.StaticMessageSource(), false);
        var http = new org.springframework.mock.web.MockHttpServletRequest("POST",
                "/api/v1/products/review-cases/upload-sessions/session-1/submit");
        var response = handler.handleIllegalArgumentException(failure, http);
        assertEquals(400, response.getStatusCode().value());
        assertEquals("INVALID_REQUEST", response.getBody().getCode());
    }

    @Test
    void validBarcodePassesRealReviewCreationAndLinksPrivateFood() {
        var realCases = new com.grun.calorietracker.service.impl.FoodProductReviewCaseServiceImpl(reviews, foodItems);
        var realSubmission = new FoodProductReviewSubmissionServiceImpl(users, sessions, assets, reviews, extractions,
                realCases, userProductLibrary, foodItems, new ObjectMapper(), rollout,
                new FoodContributionStorageProperties(), events);
        when(foodItems.saveAndFlush(any(FoodItemEntity.class))).thenAnswer(invocation -> {
            FoodItemEntity food = invocation.getArgument(0);
            food.setId(700L);
            return food;
        });
        when(reviews.saveAndFlush(any(FoodProductReviewCaseEntity.class))).thenAnswer(invocation -> {
            FoodProductReviewCaseEntity review = invocation.getArgument(0);
            review.setId(90L);
            return review;
        });

        var result = realSubmission.submit(user.getEmail(), session.getId(), request());

        assertEquals(90L, result.caseId());
        assertEquals(501L, result.customFoodId());
        verify(reviews).saveAndFlush(any());
        verify(userProductLibrary).createCustomFood(any(), any());
    }

    @Test
    void reusesLinkedCustomFoodWhenTheSubmissionIsRetried() {
        FoodProductReviewCaseEntity review = review(user, session.getId());
        FoodItemEntity existingCustomFood = new FoodItemEntity();
        existingCustomFood.setId(777L);
        existingCustomFood.setName("Existing private product");
        review.setUserCustomFood(existingCustomFood);
        when(cases.finalizeCase(any(FoodProductReviewCaseCommand.class))).thenReturn(review);
        when(extractions.findByReviewCaseId(90L)).thenReturn(Optional.of(
                new com.grun.calorietracker.entity.FoodProductReviewCaseExtractionEntity()));

        var result = service.submit(user.getEmail(), session.getId(), request());

        assertEquals(777L, result.customFoodId());
        assertEquals("Existing private product", result.customFoodName());
        assertEquals(true, result.replayed());
        verify(userProductLibrary, never()).createCustomFood(any(), any());
    }

    @Test
    void persistsStructuredOcrEvidenceWithoutBreakingLegacyExtraction() {
        FoodProductReviewCaseEntity review = review(user, session.getId());
        when(cases.finalizeCase(any(FoodProductReviewCaseCommand.class))).thenReturn(review);
        when(extractions.findByReviewCaseId(90L)).thenReturn(Optional.empty());
        FoodProductReviewSubmitRequestDto legacy = request();
        FoodProductOcrExtractionDto expanded = new FoodProductOcrExtractionDto(
                "ML_KIT", "17", "nutrition-parser-v4", "en-GB",
                List.of("Energy 100 kcal"), Map.of("calories", 100), List.of(),
                List.of(Map.of("text", "100", "confidence", 0.97,
                        "box", Map.of("x", 0.5, "y", 0.2, "width", 0.1, "height", 0.05))),
                Map.of("energy", Map.of("rawText", "100 kcal", "rowId", "row-1")),
                Map.of("blurScore", 0.91, "decision", "ACCEPT"),
                Map.of("energy", Map.of("decision", "AUTO_ACCEPT")),
                List.of(Map.of("field", "sugar", "oldValue", "8", "newValue", "3")),
                Map.of("calories", "190"), Map.of("calories", "200"), Map.of("calories", "200"));
        FoodProductReviewSubmitRequestDto request = new FoodProductReviewSubmitRequestDto(
                legacy.idempotencyKey(), legacy.barcode(), legacy.marketRegion(), legacy.productName(), legacy.brand(),
                legacy.calories(), legacy.protein(), legacy.fat(), legacy.carbs(), legacy.fiber(), legacy.sugar(),
                legacy.sodium(), legacy.nutritionBasis(), legacy.riskLevel(), legacy.submittedFields(),
                legacy.fieldConfidence(), legacy.correctionSummary(), legacy.consentVersion(),
                legacy.temporaryEvidenceAllowed(), legacy.publicMediaAllowed(), true,
                "product-nutrition-ai-v1", expanded);

        service.submit(user.getEmail(), session.getId(), request);

        ArgumentCaptor<FoodProductReviewCaseCommand> command = ArgumentCaptor.forClass(FoodProductReviewCaseCommand.class);
        verify(cases).finalizeCase(command.capture());
        assertEquals(true, command.getValue().aiNutritionLabelProcessingAllowed());
        assertEquals("product-nutrition-ai-v1", command.getValue().aiNutritionLabelConsentVersion());
        ArgumentCaptor<com.grun.calorietracker.entity.FoodProductReviewCaseExtractionEntity> saved =
                ArgumentCaptor.forClass(com.grun.calorietracker.entity.FoodProductReviewCaseExtractionEntity.class);
        verify(extractions).save(saved.capture());
        assertEquals(true, saved.getValue().getWordBoxesJson().contains("confidence"));
        assertEquals(true, saved.getValue().getFieldEvidenceJson().contains("rawText"));
        assertEquals(true, saved.getValue().getQualitySignalsJson().contains("blurScore"));
        assertEquals(true, saved.getValue().getDecisionsJson().contains("AUTO_ACCEPT"));
        assertEquals(true, saved.getValue().getCorrectionAuditJson().contains("oldValue"));
        ArgumentCaptor<ProductNutritionOcrShadowRequestedEvent> shadow =
                ArgumentCaptor.forClass(ProductNutritionOcrShadowRequestedEvent.class);
        verify(events).publishEvent(shadow.capture());
        assertEquals(90L, shadow.getValue().request().fallbackRequest().reviewCaseId());
        assertEquals(2L, shadow.getValue().request().fallbackRequest().nutritionAssetId());
        assertEquals(Map.of("calories", "190"), shadow.getValue().request().v3Fields());
        assertEquals(Map.of("calories", "200"), shadow.getValue().request().v4Fields());
    }

    @Test
    void deserializesLegacyMobileOcrExtractionWithoutNewEvidenceFields() throws Exception {
        String legacyJson = """
                {
                  "engine": "ML_KIT",
                  "engineVersion": "17",
                  "parserVersion": "nutrition-parser-v3",
                  "locale": "en-GB",
                  "recognizedLines": ["Energy 100 kcal"],
                  "parsedValues": {"calories": 100},
                  "parserWarnings": []
                }
                """;

        FoodProductOcrExtractionDto extraction = new ObjectMapper()
                .readValue(legacyJson, FoodProductOcrExtractionDto.class);

        assertEquals("nutrition-parser-v3", extraction.parserVersion());
        assertEquals(null, extraction.wordBoxes());
        assertEquals(null, extraction.fieldEvidence());
        assertEquals(null, extraction.qualitySignals());
        assertEquals(null, extraction.decisions());
        assertEquals(null, extraction.correctionAudit());
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

    @Test
    void manualSubmissionCreatesPrivateFoodAndPendingReviewWithoutOcr() {
        FoodProductReviewCaseEntity review = review(user, session.getId());
        review.setStatus(FoodProductReviewCaseStatus.SUBMITTED);
        when(cases.finalizeCase(any(FoodProductReviewCaseCommand.class))).thenReturn(review);
        FoodProductReviewSubmitRequestDto old = request();
        var manual = new FoodProductReviewSubmitRequestDto(old.idempotencyKey(), old.barcode(), old.marketRegion(),
                old.productName(), old.brand(), old.calories(), old.protein(), old.fat(), old.carbs(),
                old.fiber(), old.sugar(), old.sodium(), old.nutritionBasis(), old.riskLevel(),
                Map.of("entryMethod", "MANUAL", "basis", "PER_100G"), Map.of(), Map.of("entryMethod", "MANUAL"),
                old.consentVersion(), true, false, false, null, null);
        var result = service.submit(user.getEmail(), session.getId(), manual);
        assertEquals(501L, result.customFoodId());
        assertEquals(FoodProductReviewCaseStatus.SUBMITTED, result.status());
        verify(extractions, never()).save(any());
        org.mockito.Mockito.verifyNoInteractions(events);
        var command = ArgumentCaptor.forClass(FoodProductReviewCaseCommand.class);
        verify(cases).finalizeCase(command.capture());
        assertEquals(false, command.getValue().aiNutritionLabelProcessingAllowed());
        assertEquals(false, command.getValue().publicMediaAllowed());
        var privateRequest = ArgumentCaptor.forClass(com.grun.calorietracker.dto.CustomFoodRequestDto.class);
        verify(userProductLibrary).createCustomFood(org.mockito.ArgumentMatchers.eq(user.getEmail()), privateRequest.capture());
        assertEquals(100.0, privateRequest.getValue().getServingSizeGrams());
        assertEquals(old.calories(), privateRequest.getValue().getCalories());
    }

    @Test
    void manualServingPreserves31GramBarAndNormalizedNutrition() {
        FoodProductReviewCaseEntity review = review(user, session.getId());
        review.setStatus(FoodProductReviewCaseStatus.SUBMITTED);
        when(cases.finalizeCase(any(FoodProductReviewCaseCommand.class))).thenReturn(review);
        FoodProductReviewSubmitRequestDto old = request();
        var manual = new FoodProductReviewSubmitRequestDto(old.idempotencyKey(), old.barcode(), old.marketRegion(),
                old.productName(), old.brand(), 500.0, 10.0, old.fat(), old.carbs(),
                old.fiber(), old.sugar(), old.sodium(), old.nutritionBasis(), old.riskLevel(),
                Map.of("entryMethod", "MANUAL", "basis", "PER_100G", "servingSizeGrams", 31.0, "servingUnit", "bar"), Map.of(), Map.of("entryMethod", "MANUAL"),
                old.consentVersion(), true, false, false, null, null);
        var result = service.submit(user.getEmail(), session.getId(), manual);
        assertEquals(501L, result.customFoodId());
        assertEquals(FoodProductReviewCaseStatus.SUBMITTED, result.status());
        verify(extractions, never()).save(any());
        org.mockito.Mockito.verifyNoInteractions(events);
        var command = ArgumentCaptor.forClass(FoodProductReviewCaseCommand.class);
        verify(cases).finalizeCase(command.capture());
        assertEquals(false, command.getValue().aiNutritionLabelProcessingAllowed());
        assertEquals(false, command.getValue().publicMediaAllowed());
        var privateRequest = ArgumentCaptor.forClass(com.grun.calorietracker.dto.CustomFoodRequestDto.class);
        verify(userProductLibrary).createCustomFood(org.mockito.ArgumentMatchers.eq(user.getEmail()), privateRequest.capture());
        assertEquals(31.0, privateRequest.getValue().getServingSizeGrams());
        assertEquals("bar", privateRequest.getValue().getServingUnit());
        assertEquals(500.0, privateRequest.getValue().getCalories());
        assertEquals(155.0, privateRequest.getValue().getCalories() * privateRequest.getValue().getServingSizeGrams() / 100.0);
        assertEquals(500.0, command.getValue().calories());
    }

    @Test
    void rejectsMissingOrDuplicatePhotoTypesBeforeCreatingAnyFood() {
        for (var invalid : List.of(
                List.of(verifiedAsset(1L, FoodProductReviewAssetType.FRONT_PACKAGE)),
                List.of(verifiedAsset(1L, FoodProductReviewAssetType.FRONT_PACKAGE),
                        verifiedAsset(2L, FoodProductReviewAssetType.FRONT_PACKAGE)))) {
            when(assets.findAllByUploadSessionIdOrderByAssetTypeAsc(session.getId())).thenReturn(invalid);
            assertThrows(com.grun.calorietracker.exception.RequestConflictException.class,
                    () -> service.submit(user.getEmail(), session.getId(), request()));
        }
        verify(cases, never()).finalizeCase(any());
        verify(userProductLibrary, never()).createCustomFood(any(), any());
    }


    @Test
    void manualServingRejectsInvalidWeightBeforeCreatingPrivateFood() {
        FoodProductReviewCaseEntity review = review(user, session.getId());
        when(cases.finalizeCase(any(FoodProductReviewCaseCommand.class))).thenReturn(review);
        FoodProductReviewSubmitRequestDto old = request();
        for (Object weight : List.of(0.0, -31.0, Double.NaN, Double.POSITIVE_INFINITY, "31", 100001.0)) {
            var manual = new FoodProductReviewSubmitRequestDto(old.idempotencyKey(), old.barcode(), old.marketRegion(),
                    old.productName(), old.brand(), old.calories(), old.protein(), old.fat(), old.carbs(),
                    old.fiber(), old.sugar(), old.sodium(), old.nutritionBasis(), old.riskLevel(),
                    Map.of("entryMethod", "MANUAL", "servingSizeGrams", weight), Map.of(), Map.of(),
                    old.consentVersion(), true, false, false, null, null);
            assertThrows(IllegalArgumentException.class, () -> service.submit(user.getEmail(), session.getId(), manual));
        }
        verify(userProductLibrary, never()).createCustomFood(any(), any());
    }

    @Test
    void liquidContributionKeepsMicrosAndVolumeAndReplaysWithoutDuplicate() {
        var options = mock(com.grun.calorietracker.repository.FoodItemServingOptionRepository.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "contributionServingOptions", options);
        var review = review(user, session.getId());
        when(cases.finalizeCase(any(FoodProductReviewCaseCommand.class))).thenReturn(review);
        var old = request();
        var fields = Map.<String,Object>of("entryMethod", "MANUAL", "nutritionReferenceUnit", "PER_100ML",
                "servingAmount", 250.0, "servingUnit", "glass", "calcium", 120.0, "vitaminB12", 0.4, "transFat", 0.0);
        var request = new FoodProductReviewSubmitRequestDto(old.idempotencyKey(), old.barcode(), old.marketRegion(),
                old.productName(), old.brand(), 60.0, 3.0, 3.0, 5.0, null, null, 40.0,
                old.nutritionBasis(), old.riskLevel(), fields, Map.of(), Map.of(), old.consentVersion(), true, false, false, null, null);
        service.submit(user.getEmail(), session.getId(), request);
        var saved = ArgumentCaptor.forClass(com.grun.calorietracker.dto.CustomFoodRequestDto.class);
        verify(userProductLibrary).createCustomFood(any(), saved.capture());
        assertEquals(com.grun.calorietracker.enums.FoodNutritionReferenceUnit.PER_100ML, saved.getValue().getNutritionReferenceUnit());
        assertEquals(120.0, saved.getValue().getCalcium());
        assertEquals(0.4, saved.getValue().getVitaminB12());
        assertEquals(0.0, saved.getValue().getTransFat());
        assertNull(saved.getValue().getIron());
        assertNull(saved.getValue().getServingSizeGrams());
        var portion = ArgumentCaptor.forClass(com.grun.calorietracker.entity.FoodItemServingOptionEntity.class);
        verify(options).save(portion.capture());
        assertEquals(250.0, portion.getValue().getMlVolume());
        assertNull(portion.getValue().getGramWeight());
        service.submit(user.getEmail(), session.getId(), request);
        verify(options, times(1)).save(any());
        verify(userProductLibrary, times(1)).createCustomFood(any(), any());
    }

    private FoodProductReviewSubmitRequestDto request() {
        return new FoodProductReviewSubmitRequestDto("idem-1", "5012345678900", MarketRegion.UK_IE,
                "Test product", "Brand", 100.0, 5.0, 2.0, 10.0, 1.0, 3.0, 0.2,
                FoodNutritionBasis.SOURCE_REPORTED, null, Map.of("calories", 100), Map.of("calories", 0.95),
                Map.of("calories", "confirmed"), "consent-v1", true, false,
                true, "product-nutrition-ai-v1",
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

    private FoodProductReviewCaseAssetEntity verifiedAsset(Long id, FoodProductReviewAssetType type) {
        FoodProductReviewCaseAssetEntity asset = new FoodProductReviewCaseAssetEntity();
        asset.setId(id);
        asset.setAssetType(type);
        asset.setUploadState(FoodProductAssetUploadState.VERIFIED);
        asset.setDeletionState(FoodProductAssetDeletionState.ACTIVE);
        asset.setExpiresAt(LocalDateTime.now().plusDays(1));
        return asset;
    }
}
