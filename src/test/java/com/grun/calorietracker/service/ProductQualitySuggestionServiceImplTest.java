package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodItemSearchAliasEntity;
import com.grun.calorietracker.entity.FoodProductReviewAuditEntity;
import com.grun.calorietracker.entity.ProductQualityScanRunEntity;
import com.grun.calorietracker.entity.ProductQualitySuggestionEntity;
import com.grun.calorietracker.entity.ProductQualityAiSettingsEntity;
import com.grun.calorietracker.dto.ProductQualityAiSettingsUpdateRequestDto;
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodProductReviewAuditAction;
import com.grun.calorietracker.enums.FoodSearchAliasType;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.ProductQualitySuggestionSource;
import com.grun.calorietracker.enums.ProductQualitySuggestionStatus;
import com.grun.calorietracker.enums.ProductQualitySuggestionType;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodItemSearchAliasRepository;
import com.grun.calorietracker.repository.FoodProductReviewAuditRepository;
import com.grun.calorietracker.repository.ProductQualityScanRunRepository;
import com.grun.calorietracker.repository.ProductQualityAiSettingsRepository;
import com.grun.calorietracker.repository.ProductQualityScanRunItemRepository;
import com.grun.calorietracker.repository.ProductQualitySuggestionRepository;
import com.grun.calorietracker.service.impl.ProductQualitySuggestionServiceImpl;
import com.grun.calorietracker.service.AdminAuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductQualitySuggestionServiceImplTest {

    @Mock
    private FoodItemRepository foodItemRepository;

    @Mock
    private FoodItemSearchAliasRepository foodItemSearchAliasRepository;

    @Mock
    private com.grun.calorietracker.repository.FoodItemLocalizationRepository foodItemLocalizationRepository;

    @Mock
    private com.grun.calorietracker.repository.FoodItemServingOptionRepository foodItemServingOptionRepository;

    @Mock
    private com.grun.calorietracker.repository.FoodItemServingOptionLocalizationRepository foodItemServingOptionLocalizationRepository;

    @Mock
    private com.grun.calorietracker.repository.FoodProductQualityIssueRepository foodProductQualityIssueRepository;

    @Mock
    private com.grun.calorietracker.repository.FoodCanonicalResolutionRepository foodCanonicalResolutionRepository;

    @Mock
    private com.grun.calorietracker.service.FoodProductEvidenceService foodProductEvidenceService;

    @Mock
    private com.grun.calorietracker.service.support.ProductQualitySuggestionReconciliationService suggestionReconciliationService;

    @Mock
    private FoodProductReviewAuditRepository foodProductReviewAuditRepository;

    @Mock
    private ProductQualitySuggestionRepository productQualitySuggestionRepository;

    @Mock
    private ProductQualityScanRunRepository productQualityScanRunRepository;

    @Mock
    private ProductQualityAiSettingsRepository productQualityAiSettingsRepository;

    @Mock
    private ProductQualityScanRunItemRepository productQualityScanRunItemRepository;

    @Mock
    private AdminAuditService adminAuditService;

    @InjectMocks
    private ProductQualitySuggestionServiceImpl service;

    @Test
    void updateAiSettings_recordsAdminAudit() {
        ProductQualityAiSettingsEntity settings = new ProductQualityAiSettingsEntity();
        settings.setDailyProductLimit(250);
        settings.setMonthlyProductLimit(2000);
        when(productQualityAiSettingsRepository.findById(ProductQualityAiSettingsEntity.SINGLETON_ID)).thenReturn(Optional.of(settings));
        when(productQualityAiSettingsRepository.save(any(ProductQualityAiSettingsEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(productQualityScanRunRepository.sumScannedProductsSince(any(), any(), any())).thenReturn(0L);

        ProductQualityAiSettingsUpdateRequestDto request = new ProductQualityAiSettingsUpdateRequestDto();
        request.setEnabled(false);
        request.setMaxProductsPerRun(12);
        request.setDailyProductLimit(120);
        request.setMonthlyProductLimit(1500);
        request.setForceRescanAllowed(false);
        request.setAdminNote("temporary limit reduction");

        var result = service.updateAiSettings(request, "admin@test.com");

        assertEquals(false, result.isEnabled());
        assertEquals(12, result.getMaxProductsPerRun());
        assertEquals(120, result.getDailyProductLimit());
        assertEquals(1500, result.getMonthlyProductLimit());
        verify(productQualityAiSettingsRepository).save(settings);
        verify(adminAuditService).record(eq("admin@test.com"), any(), any(), eq("1"), any(), any(), eq(null));
    }
    @Test
    void scanSuggestions_createsNameCleanupAndSafeAliasSuggestionsWithoutUpdatingProduct() {
        FoodItemEntity plainMilk = product(1L, "milk", 48.0);
        FoodItemEntity milkChocolate = product(2L, "Milk Chocolate", 535.0);

        when(foodItemRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(plainMilk, milkChocolate)));
        when(productQualitySuggestionRepository.existsOpenDedupe(
                any(), any(), any(), any(), eq(ProductQualitySuggestionStatus.OPEN)
        )).thenReturn(false);
        when(productQualityScanRunRepository.save(any(ProductQualityScanRunEntity.class)))
                .thenAnswer(invocation -> {
                    ProductQualityScanRunEntity run = invocation.getArgument(0);
                    if (run.getId() == null) {
                        run.setId(100L);
                    }
                    return run;
                });

        var result = service.scanSuggestions(MarketRegion.UK_IE, 50);
        assertEquals(2, result.getScannedProducts());
        assertEquals(2, result.getCreatedSuggestions());
        assertEquals("milk", plainMilk.getName());
        assertEquals("Milk Chocolate", milkChocolate.getName());

        ArgumentCaptor<List<ProductQualitySuggestionEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(productQualitySuggestionRepository).saveAll(captor.capture());
        List<ProductQualitySuggestionEntity> saved = captor.getValue();

        assertTrue(saved.stream().anyMatch(suggestion ->
                suggestion.getFoodItem().getId().equals(1L)
                        && suggestion.getSuggestionType() == ProductQualitySuggestionType.NAME_CLEANUP
                        && "Milk".equals(suggestion.getSuggestedValue())
        ));
        assertTrue(saved.stream().anyMatch(suggestion ->
                suggestion.getFoodItem().getId().equals(1L)
                        && suggestion.getSuggestionType() == ProductQualitySuggestionType.SEARCH_ALIAS
                        && "sut".equals(suggestion.getSuggestedValue())
        ));
        assertTrue(saved.stream().noneMatch(suggestion -> suggestion.getFoodItem().getId().equals(2L)));
    }

    @Test
    void acceptSuggestion_whenNameCleanup_updatesProductAndClosesSuggestion() {
        FoodItemEntity product = product(1L, "milk", 48.0);
        ProductQualitySuggestionEntity suggestion = suggestion(10L, product, ProductQualitySuggestionType.NAME_CLEANUP, "milk", "Milk");
        when(productQualitySuggestionRepository.findForUpdateById(10L)).thenReturn(Optional.of(suggestion));
        when(productQualitySuggestionRepository.save(any(ProductQualitySuggestionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.acceptSuggestion(10L, "admin@test.com");

        assertEquals("Milk", product.getName());
        assertEquals(ProductQualitySuggestionStatus.ACCEPTED, result.getStatus());
        assertEquals("admin@test.com", result.getReviewedBy());
        verify(foodItemRepository).save(product);

        ArgumentCaptor<FoodProductReviewAuditEntity> auditCaptor = ArgumentCaptor.forClass(FoodProductReviewAuditEntity.class);
        verify(foodProductReviewAuditRepository).save(auditCaptor.capture());
        assertEquals(FoodProductReviewAuditAction.REVIEW_UPDATE, auditCaptor.getValue().getActionType());
        assertEquals("name", auditCaptor.getValue().getFieldName());
    }

    @Test
    void acceptSuggestion_whenSearchAlias_createsAliasAndClosesSuggestion() {
        FoodItemEntity product = product(1L, "Semi Skimmed Milk", 48.0);
        ProductQualitySuggestionEntity suggestion = suggestion(11L, product, ProductQualitySuggestionType.SEARCH_ALIAS, null, "sut");
        when(productQualitySuggestionRepository.findForUpdateById(11L)).thenReturn(Optional.of(suggestion));
        when(foodItemSearchAliasRepository.findByFoodItemIdAndNormalizedAliasAndLanguage(1L, "sut", PreferredLanguage.TR))
                .thenReturn(Optional.empty());
        when(foodItemSearchAliasRepository.save(any(FoodItemSearchAliasEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(productQualitySuggestionRepository.save(any(ProductQualitySuggestionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.acceptSuggestion(11L, "admin@test.com");

        assertEquals(ProductQualitySuggestionStatus.ACCEPTED, result.getStatus());
        ArgumentCaptor<FoodItemSearchAliasEntity> aliasCaptor = ArgumentCaptor.forClass(FoodItemSearchAliasEntity.class);
        verify(foodItemSearchAliasRepository).save(aliasCaptor.capture());
        FoodItemSearchAliasEntity alias = aliasCaptor.getValue();
        assertEquals("sut", alias.getAlias());
        assertEquals("sut", alias.getNormalizedAlias());
        assertEquals(PreferredLanguage.TR, alias.getLanguage());
        assertEquals(FoodSearchAliasType.TRANSLATION, alias.getAliasType());
        assertTrue(alias.getActive());
    }
    @Test
    void acceptSuggestion_whenReviewOnlyType_keepsSuggestionOpen() {
        FoodItemEntity product = product(1L, "Semi Skimmed Milk", 48.0);
        ProductQualitySuggestionEntity suggestion = suggestion(13L, product, ProductQualitySuggestionType.REGION_MISMATCH, "EU", "UK_IE");
        when(productQualitySuggestionRepository.findForUpdateById(13L)).thenReturn(Optional.of(suggestion));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                service.acceptSuggestion(13L, "admin@test.com"));

        assertEquals(MarketRegion.UK_IE, product.getMarketRegion());
        assertEquals(ProductQualitySuggestionStatus.OPEN, suggestion.getStatus());
        verify(foodItemRepository, never()).save(product);
        verify(productQualitySuggestionRepository, never()).save(suggestion);
    }
    @Test
    void acceptSuggestion_whenSupportedFieldSuggestion_updatesProductAndAuditsChange() {
        FoodItemEntity product = product(1L, "Protein Yogurt", 90.0);
        product.setProtein(8.0);
        ProductQualitySuggestionEntity suggestion = suggestion(14L, product, ProductQualitySuggestionType.MISSING_MACRO_DATA, "8.0", "12.5");
        suggestion.setFieldName("protein");
        suggestion.setSource(ProductQualitySuggestionSource.AI_ASSISTED);
        when(productQualitySuggestionRepository.findForUpdateById(14L)).thenReturn(Optional.of(suggestion));
        when(productQualitySuggestionRepository.save(any(ProductQualitySuggestionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.acceptSuggestion(14L, "admin@test.com");

        assertEquals(12.5, product.getProtein());
        assertEquals(ProductQualitySuggestionStatus.ACCEPTED, result.getStatus());
        verify(foodItemRepository).save(product);

        ArgumentCaptor<FoodProductReviewAuditEntity> auditCaptor = ArgumentCaptor.forClass(FoodProductReviewAuditEntity.class);
        verify(foodProductReviewAuditRepository).save(auditCaptor.capture());
        assertEquals(FoodProductReviewAuditAction.REVIEW_UPDATE, auditCaptor.getValue().getActionType());
        assertEquals("protein", auditCaptor.getValue().getFieldName());
        assertEquals("8.0", auditCaptor.getValue().getOldValue());
        assertEquals("12.5", auditCaptor.getValue().getNewValue());
    }


    @Test
    void rejectSuggestion_closesSuggestionWithoutApplyingProductChanges() {
        FoodItemEntity product = product(1L, "milk", 48.0);
        ProductQualitySuggestionEntity suggestion = suggestion(12L, product, ProductQualitySuggestionType.NAME_CLEANUP, "milk", "Milk");
        when(productQualitySuggestionRepository.findForUpdateById(12L)).thenReturn(Optional.of(suggestion));
        when(productQualitySuggestionRepository.save(any(ProductQualitySuggestionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.rejectSuggestion(12L, "admin@test.com");

        assertEquals("milk", product.getName());
        assertEquals(ProductQualitySuggestionStatus.REJECTED, result.getStatus());
        assertEquals("admin@test.com", result.getReviewedBy());
    }

    @Test
    void aiValidationContext_includesLocalizationAliasesServingAndQualityMetadata() {
        FoodItemEntity product = product(42L, "Rice, white, cooked", 130.0);
        product.setDisplayName("Cooked White Rice");
        product.setShortDisplayName("White Rice");
        product.setCanonicalFoodKey("rice-white-cooked");
        product.setQualityScore(88);
        product.setConfidenceScore(91);
        product.setUsageCount(120L);

        com.grun.calorietracker.entity.FoodItemLocalizationEntity localization =
                new com.grun.calorietracker.entity.FoodItemLocalizationEntity();
        localization.setId(1L);
        localization.setLanguage(PreferredLanguage.TR);
        localization.setDisplayName("Pismis Beyaz Pirinc");
        localization.setShortDisplayName("Beyaz Pirinc");
        localization.setActive(true);

        FoodItemSearchAliasEntity alias = new FoodItemSearchAliasEntity();
        alias.setId(2L);
        alias.setAlias("pirinc");
        alias.setNormalizedAlias("pirinc");
        alias.setLanguage(PreferredLanguage.TR);
        alias.setAliasType(FoodSearchAliasType.TRANSLATION);
        alias.setActive(true);

        com.grun.calorietracker.entity.FoodItemServingOptionEntity serving =
                new com.grun.calorietracker.entity.FoodItemServingOptionEntity();
        serving.setId(3L);
        serving.setLabel("cup");
        serving.setUnitType(com.grun.calorietracker.enums.FoodServingOptionUnit.CUP);
        serving.setQuantity(1.0);
        serving.setGramWeight(158.0);
        serving.setIsDefault(true);
        serving.setSource(com.grun.calorietracker.enums.FoodServingOptionSource.ADMIN);
        serving.setQualityStatus(com.grun.calorietracker.enums.FoodServingOptionQualityStatus.VERIFIED);

        com.grun.calorietracker.entity.FoodItemServingOptionLocalizationEntity servingLocalization =
                new com.grun.calorietracker.entity.FoodItemServingOptionLocalizationEntity();
        servingLocalization.setId(4L);
        servingLocalization.setServingOption(serving);
        servingLocalization.setLanguage(PreferredLanguage.TR);
        servingLocalization.setLabel("kase");
        servingLocalization.setActive(true);

        com.grun.calorietracker.entity.FoodProductQualityIssueEntity qualityIssue =
                new com.grun.calorietracker.entity.FoodProductQualityIssueEntity();
        qualityIssue.setId(5L);
        qualityIssue.setIssueType(com.grun.calorietracker.enums.FoodProductQualityIssue.MISSING_MICRONUTRIENTS);
        qualityIssue.setReason("Micronutrient coverage is incomplete.");

        com.grun.calorietracker.dto.FoodProductEvidenceDto sourceEvidence =
                new com.grun.calorietracker.dto.FoodProductEvidenceDto();
        sourceEvidence.setEvidenceId(6L);
        sourceEvidence.setProductId(42L);
        sourceEvidence.setProvider(com.grun.calorietracker.enums.FoodDataSource.USDA_FOODDATA);
        sourceEvidence.setFieldName(com.grun.calorietracker.enums.FoodEvidenceField.CALORIES);
        sourceEvidence.setNumericValue(130.0);
        sourceEvidence.setBasis(com.grun.calorietracker.enums.FoodEvidenceBasis.PER_100_G);
        sourceEvidence.setReviewerIdentity("admin@grun.local");
        com.grun.calorietracker.dto.FoodProductEvidenceComparisonDto evidenceComparison =
                new com.grun.calorietracker.dto.FoodProductEvidenceComparisonDto(
                        com.grun.calorietracker.enums.FoodEvidenceField.CALORIES,
                        com.grun.calorietracker.enums.FoodEvidenceBasis.PER_100_G,
                        com.grun.calorietracker.enums.FoodEvidenceComparisonState.SINGLE_SOURCE,
                        6L,
                        0.0,
                        "Only one current provider supports this field.",
                        List.of(6L)
                );
        com.grun.calorietracker.dto.FoodProductEvidenceContextDto evidenceContext =
                new com.grun.calorietracker.dto.FoodProductEvidenceContextDto(
                        List.of(sourceEvidence),
                        List.of(evidenceComparison)
                );

        when(foodItemLocalizationRepository.findByFoodItemIdInAndLanguageIn(any(), any()))
                .thenReturn(List.of(localization));
        when(foodItemSearchAliasRepository.findByFoodItemIdOrderByActiveDescLanguageAscAliasAsc(42L))
                .thenReturn(List.of(alias));
        when(foodItemServingOptionRepository.findByFoodItemIdInOrderByFoodItemIdAscIsDefaultDescLabelAsc(any()))
                .thenReturn(List.of(serving));
        when(foodItemServingOptionLocalizationRepository.findByServingOptionIdIn(any()))
                .thenReturn(List.of(servingLocalization));
        when(foodProductQualityIssueRepository.findByFoodItemIdAndResolvedFalse(42L))
                .thenReturn(List.of(qualityIssue));
        when(foodItemRepository.findByCanonicalFoodKeyIn(any(), any()))
                .thenReturn(List.of(product));
        when(foodCanonicalResolutionRepository.findById("rice-white-cooked"))
                .thenReturn(Optional.empty());
        when(foodProductEvidenceService.buildContext(product))
                .thenReturn(evidenceContext);

        com.grun.calorietracker.dto.AiProductQualityValidationRequestDto context =
                org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "toAiValidationRequest", product);

        assertEquals("product_quality_context_v2", context.getSchemaVersion());
        assertEquals("product_quality_prompt_v2", context.getPromptVersion());
        assertEquals("Cooked White Rice", context.getDisplayName());
        assertEquals("Pismis Beyaz Pirinc", context.getLocalizations().get(0).getDisplayName());
        assertEquals("pirinc", context.getSearchAliases().get(0).getAlias());
        assertEquals(158.0, context.getServingOptions().get(0).getGramWeight());
        assertEquals("kase", context.getServingOptions().get(0).getLocalizations().get(0).getLabel());
        assertEquals("MISSING_MICRONUTRIENTS", context.getActiveQualityIssues().get(0).getIssueType());
        assertEquals(1, context.getCanonicalDuplicate().getCandidates().size());
        assertEquals(1, context.getSourceEvidence().size());
        assertEquals("admin@grun.local", context.getSourceEvidence().get(0).getReviewerIdentity());
        assertEquals("SINGLE_SOURCE", context.getEvidenceComparisons().get(0).getState().name());
    }

    @Test
    void aiSuggestion_rejectsUnsupportedFieldAndResponseSchema() {
        FoodItemEntity product = product(1L, "Milk", 48.0);
        com.grun.calorietracker.dto.AiProductQualityValidationResponseDto.AiProductQualityIssueDto issue =
                new com.grun.calorietracker.dto.AiProductQualityValidationResponseDto.AiProductQualityIssueDto();
        issue.setSuggestionType(ProductQualitySuggestionType.DISPLAY_NAME);
        issue.setFieldName("calories");

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "buildAiSuggestion", product, issue));

        com.grun.calorietracker.dto.AiProductQualityValidationResponseDto response =
                new com.grun.calorietracker.dto.AiProductQualityValidationResponseDto();
        response.setSchemaVersion("legacy_response");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "validateAiResponse", response));
    }
    @Test
    void aiNutritionSuggestion_removesExactValueWithoutMatchingEvidence() {
        FoodItemEntity product = product(1L, "Milk", 48.0);
        com.grun.calorietracker.dto.AiProductQualityValidationResponseDto.AiProductQualityIssueDto issue =
                new com.grun.calorietracker.dto.AiProductQualityValidationResponseDto.AiProductQualityIssueDto();
        issue.setSuggestionType(ProductQualitySuggestionType.SUSPICIOUS_CALORIE_VALUE);
        issue.setFieldName("calories");
        issue.setSuggestedValue("52.0");
        issue.setReason("Provider response proposed a correction.");

        com.grun.calorietracker.dto.FoodProductEvidenceComparisonDto conflict =
                new com.grun.calorietracker.dto.FoodProductEvidenceComparisonDto(
                        com.grun.calorietracker.enums.FoodEvidenceField.CALORIES,
                        com.grun.calorietracker.enums.FoodEvidenceBasis.PER_100_G,
                        com.grun.calorietracker.enums.FoodEvidenceComparisonState.CONFLICT,
                        10L,
                        20.0,
                        "conflict",
                        List.of(10L, 11L)
                );

        ProductQualitySuggestionEntity suggestion = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                service,
                "buildAiSuggestion",
                product,
                issue,
                List.of(conflict)
        );

        assertEquals(null, suggestion.getSuggestedValue());
        assertTrue(suggestion.getReason().contains("Exact suggested value removed"));
    }
    @Test
    void getProductWorkbench_returnsCompleteProductContext() {
        FoodItemEntity product = product(1L, "Milk", 48.0);
        ProductQualitySuggestionEntity suggestion = suggestion(
                10L, product, ProductQualitySuggestionType.SUSPICIOUS_CALORIE_VALUE, "48", "50"
        );
        suggestion.setFieldName("calories");

        when(foodItemRepository.findById(1L)).thenReturn(Optional.of(product));
        when(foodItemLocalizationRepository.findByFoodItemIdOrderByLanguageAsc(1L)).thenReturn(List.of());
        when(foodItemServingOptionRepository.findByFoodItemIdInOrderByFoodItemIdAscIsDefaultDescLabelAsc(List.of(1L)))
                .thenReturn(List.of());
        when(foodItemSearchAliasRepository.findByFoodItemIdOrderByActiveDescLanguageAscAliasAsc(1L)).thenReturn(List.of());
        when(foodProductQualityIssueRepository.findByFoodItemIdOrderByResolvedAscLastDetectedAtDesc(1L)).thenReturn(List.of());
        when(productQualitySuggestionRepository.findByFoodItemIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(suggestion));
        when(foodProductEvidenceService.buildContext(product)).thenReturn(
                new com.grun.calorietracker.dto.FoodProductEvidenceContextDto(List.of(), List.of())
        );
        when(foodProductReviewAuditRepository.findByFoodItemId(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        var result = service.getProductWorkbench(1L);

        assertEquals(1L, result.getProduct().getId());
        assertEquals("Milk", result.getProduct().getProductName());
        assertEquals(1, result.getSuggestions().size());
        assertEquals("calories", result.getSuggestions().get(0).getFieldName());
        assertTrue(result.getAliases().isEmpty());
        assertTrue(result.getServingOptions().isEmpty());
        assertTrue(result.getAudit().isEmpty());
    }

    @Test
    void acceptSuggestion_whenLocalization_appliesLocalizedNameBeforeClosing() {
        FoodItemEntity product = product(1L, "Milk", 48.0);
        ProductQualitySuggestionEntity suggestion = suggestion(
                11L, product, ProductQualitySuggestionType.LOCALIZATION, null, "Sut"
        );
        suggestion.setFieldName("localizations.TR.displayName");
        when(productQualitySuggestionRepository.findForUpdateById(11L)).thenReturn(Optional.of(suggestion));
        when(productQualitySuggestionRepository.save(any(ProductQualitySuggestionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(foodItemLocalizationRepository.findByFoodItemIdAndLanguage(1L, PreferredLanguage.TR))
                .thenReturn(Optional.empty());

        var result = service.acceptSuggestion(11L, "admin@test.com");

        assertEquals(ProductQualitySuggestionStatus.ACCEPTED, result.getStatus());
        ArgumentCaptor<com.grun.calorietracker.entity.FoodItemLocalizationEntity> localization =
                ArgumentCaptor.forClass(com.grun.calorietracker.entity.FoodItemLocalizationEntity.class);
        verify(foodItemLocalizationRepository).save(localization.capture());
        assertEquals("Sut", localization.getValue().getDisplayName());
        assertEquals(PreferredLanguage.TR, localization.getValue().getLanguage());
    }

    @Test
    void acceptSuggestion_whenServingRecommendationIsReviewOnly_doesNotCloseSuggestion() {
        FoodItemEntity product = product(1L, "Milk", 48.0);
        ProductQualitySuggestionEntity suggestion = suggestion(
                12L, product, ProductQualitySuggestionType.SERVING_OPTION, null, "1 cup = 240 ml"
        );
        suggestion.setFieldName("servingOptions.cup.gramWeight");
        when(productQualitySuggestionRepository.findForUpdateById(12L)).thenReturn(Optional.of(suggestion));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                service.acceptSuggestion(12L, "admin@test.com"));

        assertEquals(ProductQualitySuggestionStatus.OPEN, suggestion.getStatus());
        verify(productQualitySuggestionRepository, never()).save(suggestion);
    }

    @Test
    void acceptSuggestion_whenNutritionValueExceedsDeterministicLimit_keepsSuggestionOpen() {
        FoodItemEntity product = product(1L, "Olive Oil", 884.0);
        ProductQualitySuggestionEntity suggestion = suggestion(
                20L, product, ProductQualitySuggestionType.SUSPICIOUS_CALORIE_VALUE, "884.0", "901"
        );
        suggestion.setFieldName("calories");
        suggestion.setSource(ProductQualitySuggestionSource.AI_ASSISTED);
        suggestion.setConfidenceScore(95);
        when(productQualitySuggestionRepository.findForUpdateById(20L)).thenReturn(Optional.of(suggestion));

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.acceptSuggestion(20L, "admin@test.com")
        );

        assertEquals(884.0, product.getCalories());
        assertEquals(ProductQualitySuggestionStatus.OPEN, suggestion.getStatus());
        verify(suggestionReconciliationService, never()).reconcile(any(), any(), any(), any());
        verify(productQualitySuggestionRepository, never()).save(suggestion);
    }

    @Test
    void acceptSuggestion_whenProductChangedAfterScan_rejectsStaleSuggestion() {
        FoodItemEntity product = product(1L, "Milk", 48.0);
        ProductQualitySuggestionEntity suggestion = suggestion(
                21L, product, ProductQualitySuggestionType.NAME_CLEANUP, "milk", "Fresh Milk"
        );
        when(productQualitySuggestionRepository.findForUpdateById(21L)).thenReturn(Optional.of(suggestion));

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.acceptSuggestion(21L, "admin@test.com")
        );

        assertEquals("Milk", product.getName());
        assertEquals(ProductQualitySuggestionStatus.OPEN, suggestion.getStatus());
        verify(suggestionReconciliationService, never()).reconcile(any(), any(), any(), any());
    }

    @Test
    void acceptSuggestion_whenReconciliationFails_remainsOpenAndCanBeRetried() {
        FoodItemEntity product = product(1L, "Protein Yogurt", 90.0);
        product.setProtein(8.0);
        ProductQualitySuggestionEntity suggestion = suggestion(
                22L, product, ProductQualitySuggestionType.MISSING_MACRO_DATA, "8.0", "12.5"
        );
        suggestion.setFieldName("protein");
        suggestion.setSource(ProductQualitySuggestionSource.AI_ASSISTED);
        suggestion.setConfidenceScore(90);
        when(productQualitySuggestionRepository.findForUpdateById(22L)).thenReturn(Optional.of(suggestion));
        when(productQualitySuggestionRepository.save(any(ProductQualitySuggestionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.doThrow(new IllegalStateException("quality issue sync failed"))
                .doNothing()
                .when(suggestionReconciliationService)
                .reconcile(any(), any(), any(), any());

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> service.acceptSuggestion(22L, "admin@test.com")
        );
        assertEquals(ProductQualitySuggestionStatus.OPEN, suggestion.getStatus());
        verify(productQualitySuggestionRepository, never()).save(suggestion);

        product.setProtein(8.0);
        var retried = service.acceptSuggestion(22L, "admin@test.com");

        assertEquals(ProductQualitySuggestionStatus.ACCEPTED, retried.getStatus());
        assertEquals(12.5, product.getProtein());
        verify(productQualitySuggestionRepository).save(suggestion);
    }

    @Test
    void acceptSuggestion_whenEnglishAliasSuggestion_preservesRequestedLanguage() {
        FoodItemEntity product = product(1L, "Pirinç", 130.0);
        ProductQualitySuggestionEntity suggestion = suggestion(
                23L, product, ProductQualitySuggestionType.SEARCH_ALIAS, null, "rice"
        );
        suggestion.setFieldName("searchAliases.EN");
        when(productQualitySuggestionRepository.findForUpdateById(23L)).thenReturn(Optional.of(suggestion));
        when(foodItemSearchAliasRepository.findByFoodItemIdAndNormalizedAliasAndLanguage(
                1L, "rice", PreferredLanguage.EN
        )).thenReturn(Optional.empty());
        when(foodItemSearchAliasRepository.save(any(FoodItemSearchAliasEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(productQualitySuggestionRepository.save(any(ProductQualitySuggestionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.acceptSuggestion(23L, "admin@test.com");

        ArgumentCaptor<FoodItemSearchAliasEntity> aliasCaptor =
                ArgumentCaptor.forClass(FoodItemSearchAliasEntity.class);
        verify(foodItemSearchAliasRepository).save(aliasCaptor.capture());
        assertEquals(PreferredLanguage.EN, aliasCaptor.getValue().getLanguage());
    }
    @Test
    void acceptSuggestion_whenSuggestionTypeDoesNotOwnField_treatsItAsReviewOnly() {
        FoodItemEntity product = product(1L, "Milk", 48.0);
        ProductQualitySuggestionEntity suggestion = suggestion(
                24L, product, ProductQualitySuggestionType.REGION_MISMATCH, "48.0", "50"
        );
        suggestion.setFieldName("calories");
        when(productQualitySuggestionRepository.findForUpdateById(24L)).thenReturn(Optional.of(suggestion));

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.acceptSuggestion(24L, "admin@test.com")
        );

        assertEquals(48.0, product.getCalories());
        assertEquals(ProductQualitySuggestionStatus.OPEN, suggestion.getStatus());
        verify(suggestionReconciliationService, never()).reconcile(any(), any(), any(), any());
    }

    @Test
    void acceptSuggestion_whenAiNutritionConfidenceIsLow_keepsSuggestionOpen() {
        FoodItemEntity product = product(1L, "Milk", 48.0);
        ProductQualitySuggestionEntity suggestion = suggestion(
                25L, product, ProductQualitySuggestionType.SUSPICIOUS_CALORIE_VALUE, "48.0", "50"
        );
        suggestion.setFieldName("calories");
        suggestion.setSource(ProductQualitySuggestionSource.AI_ASSISTED);
        suggestion.setConfidenceScore(79);
        when(productQualitySuggestionRepository.findForUpdateById(25L)).thenReturn(Optional.of(suggestion));

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.acceptSuggestion(25L, "admin@test.com")
        );

        assertEquals(ProductQualitySuggestionStatus.OPEN, suggestion.getStatus());
        verify(suggestionReconciliationService, never()).reconcile(any(), any(), any(), any());
    }
    private ProductQualitySuggestionEntity suggestion(
            Long id,
            FoodItemEntity product,
            ProductQualitySuggestionType type,
            String currentValue,
            String suggestedValue
    ) {
        ProductQualitySuggestionEntity suggestion = new ProductQualitySuggestionEntity();
        suggestion.setId(id);
        suggestion.setFoodItem(product);
        suggestion.setSuggestionType(type);
        suggestion.setSource(ProductQualitySuggestionSource.RULE_BASED);
        suggestion.setStatus(ProductQualitySuggestionStatus.OPEN);
        suggestion.setCurrentValue(currentValue);
        suggestion.setSuggestedValue(suggestedValue);
        suggestion.setConfidenceScore(85);
        return suggestion;
    }

    private FoodItemEntity product(Long id, String name, Double calories) {
        FoodItemEntity product = new FoodItemEntity();
        product.setId(id);
        product.setName(name);
        product.setCalories(calories);
        product.setVerificationStatus(VerificationStatus.RAW_IMPORTED);
        product.setCatalogType(FoodCatalogType.BRANDED_PRODUCT);
        product.setMarketRegion(MarketRegion.UK_IE);
        product.setIsCustom(false);
        return product;
    }
}






