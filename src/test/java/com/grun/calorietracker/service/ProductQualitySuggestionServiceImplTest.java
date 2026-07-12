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
        when(productQualitySuggestionRepository.findById(10L)).thenReturn(Optional.of(suggestion));
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
        when(productQualitySuggestionRepository.findById(11L)).thenReturn(Optional.of(suggestion));
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
    void acceptSuggestion_whenReviewOnlyType_closesWithoutApplyingProductChanges() {
        FoodItemEntity product = product(1L, "Semi Skimmed Milk", 48.0);
        ProductQualitySuggestionEntity suggestion = suggestion(13L, product, ProductQualitySuggestionType.REGION_MISMATCH, "EU", "UK_IE");
        when(productQualitySuggestionRepository.findById(13L)).thenReturn(Optional.of(suggestion));
        when(productQualitySuggestionRepository.save(any(ProductQualitySuggestionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.acceptSuggestion(13L, "admin@test.com");

        assertEquals(MarketRegion.UK_IE, product.getMarketRegion());
        assertEquals(ProductQualitySuggestionStatus.ACCEPTED, result.getStatus());
        assertEquals("admin@test.com", result.getReviewedBy());
        verify(foodItemRepository, never()).save(product);
    }
    @Test
    void acceptSuggestion_whenSupportedFieldSuggestion_updatesProductAndAuditsChange() {
        FoodItemEntity product = product(1L, "Protein Yogurt", 90.0);
        product.setProtein(8.0);
        ProductQualitySuggestionEntity suggestion = suggestion(14L, product, ProductQualitySuggestionType.MISSING_MACRO_DATA, "8.0", "12.5");
        suggestion.setFieldName("protein");
        suggestion.setSource(ProductQualitySuggestionSource.AI_ASSISTED);
        when(productQualitySuggestionRepository.findById(14L)).thenReturn(Optional.of(suggestion));
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
        when(productQualitySuggestionRepository.findById(12L)).thenReturn(Optional.of(suggestion));
        when(productQualitySuggestionRepository.save(any(ProductQualitySuggestionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.rejectSuggestion(12L, "admin@test.com");

        assertEquals("milk", product.getName());
        assertEquals(ProductQualitySuggestionStatus.REJECTED, result.getStatus());
        assertEquals("admin@test.com", result.getReviewedBy());
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






