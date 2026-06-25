package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodItemSearchAliasEntity;
import com.grun.calorietracker.entity.FoodProductReviewAuditEntity;
import com.grun.calorietracker.entity.ProductQualitySuggestionEntity;
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
import com.grun.calorietracker.repository.ProductQualitySuggestionRepository;
import com.grun.calorietracker.service.impl.ProductQualitySuggestionServiceImpl;
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

    @InjectMocks
    private ProductQualitySuggestionServiceImpl service;

    @Test
    void scanSuggestions_createsNameCleanupAndSafeAliasSuggestionsWithoutUpdatingProduct() {
        FoodItemEntity plainMilk = product(1L, "milk", 48.0);
        FoodItemEntity milkChocolate = product(2L, "Milk Chocolate", 535.0);

        when(foodItemRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(plainMilk, milkChocolate)));
        when(productQualitySuggestionRepository.existsByFoodItemIdAndSuggestionTypeAndSuggestedValueAndStatus(
                any(), any(), any(), eq(ProductQualitySuggestionStatus.OPEN)
        )).thenReturn(false);

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
                        && "s\u00fct".equals(suggestion.getSuggestedValue())
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