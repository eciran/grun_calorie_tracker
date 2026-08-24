package com.grun.calorietracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.FoodProductImportResultDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodItemLocalizationEntity;
import com.grun.calorietracker.entity.FoodItemSearchAliasEntity;
import com.grun.calorietracker.entity.FoodItemServingOptionEntity;
import com.grun.calorietracker.entity.FoodItemServingOptionLocalizationEntity;
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodDataSource;
import com.grun.calorietracker.enums.FoodProductImportFormat;
import com.grun.calorietracker.enums.FoodProductImportMode;
import com.grun.calorietracker.enums.FoodPreparationState;
import com.grun.calorietracker.enums.FoodNutritionBasis;
import com.grun.calorietracker.enums.FoodNutritionReferenceUnit;
import com.grun.calorietracker.enums.FoodServingOptionUnit;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.repository.FoodItemLocalizationRepository;
import com.grun.calorietracker.repository.FoodItemSearchAliasRepository;
import com.grun.calorietracker.repository.FoodItemServingOptionRepository;
import com.grun.calorietracker.repository.FoodItemServingOptionLocalizationRepository;
import com.grun.calorietracker.service.impl.FoodProductImportServiceImpl;
import com.grun.calorietracker.service.support.FoodProductQualityIssueTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class FoodProductImportServiceImplTest {

    @Mock
    private FoodItemRepository foodItemRepository;

    @Mock
    private FoodItemLocalizationRepository foodItemLocalizationRepository;

    @Mock
    private FoodItemSearchAliasRepository foodItemSearchAliasRepository;

    @Mock
    private FoodItemServingOptionRepository foodItemServingOptionRepository;

    @Mock
    private FoodItemServingOptionLocalizationRepository foodItemServingOptionLocalizationRepository;

    @Mock
    private FoodProductQualityIssueTracker foodProductQualityIssueTracker;

    @Mock
    private com.grun.calorietracker.service.FoodProductEvidenceService foodProductEvidenceService;

    private FoodProductImportServiceImpl foodProductImportService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        foodProductImportService = new FoodProductImportServiceImpl(
                foodItemRepository,
                foodItemLocalizationRepository,
                foodItemSearchAliasRepository,
                foodItemServingOptionRepository,
                foodItemServingOptionLocalizationRepository,
                foodProductQualityIssueTracker,
                foodProductEvidenceService,
                new ObjectMapper()
        );
    }

    @Test
    void importCsv_insertsAndUpdatesProductsByNormalizedBarcode() {
        FoodItemEntity existing = new FoodItemEntity();
        existing.setId(1L);
        existing.setBarcode("3017620422003");
        existing.setNormalizedBarcode("3017620422003");
        existing.setName("Old Nutella");

        when(foodItemRepository.findByNormalizedBarcodeIn(any(), any(Sort.class))).thenReturn(List.of(existing));
        when(foodItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = csv("""
                barcode,name,calories,protein,fat,carbs,market_region,display_image_url
                3017620422003,Nutella,539,6.3,30.9,57.5,UK_IE,https://cdn.grun.app/nutella.jpg
                8690000000011,GRun Yogurt,65,10,1.5,3.2,TR,
                """);

        FoodProductImportResultDto result = foodProductImportService.importCsv(file, "admin@test.com");

        assertEquals(2, result.getTotalRows());
        assertEquals(1, result.getInsertedRows());
        assertEquals(1, result.getUpdatedRows());
        assertEquals(0, result.getSkippedRows());
        assertEquals(2, result.getSavedRows());
        assertEquals(0, result.getReviewRequiredRows());
        assertEquals("CSV", result.getImportFormat());
        assertEquals(0, result.getMissingMarketRegionRows());
        assertEquals(0, result.getUnsupportedMarketRegionRows());
        assertEquals(1, result.getMarketRegionCounts().get("UK_IE"));
        assertEquals(1, result.getMarketRegionCounts().get("TR"));
        assertEquals(2, result.getCatalogTypeCounts().get("BRANDED_PRODUCT"));
        assertEquals(2, result.getDataSourceCounts().get("ADMIN_IMPORT"));

        ArgumentCaptor<List<FoodItemEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(foodItemRepository).saveAll(captor.capture());
        List<FoodItemEntity> savedProducts = captor.getValue();

        FoodItemEntity updated = savedProducts.get(0);
        assertEquals("Nutella", updated.getName());
        assertEquals(FoodDataSource.ADMIN_IMPORT, updated.getDataSource());
        assertEquals(VerificationStatus.VERIFIED, updated.getVerificationStatus());
        assertEquals(ImageStatus.APPROVED, updated.getImageStatus());
        assertEquals(MarketRegion.UK_IE, updated.getMarketRegion());
        assertEquals("admin@test.com", updated.getReviewedBy());

        FoodItemEntity inserted = savedProducts.get(1);
        assertEquals("8690000000011", inserted.getNormalizedBarcode());
        assertEquals("GRun Yogurt", inserted.getName());
        assertEquals(MarketRegion.TR, inserted.getMarketRegion());
        assertEquals(ImageStatus.NEEDS_REVIEW, inserted.getImageStatus());
        assertEquals(FoodNutritionReferenceUnit.PER_100G, inserted.getNutritionReferenceUnit());
    }

    @Test
    void importCsv_mergesMarketAvailabilityWithoutDuplicatingBarcodeIdentity() {
        FoodItemEntity existing = new FoodItemEntity();
        existing.setId(1L);
        existing.setBarcode("8690000000001");
        existing.setNormalizedBarcode("8690000000001");
        existing.setSourceKey("barcode:8690000000001");
        existing.setName("Shared Product");
        existing.setMarketRegion(MarketRegion.UK_IE);
        existing.setMarketRegions(new HashSet<>(Set.of(MarketRegion.UK_IE)));

        when(foodItemRepository.findByNormalizedBarcodeIn(any(), any(Sort.class))).thenReturn(List.of(existing));
        when(foodItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = csv("""
                barcode,name,brand,calories,protein,fat,carbs,market_region,market_regions
                8690000000001,Shared Product,Shared Brand,100,2,3,15,EU,EU;TR
                """);

        FoodProductImportResultDto result = foodProductImportService.importCsv(
                file,
                "admin@test.com",
                FoodProductImportMode.RAW_EXTERNAL
        );

        assertEquals(0, result.getInsertedRows());
        assertEquals(1, result.getUpdatedRows());
        ArgumentCaptor<List<FoodItemEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(foodItemRepository).saveAll(captor.capture());
        FoodItemEntity updated = captor.getValue().get(0);
        assertEquals(MarketRegion.UK_IE, updated.getMarketRegion());
        assertEquals(Set.of(MarketRegion.UK_IE, MarketRegion.EU, MarketRegion.TR), updated.getMarketRegions());
        assertEquals("barcode:8690000000001", updated.getSourceKey());
    }
    @Test
    void importCsv_normalizesProductAndBrandDisplayNames() {
        when(foodItemRepository.findByNormalizedBarcodeIn(any(), any(Sort.class))).thenReturn(List.of());
        when(foodItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = csv("""
                barcode,name,brand,calories,protein,fat,carbs,market_region
                1234567890123,milk,almond breeze,48,4,2,5,UK_IE
                1234567890124,PEANUT BUTTER PROTEIN BAR,M&S FOOD,357,35,14,36.7,UK_IE
                """);

        foodProductImportService.importCsv(file, "admin@test.com", FoodProductImportMode.RAW_EXTERNAL);

        ArgumentCaptor<List<FoodItemEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(foodItemRepository).saveAll(captor.capture());
        List<FoodItemEntity> savedProducts = captor.getValue();

        assertEquals("Milk", savedProducts.get(0).getName());
        assertEquals("Almond Breeze", savedProducts.get(0).getBrand());
        assertEquals("Peanut Butter Protein Bar", savedProducts.get(1).getName());
        assertEquals("M&S Food", savedProducts.get(1).getBrand());
    }

    @Test
    void importCsv_importsMultilingualSearchAliases() {
        when(foodItemRepository.findByNormalizedBarcodeIn(any(), any(Sort.class))).thenReturn(List.of());
        when(foodItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(foodItemSearchAliasRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = csv("""
                barcode,name,brand,calories,protein,fat,carbs,market_region,aliases_tr,aliases_en
                1234567890123,Semi Skimmed Milk,Tesco,48,4,2,5,UK_IE,yarım yağlı süt|az yağlı süt,semi skim milk|milk
                """);

        foodProductImportService.importCsv(file, "admin@test.com", FoodProductImportMode.RAW_EXTERNAL);

        ArgumentCaptor<List<FoodItemSearchAliasEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(foodItemSearchAliasRepository).saveAll(captor.capture());
        List<FoodItemSearchAliasEntity> aliases = captor.getValue();

        assertEquals(4, aliases.size());
        assertEquals(PreferredLanguage.TR, aliases.get(0).getLanguage());
        assertEquals("yarım yağlı süt", aliases.get(0).getAlias());
        assertEquals("yarim yagli sut", aliases.get(0).getNormalizedAlias());
        assertEquals(PreferredLanguage.EN, aliases.get(2).getLanguage());
        assertEquals("semi skim milk", aliases.get(2).getNormalizedAlias());
    }
    @Test
    void importCsv_pilotFileSupportsUkIeTrAndEuRegions() {
        when(foodItemRepository.findByNormalizedBarcodeIn(any(), any(Sort.class))).thenReturn(List.of());
        when(foodItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = csv("""
                barcode,name,calories,market_region,display_image_url
                5010000000001,Irish Oats,380,Ireland,https://cdn.grun.app/irish-oats.jpg
                8690000000011,Turkish Yogurt,65,TR,https://cdn.grun.app/yogurt.jpg
                5000000000002,UK Protein Bar,410,United Kingdom,https://cdn.grun.app/protein-bar.jpg
                4000000000003,EU Muesli,380,European Union,https://cdn.grun.app/eu-muesli.jpg
                """);

        FoodProductImportResultDto result = foodProductImportService.importCsv(file, "admin@test.com");

        assertEquals(4, result.getTotalRows());
        assertEquals(4, result.getInsertedRows());
        assertEquals(0, result.getSkippedRows());
        assertEquals(0, result.getReviewRequiredRows());
        assertEquals(2, result.getMarketRegionCounts().get("UK_IE"));
        assertEquals(1, result.getMarketRegionCounts().get("TR"));
        assertEquals(1, result.getMarketRegionCounts().get("EU"));

        ArgumentCaptor<List<FoodItemEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(foodItemRepository).saveAll(captor.capture());
        List<FoodItemEntity> savedProducts = captor.getValue();

        assertEquals(MarketRegion.UK_IE, savedProducts.get(0).getMarketRegion());
        assertEquals(MarketRegion.TR, savedProducts.get(1).getMarketRegion());
        assertEquals(MarketRegion.UK_IE, savedProducts.get(2).getMarketRegion());
        assertEquals(MarketRegion.EU, savedProducts.get(3).getMarketRegion());
        assertEquals(VerificationStatus.VERIFIED, savedProducts.get(0).getVerificationStatus());
        assertEquals(ImageStatus.APPROVED, savedProducts.get(0).getImageStatus());
    }

    @Test
    void importCsv_skipsRowsWithoutBarcodeOrName() {
        when(foodItemRepository.findByNormalizedBarcodeIn(any(), any(Sort.class))).thenReturn(List.of());

        MockMultipartFile file = csv("""
                barcode,name,calories
                ,Missing Barcode,100
                123456,,100
                """);

        FoodProductImportResultDto result = foodProductImportService.importCsv(file, "admin@test.com");

        assertEquals(2, result.getTotalRows());
        assertEquals(0, result.getSavedRows());
        assertEquals(2, result.getSkippedRows());
        assertEquals(2, result.getErrors().size());
        verify(foodItemRepository).saveAll(eq(List.of()));
    }

    @Test
    void importCsv_skipsLaterDuplicateInputRowAndKeepsFirstValidValues() {
        when(foodItemRepository.findByNormalizedBarcodeIn(any(), any(Sort.class))).thenReturn(List.of());
        when(foodItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = csv("""
                barcode,name,calories,protein,fat,carbs,market_region
                1234567890123,Original Milk,48,3.4,1.7,4.8,UK_IE
                1234567890123,Duplicate Milk,480,34,17,48,UK_IE
                """);

        FoodProductImportResultDto result = foodProductImportService.importCsv(file, "admin@test.com");

        assertEquals(2, result.getTotalRows());
        assertEquals(1, result.getSavedRows());
        assertEquals(1, result.getSkippedRows());
        assertEquals(1, result.getDuplicateInputRows());
        assertEquals(1, result.getQualityWarningCounts().get("DUPLICATE_INPUT_KEY"));

        ArgumentCaptor<List<FoodItemEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(foodItemRepository).saveAll(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals("Original Milk", captor.getValue().get(0).getName());
        assertEquals(48.0, captor.getValue().get(0).getCalories());
    }

    @Test
    void importCsv_allowsValidRowAfterEarlierDuplicateKeyRowFailedValidation() {
        when(foodItemRepository.findByNormalizedBarcodeIn(any(), any(Sort.class))).thenReturn(List.of());
        when(foodItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = csv("""
                barcode,name,calories,protein,fat,carbs,market_region
                1234567890123,,48,3.4,1.7,4.8,UK_IE
                1234567890123,Valid Milk,48,3.4,1.7,4.8,UK_IE
                """);

        FoodProductImportResultDto result = foodProductImportService.importCsv(file, "admin@test.com");

        assertEquals(2, result.getTotalRows());
        assertEquals(1, result.getSavedRows());
        assertEquals(1, result.getSkippedRows());
        assertEquals(0, result.getDuplicateInputRows());
        assertEquals(1, result.getErrors().size());

        ArgumentCaptor<List<FoodItemEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(foodItemRepository).saveAll(captor.capture());
        assertEquals("Valid Milk", captor.getValue().get(0).getName());
    }

    @Test
    void importCsv_synchronizesEnglishAndTurkishLocalizedDisplayNames() {
        when(foodItemRepository.findBySourceKeyIn(any(), any(Sort.class))).thenReturn(List.of());
        when(foodItemRepository.saveAll(any())).thenAnswer(invocation -> {
            List<FoodItemEntity> products = invocation.getArgument(0);
            for (int index = 0; index < products.size(); index++) {
                products.get(index).setId((long) index + 1);
            }
            return products;
        });
        when(foodItemLocalizationRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = csv("""
                catalog_type,source_key,name,display_name,short_display_name,display_name_en,short_display_name_en,display_name_tr,short_display_name_tr,calories,protein,fat,carbs,market_region,preparation_state
                GENERIC_INGREDIENT,GLOBAL:GENERIC_INGREDIENT:RAW:banana,Banana raw,Raw Banana,Banana,Banana,Banana,Muz,Muz,89,1.1,0.3,22.8,GLOBAL,RAW
                """);

        FoodProductImportResultDto result = foodProductImportService.importCsv(file, "admin@test.com");

        assertEquals(1, result.getSavedRows());
        ArgumentCaptor<List<FoodItemLocalizationEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(foodItemLocalizationRepository).saveAll(captor.capture());
        assertEquals(2, captor.getValue().size());
        assertEquals(List.of(PreferredLanguage.EN, PreferredLanguage.TR), captor.getValue().stream()
                .map(FoodItemLocalizationEntity::getLanguage)
                .toList());
        assertEquals(List.of("Banana", "Muz"), captor.getValue().stream()
                .map(FoodItemLocalizationEntity::getDisplayName)
                .toList());
    }
    @Test
    void importCsv_upsertsStructuredServingOptionsWithoutDuplicatingExistingLabel() {
        when(foodItemRepository.findBySourceKeyIn(any(), any(Sort.class))).thenReturn(List.of());
        when(foodItemRepository.saveAll(any())).thenAnswer(invocation -> {
            List<FoodItemEntity> products = invocation.getArgument(0);
            products.get(0).setId(41L);
            return products;
        });

        FoodItemServingOptionEntity existingMedium = new FoodItemServingOptionEntity();
        existingMedium.setId(7L);
        FoodItemEntity existingProduct = new FoodItemEntity();
        existingProduct.setId(41L);
        existingMedium.setFoodItem(existingProduct);
        existingMedium.setLabel("1 medium banana");
        existingMedium.setUnitType(FoodServingOptionUnit.PIECE);
        existingMedium.setQuantity(1.0);
        existingMedium.setGramWeight(110.0);
        existingMedium.setIsDefault(true);
        when(foodItemServingOptionRepository
                .findByFoodItemIdInOrderByFoodItemIdAscIsDefaultDescLabelAsc(any()))
                .thenReturn(List.of(existingMedium));
        when(foodItemServingOptionRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = csv("""
                catalog_type,source_key,name,calories,market_region,preparation_state,serving_options_json
                GENERIC_INGREDIENT,GLOBAL:GENERIC_INGREDIENT:RAW:banana,Banana raw,89,GLOBAL,RAW,"[{""label"":""1 medium banana"",""unitType"":""PIECE"",""quantity"":1,""gramWeight"":118,""defaultOption"":true},{""label"":""1/2 banana"",""unitType"":""PIECE"",""quantity"":0.5,""gramWeight"":59,""defaultOption"":false}]"
                """);

        FoodProductImportResultDto result = foodProductImportService.importCsv(file, "admin@test.com");

        assertEquals(1, result.getSavedRows());
        assertEquals(0, result.getQualityWarningCounts().getOrDefault("INVALID_SERVING_OPTIONS", 0));
        ArgumentCaptor<List<FoodItemServingOptionEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(foodItemServingOptionRepository).saveAll(captor.capture());
        List<FoodItemServingOptionEntity> options = captor.getValue();
        assertEquals(2, options.size());
        assertEquals(7L, options.get(0).getId());
        assertEquals(118.0, options.get(0).getGramWeight());
        assertEquals(true, options.get(0).getIsDefault());
        assertEquals("1/2 banana", options.get(1).getLabel());
        assertEquals(59.0, options.get(1).getGramWeight());
    }

    @Test
    void importCsv_upsertsLocalizedServingLabelsByOptionAndLanguage() {
        when(foodItemRepository.findBySourceKeyIn(any(), any(Sort.class))).thenReturn(List.of());
        when(foodItemRepository.saveAll(any())).thenAnswer(invocation -> {
            List<FoodItemEntity> products = invocation.getArgument(0);
            products.get(0).setId(51L);
            return products;
        });
        when(foodItemServingOptionRepository
                .findByFoodItemIdInOrderByFoodItemIdAscIsDefaultDescLabelAsc(any()))
                .thenReturn(List.of());
        when(foodItemServingOptionRepository.saveAll(any())).thenAnswer(invocation -> {
            List<FoodItemServingOptionEntity> options = invocation.getArgument(0);
            options.get(0).setId(61L);
            return options;
        });
FoodItemServingOptionEntity existingOptionReference = new FoodItemServingOptionEntity();
        existingOptionReference.setId(61L);
        FoodItemServingOptionLocalizationEntity existingTurkish =
                new FoodItemServingOptionLocalizationEntity();
        existingTurkish.setId(71L);
        existingTurkish.setServingOption(existingOptionReference);
        existingTurkish.setLanguage(PreferredLanguage.TR);
        existingTurkish.setLabel("eski etiket");
        existingTurkish.setActive(true);
        when(foodItemServingOptionLocalizationRepository.findByServingOptionIdIn(any()))
                .thenReturn(List.of(existingTurkish));
        when(foodItemServingOptionLocalizationRepository.saveAll(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = csv("""
                catalog_type,source_key,name,calories,market_region,preparation_state,serving_options_json
                GENERIC_INGREDIENT,GLOBAL:GENERIC_INGREDIENT:RAW:banana-localized-serving,Banana raw,89,GLOBAL,RAW,"[{""label"":""1 medium banana"",""unitType"":""PIECE"",""quantity"":1,""gramWeight"":118,""defaultOption"":true,""labels"":{""EN"":""1 medium banana"",""TR"":""1 orta boy muz""}}]"
                """);

        FoodProductImportResultDto result = foodProductImportService.importCsv(file, "admin@test.com");

        assertEquals(1, result.getSavedRows());
        assertEquals(0, result.getQualityWarningCounts().getOrDefault("INVALID_SERVING_OPTIONS", 0));
        ArgumentCaptor<List<FoodItemServingOptionLocalizationEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(foodItemServingOptionLocalizationRepository).saveAll(captor.capture());
        assertEquals(2, captor.getValue().size());
        assertEquals(List.of(PreferredLanguage.EN, PreferredLanguage.TR), captor.getValue().stream()
                .map(FoodItemServingOptionLocalizationEntity::getLanguage)
                .toList());
        assertEquals(List.of("1 medium banana", "1 orta boy muz"), captor.getValue().stream()
                .map(FoodItemServingOptionLocalizationEntity::getLabel)
                .toList());
        assertEquals(List.of(61L, 61L), captor.getValue().stream()
                .map(localization -> localization.getServingOption().getId())
                .toList());
        assertEquals(71L, captor.getValue().stream()
                .filter(localization -> localization.getLanguage() == PreferredLanguage.TR)
                .findFirst()
                .orElseThrow()
                .getId());
    }
    @Test
    void importCsv_reportsInvalidServingOptionsAndDoesNotPersistThem() {
        when(foodItemRepository.findBySourceKeyIn(any(), any(Sort.class))).thenReturn(List.of());
        when(foodItemRepository.saveAll(any())).thenAnswer(invocation -> {
            List<FoodItemEntity> products = invocation.getArgument(0);
            products.get(0).setId(42L);
            return products;
        });

        MockMultipartFile file = csv("""
                catalog_type,source_key,name,calories,market_region,preparation_state,serving_options_json
                GENERIC_INGREDIENT,GLOBAL:GENERIC_INGREDIENT:RAW:apple,Apple raw,52,GLOBAL,RAW,"[{""label"":""1 apple"",""unitType"":""PIECE"",""quantity"":1}]"
                """);

        FoodProductImportResultDto result = foodProductImportService.importCsv(file, "admin@test.com");

        assertEquals(1, result.getQualityWarningCounts().get("INVALID_SERVING_OPTIONS"));
        verify(foodItemServingOptionRepository, times(0)).saveAll(any());
    }
    @Test
    void importCsv_marksNutritionBasisExplicitlyByCatalogPolicy() {
        when(foodItemRepository.findBySourceKeyIn(any(), any(Sort.class))).thenReturn(List.of());
        when(foodItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = csv("""
                catalog_type,name,calories,protein,fat,carbs,market_region,preparation_state,nutrition_basis
                LOCAL_DISH,Mercimek Corbasi,92,5.8,2.4,12.1,TR,PREPARED,
                LOCAL_DISH,Ev Yapimi Pilav,180,3.2,4.0,32.0,TR,PREPARED,CALCULATED
                GENERIC_INGREDIENT,Rolled Oats,389,16.9,6.9,66.3,GLOBAL,RAW,
                """);

        FoodProductImportResultDto result = foodProductImportService.importCsv(file, "admin@test.com");

        assertEquals(3, result.getSavedRows());
        ArgumentCaptor<List<FoodItemEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(foodItemRepository).saveAll(captor.capture());
        List<FoodItemEntity> products = captor.getValue();
        assertEquals(FoodNutritionBasis.ESTIMATED, products.get(0).getNutritionBasis());
        assertEquals(FoodNutritionBasis.CALCULATED, products.get(1).getNutritionBasis());
        assertEquals(FoodNutritionBasis.SOURCE_REPORTED, products.get(2).getNutritionBasis());
    }

    @Test
    void importCsv_acceptsExplicitNonBarcodeCatalogRows() {
        when(foodItemRepository.findBySourceKeyIn(any(), any(Sort.class))).thenReturn(List.of());
        when(foodItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = csv("""
                catalog_type,name,calories,protein,fat,carbs,market_region,preparation_state,serving_size_grams,serving_unit
                LOCAL_DISH,Mercimek Corbasi,92,5.8,2.4,12.1,TR,PREPARED,250,bowl
                GENERIC_INGREDIENT,Rolled Oats,389,16.9,6.9,66.3,UK_IE,RAW,100,g
                """);

        FoodProductImportResultDto result = foodProductImportService.importCsv(file, "admin@test.com");

        assertEquals(2, result.getTotalRows());
        assertEquals(2, result.getSavedRows());
        assertEquals(0, result.getSkippedRows());
        assertEquals(1, result.getCatalogTypeCounts().get("LOCAL_DISH"));
        assertEquals(1, result.getCatalogTypeCounts().get("GENERIC_INGREDIENT"));
        assertEquals(2, result.getDataSourceCounts().get("ADMIN_IMPORT"));

        ArgumentCaptor<List<FoodItemEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(foodItemRepository).saveAll(captor.capture());
        List<FoodItemEntity> savedProducts = captor.getValue();

        assertEquals(FoodCatalogType.LOCAL_DISH, savedProducts.get(0).getCatalogType());
        assertEquals("TR:LOCAL_DISH:PREPARED:mercimek_corbasi", savedProducts.get(0).getSourceKey());
        assertEquals(FoodPreparationState.PREPARED, savedProducts.get(0).getPreparationState());
        assertEquals(null, savedProducts.get(0).getBarcode());
        assertEquals(FoodCatalogType.GENERIC_INGREDIENT, savedProducts.get(1).getCatalogType());
        assertEquals("UK_IE:GENERIC_INGREDIENT:RAW:rolled_oats", savedProducts.get(1).getSourceKey());
        assertEquals(FoodPreparationState.RAW, savedProducts.get(1).getPreparationState());
    }

    @Test
    void importCsv_persistsLocalDishFamilyVariantAndCanonicalIdentity() {
        when(foodItemRepository.findBySourceKeyIn(any(), any(Sort.class))).thenReturn(List.of());
        when(foodItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = csv("""
                catalog_type,source_key,name,calories,protein,fat,carbs,market_region,preparation_state,nutrition_basis,dish_family_key,dish_variant_key
                LOCAL_DISH,TR:LOCAL_DISH:PREPARED:kuru_fasulye:etli,Etli Kuru Fasulye,165,10,7,15,TR,PREPARED,ESTIMATED,kuru-fasulye,etli
                """);

        FoodProductImportResultDto result = foodProductImportService.importCsv(file, "admin@test.com");

        assertEquals(1, result.getSavedRows());
        ArgumentCaptor<List<FoodItemEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(foodItemRepository).saveAll(captor.capture());
        FoodItemEntity dish = captor.getValue().get(0);
        assertEquals("kuru_fasulye", dish.getDishFamilyKey());
        assertEquals("etli", dish.getDishVariantKey());
        assertEquals("TR:LOCAL_DISH:PREPARED:kuru_fasulye:etli", dish.getCanonicalFoodKey());
        assertEquals(null, dish.getBarcode());
    }



    @Test
    void importCsv_keepsRawAndCookedNonBarcodeFoodsSeparate() {
        when(foodItemRepository.findBySourceKeyIn(any(), any(Sort.class))).thenReturn(List.of());
        when(foodItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = csv("""
                catalog_type,name,calories,market_region,preparation_state
                GENERIC_INGREDIENT,Rice,360,GLOBAL,RAW
                GENERIC_INGREDIENT,Rice,130,GLOBAL,COOKED
                """);

        FoodProductImportResultDto result = foodProductImportService.importCsv(file, "admin@test.com");

        assertEquals(2, result.getSavedRows());

        ArgumentCaptor<List<FoodItemEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(foodItemRepository).saveAll(captor.capture());
        List<FoodItemEntity> savedProducts = captor.getValue();

        assertEquals("GLOBAL:GENERIC_INGREDIENT:RAW:rice", savedProducts.get(0).getSourceKey());
        assertEquals(FoodPreparationState.RAW, savedProducts.get(0).getPreparationState());
        assertEquals("GLOBAL:GENERIC_INGREDIENT:COOKED:rice", savedProducts.get(1).getSourceKey());
        assertEquals(FoodPreparationState.COOKED, savedProducts.get(1).getPreparationState());
    }

    @Test
    void importCsv_curatedGenericStaplesSeedIsProductionReady() throws Exception {
        when(foodItemRepository.findBySourceKeyIn(any(), any(Sort.class))).thenReturn(List.of());
        when(foodItemRepository.saveAll(any())).thenAnswer(invocation -> {
            List<FoodItemEntity> products = invocation.getArgument(0);
            for (int index = 0; index < products.size(); index++) {
                products.get(index).setId((long) index + 1);
            }
            return products;
        });
        when(foodItemSearchAliasRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(foodItemLocalizationRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        byte[] bytes = Files.readAllBytes(Path.of("src", "test", "resources", "food-generic-staples-curated-seed.csv"));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "food-generic-staples-curated-seed.csv",
                "text/csv",
                bytes
        );

        FoodProductImportResultDto result = foodProductImportService.importCsv(file, "admin@test.com");

        assertEquals(14, result.getTotalRows());
        assertEquals(14, result.getSavedRows());
        assertEquals(0, result.getSkippedRows());
        assertEquals(0, result.getQualityWarningCounts().getOrDefault("GENERIC_MISSING_PREPARATION_STATE", 0));
        assertEquals(0, result.getQualityWarningCounts().getOrDefault("SUSPICIOUS_DISPLAY_NAME", 0));
        assertEquals(14, result.getCatalogTypeCounts().get("GENERIC_INGREDIENT"));
        assertEquals(14, result.getDataSourceCounts().get("LOCAL_CURATED"));

        ArgumentCaptor<List<FoodItemEntity>> productCaptor = ArgumentCaptor.forClass(List.class);
        verify(foodItemRepository).saveAll(productCaptor.capture());
        List<FoodItemEntity> savedProducts = productCaptor.getValue();

        FoodItemEntity cookedRice = savedProducts.stream()
                .filter(product -> "GLOBAL:GENERIC_INGREDIENT:COOKED:white_rice".equals(product.getSourceKey()))
                .findFirst()
                .orElseThrow();
        assertEquals(FoodPreparationState.COOKED, cookedRice.getPreparationState());
        assertEquals("Cooked White Rice", cookedRice.getDisplayName());
        assertEquals("White Rice", cookedRice.getShortDisplayName());
        assertEquals(130.0, cookedRice.getCalories());

        FoodItemEntity cookedChicken = savedProducts.stream()
                .filter(product -> "GLOBAL:GENERIC_INGREDIENT:COOKED:chicken_breast".equals(product.getSourceKey()))
                .findFirst()
                .orElseThrow();
        assertEquals(FoodPreparationState.COOKED, cookedChicken.getPreparationState());
        assertEquals("Cooked Chicken Breast", cookedChicken.getDisplayName());
        assertEquals("Chicken Breast", cookedChicken.getShortDisplayName());

        ArgumentCaptor<List<FoodItemSearchAliasEntity>> aliasCaptor = ArgumentCaptor.forClass(List.class);
        verify(foodItemSearchAliasRepository).saveAll(aliasCaptor.capture());
        List<FoodItemSearchAliasEntity> aliases = aliasCaptor.getValue();

        boolean hasPirincAlias = aliases.stream().anyMatch(alias -> alias.getLanguage() == PreferredLanguage.TR
                && "pirinc".equals(alias.getNormalizedAlias()));
        boolean hasChickenAlias = aliases.stream().anyMatch(alias -> alias.getLanguage() == PreferredLanguage.EN
                && "chicken breast".equals(alias.getNormalizedAlias()));
        assertEquals(true, hasPirincAlias);
        assertEquals(true, hasChickenAlias);

        ArgumentCaptor<List<FoodItemLocalizationEntity>> localizationCaptor = ArgumentCaptor.forClass(List.class);
        verify(foodItemLocalizationRepository).saveAll(localizationCaptor.capture());
        List<FoodItemLocalizationEntity> localizations = localizationCaptor.getValue();
        assertEquals(28, localizations.size());
        assertEquals(true, localizations.stream().anyMatch(localization ->
                localization.getLanguage() == PreferredLanguage.TR
                        && "Muz".equals(localization.getDisplayName())));
        assertEquals(true, localizations.stream().anyMatch(localization ->
                localization.getLanguage() == PreferredLanguage.TR
                        && "Pişmiş Beyaz Pirinç".equals(localization.getDisplayName())));
    }

    @Test
    void importCsv_whenRawExternal_keepsProductsInReviewState() {
        when(foodItemRepository.findByNormalizedBarcodeIn(any(), any(Sort.class))).thenReturn(List.of());
        when(foodItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = csv("""
                barcode,name,calories,protein,fat,carbs,image_url,display_image_url
                5449000000996,Coca-Cola,42,0,0,10.6,https://images.openfoodfacts.org/coke.jpg,https://cdn.grun.app/coke.jpg
                """);

        FoodProductImportResultDto result = foodProductImportService.importCsv(
                file,
                "admin@test.com",
                FoodProductImportMode.RAW_EXTERNAL
        );

        assertEquals(1, result.getInsertedRows());

        ArgumentCaptor<List<FoodItemEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(foodItemRepository).saveAll(captor.capture());
        FoodItemEntity imported = captor.getValue().get(0);

        assertEquals(FoodDataSource.OPEN_FOOD_FACTS, imported.getDataSource());
        assertEquals(VerificationStatus.RAW_IMPORTED, imported.getVerificationStatus());
        assertEquals(80, imported.getConfidenceScore());
        assertEquals(true, imported.getAutoApprovedForCatalog());
        assertEquals(ImageStatus.APPROVED, imported.getImageStatus());
        assertEquals("https://images.openfoodfacts.org/coke.jpg", imported.getExternalImageUrl());
        assertEquals(null, imported.getDisplayImageUrl());
        assertEquals(null, imported.getReviewedBy());
    }

    @Test
    void importCsv_whenRawExternalPilotRows_setsReviewStateAndRegionAliases() {
        when(foodItemRepository.findByNormalizedBarcodeIn(any(), any(Sort.class))).thenReturn(List.of());
        when(foodItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = csv("""
                barcode,name,calories,country,image_url
                5010000000001,Irish Oats,380,Ireland,https://images.openfoodfacts.org/irish-oats.jpg
                8690000000011,Turkish Yogurt,65,Turkiye,https://images.openfoodfacts.org/yogurt.jpg
                5000000000002,UK Protein Bar,410,GB,https://images.openfoodfacts.org/protein-bar.jpg
                """);

        FoodProductImportResultDto result = foodProductImportService.importCsv(
                file,
                "bulk@test.com",
                FoodProductImportMode.RAW_EXTERNAL
        );

        assertEquals(3, result.getSavedRows());
        assertEquals(3, result.getReviewRequiredRows());

        ArgumentCaptor<List<FoodItemEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(foodItemRepository).saveAll(captor.capture());
        List<FoodItemEntity> savedProducts = captor.getValue();

        assertEquals(MarketRegion.UK_IE, savedProducts.get(0).getMarketRegion());
        assertEquals(MarketRegion.TR, savedProducts.get(1).getMarketRegion());
        assertEquals(MarketRegion.UK_IE, savedProducts.get(2).getMarketRegion());
        assertEquals(VerificationStatus.RAW_IMPORTED, savedProducts.get(0).getVerificationStatus());
        assertEquals(ImageStatus.APPROVED, savedProducts.get(0).getImageStatus());
        assertEquals(FoodDataSource.OPEN_FOOD_FACTS, savedProducts.get(0).getDataSource());
    }

    @Test
    void importCsv_whenDataSourceColumnProvided_usesExplicitSupportedSourceAndReportsCounts() {
        when(foodItemRepository.findBySourceKeyIn(any(), any(Sort.class))).thenReturn(List.of());
        when(foodItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = csv("""
                catalog_type,data_source,source_key,name,calories,protein,fat,carbs,market_region
                GENERIC_INGREDIENT,USDA,USDA:fdc:1102647,Raw Oats,389,16.9,6.9,66.3,GLOBAL
                LOCAL_DISH,LOCAL_CURATED,TR:LOCAL_DISH:ezogelin_corbasi,Ezogelin Corbasi,95,4.5,2.7,13.2,TR
                """);

        FoodProductImportResultDto result = foodProductImportService.importCsv(file, "admin@test.com");

        assertEquals(2, result.getSavedRows());
        assertEquals(1, result.getDataSourceCounts().get("USDA_FOODDATA"));
        assertEquals(1, result.getDataSourceCounts().get("LOCAL_CURATED"));

        ArgumentCaptor<List<FoodItemEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(foodItemRepository).saveAll(captor.capture());
        List<FoodItemEntity> savedProducts = captor.getValue();

        assertEquals(FoodDataSource.USDA_FOODDATA, savedProducts.get(0).getDataSource());
        assertEquals(FoodCatalogType.GENERIC_INGREDIENT, savedProducts.get(0).getCatalogType());
        assertEquals(FoodDataSource.LOCAL_CURATED, savedProducts.get(1).getDataSource());
        assertEquals(FoodCatalogType.LOCAL_DISH, savedProducts.get(1).getCatalogType());
    }

    @Test
    void importCsv_whenOpenFoodFactsExportFormat_mapsNativeColumns() {
        when(foodItemRepository.findByNormalizedBarcodeIn(any(), any(Sort.class))).thenReturn(List.of());
        when(foodItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = tsv("""
                code\tproduct_name\tenergy-kcal_100g\tproteins_100g\tfat_100g\tcarbohydrates_100g\tfiber_100g\tsugars_100g\tsodium_100g\tpotassium_100g\tcalcium_100g\tiron_100g\tvitamin-a_100g\tsaturated-fat_100g\ttrans-fat_100g\tserving_size\tserving_quantity\tserving_quantity_unit\timage_url\tallergens_tags\tnutrition_grade_fr
                3017620422003\tNutella Hazelnut Cocoa Spread\t539\t6.3\t30.9\t57.5\t0\t56.3\t0.107\t0.3578\t0.1824\t0.0045\t0.0009\t10.6\t0.01\t15 g\t15\tg\thttps://images.openfoodfacts.org/nutella.jpg\ten:milk,en:nuts\te
                """);

        FoodProductImportResultDto result = foodProductImportService.importCsv(
                file,
                "bulk@test.com",
                FoodProductImportMode.RAW_EXTERNAL,
                FoodProductImportFormat.AUTO
        );

        assertEquals("OPEN_FOOD_FACTS_EXPORT", result.getSourceFormat());
        assertEquals(1, result.getDataSourceCounts().get("OPEN_FOOD_FACTS"));

        ArgumentCaptor<List<FoodItemEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(foodItemRepository).saveAll(captor.capture());
        FoodItemEntity imported = captor.getValue().get(0);

        assertEquals("3017620422003", imported.getNormalizedBarcode());
        assertEquals("Nutella Hazelnut Cocoa Spread", imported.getName());
        assertEquals(FoodDataSource.OPEN_FOOD_FACTS, imported.getDataSource());
        assertEquals(539, imported.getCalories());
        assertEquals(6.3, imported.getProtein());
        assertEquals(30.9, imported.getFat());
        assertEquals(57.5, imported.getCarbs());
        assertEquals(0.358, imported.getPotassium());
        assertEquals(0.182, imported.getCalcium());
        assertEquals(0.005, imported.getIron());
        assertEquals(0.001, imported.getVitaminA());
        assertEquals(10.6, imported.getSaturatedFat());
        assertEquals(0.0, imported.getTransFat());
        assertEquals("https://images.openfoodfacts.org/nutella.jpg", imported.getImageUrl());
        assertEquals("en:milk,en:nuts", imported.getAllergens());
        assertEquals("e", imported.getNutriScore());
        assertEquals(15, imported.getServingSizeGrams());
        assertEquals("g", imported.getServingUnit());
    }

    @Test
    void importCsv_whenUsdaFormat_mapsFdcIdAndDescriptionAsGenericIngredient() {
        when(foodItemRepository.findBySourceKeyIn(any(), any(Sort.class))).thenReturn(List.of());
        when(foodItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = csv("""
                fdc_id,description,calories,protein,fat,carbohydrates_100g,fiber_100g,market_region
                1102647,Oats raw,389,16.9,6.9,66.3,10.6,GLOBAL
                """);

        FoodProductImportResultDto result = foodProductImportService.importCsv(
                file,
                "bulk@test.com",
                FoodProductImportMode.RAW_EXTERNAL,
                FoodProductImportFormat.AUTO
        );

        assertEquals("USDA_FOODDATA", result.getSourceFormat());
        assertEquals(1, result.getDataSourceCounts().get("USDA_FOODDATA"));

        ArgumentCaptor<List<FoodItemEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(foodItemRepository).saveAll(captor.capture());
        FoodItemEntity imported = captor.getValue().get(0);

        assertEquals("USDA_FOODDATA:fdc:1102647", imported.getSourceKey());
        assertEquals("Oats raw", imported.getName());
        assertEquals(FoodCatalogType.GENERIC_INGREDIENT, imported.getCatalogType());
        assertEquals(FoodDataSource.USDA_FOODDATA, imported.getDataSource());
        assertEquals(null, imported.getNormalizedBarcode());
        assertEquals(389, imported.getCalories());
        assertEquals(66.3, imported.getCarbs());
    }

    @Test
    void importCsv_whenDifferentUsdaRecordsShareCanonicalIdentity_reportsPotentialGenericDuplicate() {
        when(foodItemRepository.findBySourceKeyIn(any(), any(Sort.class))).thenReturn(List.of());
        when(foodItemRepository.findByCanonicalFoodKeyIn(any(), any(Sort.class))).thenReturn(List.of());
        when(foodItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = csv("""
                fdc_id,description,calories,protein,fat,carbohydrates_100g,market_region,preparation_state
                1001,Bananas raw,89,1.1,0.3,22.8,GLOBAL,RAW
                1002,Banana raw,97,0.7,0.3,23.0,GLOBAL,RAW
                """);

        FoodProductImportResultDto result = foodProductImportService.importCsv(
                file,
                "bulk@test.com",
                FoodProductImportMode.RAW_EXTERNAL,
                FoodProductImportFormat.USDA_FOODDATA
        );

        assertEquals(2, result.getSavedRows());

        ArgumentCaptor<List<FoodItemEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(foodItemRepository).saveAll(captor.capture());
        List<FoodItemEntity> imported = captor.getValue();

        assertEquals("USDA_FOODDATA:fdc:1001", imported.get(0).getSourceKey());
        assertEquals("USDA_FOODDATA:fdc:1002", imported.get(1).getSourceKey());
        assertEquals(
                "GLOBAL:GENERIC_INGREDIENT:RAW:banana",
                imported.get(0).getCanonicalFoodKey()
        );
        assertEquals(imported.get(0).getCanonicalFoodKey(), imported.get(1).getCanonicalFoodKey());
        assertEquals(1, result.getQualityWarningCounts().get("POTENTIAL_GENERIC_DUPLICATE"));
    }

    @Test
    void importCsv_whenCanonicalIdentityExistsUnderDifferentSource_reportsPotentialGenericDuplicate() {
        FoodItemEntity existing = new FoodItemEntity();
        existing.setId(99L);
        existing.setSourceKey("USDA_FOODDATA:fdc:1001");
        existing.setCanonicalFoodKey("GLOBAL:GENERIC_INGREDIENT:RAW:banana");

        when(foodItemRepository.findBySourceKeyIn(any(), any(Sort.class))).thenReturn(List.of());
        when(foodItemRepository.findByCanonicalFoodKeyIn(any(), any(Sort.class))).thenReturn(List.of(existing));
        when(foodItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = csv("""
                fdc_id,description,calories,protein,fat,carbohydrates_100g,market_region,preparation_state
                1002,Banana raw,97,0.7,0.3,23.0,GLOBAL,RAW
                """);

        FoodProductImportResultDto result = foodProductImportService.importCsv(
                file,
                "bulk@test.com",
                FoodProductImportMode.RAW_EXTERNAL,
                FoodProductImportFormat.USDA_FOODDATA
        );

        assertEquals(1, result.getSavedRows());
        assertEquals(1, result.getQualityWarningCounts().get("POTENTIAL_GENERIC_DUPLICATE"));
    }

    @Test
    void importCsv_repositoryPilotSampleFile_isValidForRegionalPilot() throws Exception {
        when(foodItemRepository.findByNormalizedBarcodeIn(any(), any(Sort.class))).thenReturn(List.of());
        when(foodItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        byte[] sample = Files.readAllBytes(Path.of("src/test/resources/open-food-facts-pilot-import.csv"));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "open-food-facts-pilot-import.csv",
                "text/csv",
                sample
        );

        FoodProductImportResultDto result = foodProductImportService.importCsv(file, "admin@test.com");

        assertEquals(9, result.getTotalRows());
        assertEquals(9, result.getInsertedRows());
        assertEquals(0, result.getSkippedRows());
        assertEquals(0, result.getReviewRequiredRows());

        ArgumentCaptor<List<FoodItemEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(foodItemRepository).saveAll(captor.capture());
        List<FoodItemEntity> savedProducts = captor.getValue();

        assertEquals(6, savedProducts.stream().filter(product -> product.getMarketRegion() == MarketRegion.UK_IE).count());
        assertEquals(3, savedProducts.stream().filter(product -> product.getMarketRegion() == MarketRegion.TR).count());
    }

    @Test
    void importCsv_ukOpenFoodFactsSmallSample_isValidRawExternalPilot() throws Exception {
        when(foodItemRepository.findByNormalizedBarcodeIn(any(), any(Sort.class))).thenReturn(List.of());
        when(foodItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        byte[] sample = Files.readAllBytes(Path.of("sample-data/food-products-uk-openfoodfacts-small.csv"));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "food-products-uk-openfoodfacts-small.csv",
                "text/csv",
                sample
        );

        FoodProductImportResultDto result = foodProductImportService.importCsv(
                file,
                "admin@test.com",
                FoodProductImportMode.RAW_EXTERNAL,
                FoodProductImportFormat.AUTO
        );

        assertEquals(10, result.getTotalRows());
        assertEquals(10, result.getInsertedRows());
        assertEquals(0, result.getSkippedRows());
        assertEquals(0, result.getReviewRequiredRows());
        assertEquals(10, result.getMarketRegionCounts().get("UK_IE"));
        assertEquals(10, result.getDataSourceCounts().get("OPEN_FOOD_FACTS"));

        ArgumentCaptor<List<FoodItemEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(foodItemRepository).saveAll(captor.capture());
        List<FoodItemEntity> savedProducts = captor.getValue();

        assertEquals(10, savedProducts.stream().filter(product -> product.getMarketRegion() == MarketRegion.UK_IE).count());
        assertEquals(10, savedProducts.stream().filter(product -> product.getVerificationStatus() == VerificationStatus.RAW_IMPORTED).count());
        assertEquals(10, savedProducts.stream().filter(product -> Boolean.TRUE.equals(product.getAutoApprovedForCatalog())).count());
        assertEquals(10, savedProducts.stream().filter(product -> product.getImageStatus() == ImageStatus.APPROVED).count());
    }

    @Test
    void importCsv_whenRawExternalMatchesCuratedProduct_preservesCuratedMetadata() {
        FoodItemEntity curated = new FoodItemEntity();
        curated.setId(10L);
        curated.setBarcode("3017620422003");
        curated.setNormalizedBarcode("3017620422003");
        curated.setName("Curated Nutella");
        curated.setDataSource(FoodDataSource.ADMIN_IMPORT);
        curated.setVerificationStatus(VerificationStatus.VERIFIED);
        curated.setImageStatus(ImageStatus.APPROVED);
        curated.setReviewedBy("admin@test.com");

        when(foodItemRepository.findByNormalizedBarcodeIn(any(), any(Sort.class))).thenReturn(List.of(curated));
        when(foodItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = csv("""
                barcode,name,calories
                3017620422003,Raw External Nutella,539
                """);

        foodProductImportService.importCsv(file, "bulk@test.com", FoodProductImportMode.RAW_EXTERNAL);

        ArgumentCaptor<List<FoodItemEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(foodItemRepository).saveAll(captor.capture());
        FoodItemEntity preserved = captor.getValue().get(0);

        assertEquals("Curated Nutella", preserved.getName());
        assertEquals(FoodDataSource.ADMIN_IMPORT, preserved.getDataSource());
        assertEquals(VerificationStatus.VERIFIED, preserved.getVerificationStatus());
        assertEquals(ImageStatus.APPROVED, preserved.getImageStatus());
        assertEquals("admin@test.com", preserved.getReviewedBy());
    }

    @Test
    void importCsv_acceptsTsvAndReportsDuplicateInputRows() {
        when(foodItemRepository.findByNormalizedBarcodeIn(any(), any(Sort.class))).thenReturn(List.of());
        when(foodItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = tsv("""
                barcode\tname\tcalories
                1234567890123\tFirst Product\t100
                1234567890123\tSecond Product\t120
                9999999999999\tThird Product\t80
                """);

        FoodProductImportResultDto result = foodProductImportService.importCsv(
                file,
                "admin@test.com",
                FoodProductImportMode.RAW_EXTERNAL
        );

        assertEquals(3, result.getTotalRows());
        assertEquals(2, result.getSavedRows());
        assertEquals(1, result.getSkippedRows());
        assertEquals(1, result.getDuplicateInputRows());
        assertEquals(2, result.getReviewRequiredRows());
        assertEquals("TSV", result.getImportFormat());
        assertEquals(2, result.getMissingMarketRegionRows());
        assertEquals(2, result.getMarketRegionCounts().get("GLOBAL"));

        ArgumentCaptor<List<FoodItemEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(foodItemRepository).saveAll(captor.capture());
        assertEquals(List.of("First Product", "Third Product"), captor.getValue().stream()
                .map(FoodItemEntity::getName)
                .toList());
    }

    @Test
    void importCsv_whenRegionIsMissingOrUnsupported_reportsAndFallsBackToGlobal() {
        when(foodItemRepository.findByNormalizedBarcodeIn(any(), any(Sort.class))).thenReturn(List.of());
        when(foodItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = csv("""
                barcode,name,calories,market_region
                1111111111111,No Region Product,100,
                2222222222222,Unsupported Region Product,120,Mars
                """);

        FoodProductImportResultDto result = foodProductImportService.importCsv(file, "admin@test.com");

        assertEquals(2, result.getSavedRows());
        assertEquals(1, result.getMissingMarketRegionRows());
        assertEquals(1, result.getUnsupportedMarketRegionRows());
        assertEquals(2, result.getMarketRegionCounts().get("GLOBAL"));
        assertEquals(1, result.getQualityWarningCounts().get("MISSING_REGION"));
        assertEquals(1, result.getQualityWarningCounts().get("UNSUPPORTED_REGION"));
        assertEquals(6, result.getWarnings().size());
        assertEquals("MISSING_REGION", result.getWarnings().get(0).getCode());
        assertEquals("1111111111111", result.getWarnings().get(0).getIdentifier());
        assertEquals("UNSUPPORTED_REGION", result.getWarnings().get(3).getCode());
        assertEquals(76, result.getImportQualityScore());

        ArgumentCaptor<List<FoodItemEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(foodItemRepository).saveAll(captor.capture());
        assertEquals(MarketRegion.GLOBAL, captor.getValue().get(0).getMarketRegion());
        assertEquals(MarketRegion.GLOBAL, captor.getValue().get(1).getMarketRegion());
    }

    @Test
    void importCsv_reportsQualityWarningCountsForSavedRows() {
        when(foodItemRepository.findByNormalizedBarcodeIn(any(), any(Sort.class))).thenReturn(List.of());
        when(foodItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = csv("""
                barcode,name,calories,protein,fat,carbs,market_region
                not-a-barcode,Incomplete Branded Product,,,,,TR
                1234567890123,No Image Product,120,3,4,5,TR
                """);

        FoodProductImportResultDto result = foodProductImportService.importCsv(file, "admin@test.com");

        assertEquals(2, result.getSavedRows());
        assertEquals(1, result.getQualityWarningCounts().get("INVALID_BARCODE_FORMAT"));
        assertEquals(1, result.getQualityWarningCounts().get("MISSING_CALORIES"));
        assertEquals(1, result.getQualityWarningCounts().get("MISSING_MACROS"));
        assertEquals(2, result.getQualityWarningCounts().get("MISSING_SERVING_SIZE"));
        assertEquals(5, result.getWarnings().size());
        assertEquals("MISSING_CALORIES", result.getWarnings().get(0).getCode());
        assertEquals("not-a-barcode", result.getWarnings().get(0).getIdentifier());
        assertEquals("INVALID_BARCODE_FORMAT", result.getWarnings().get(3).getCode());
        assertEquals(80, result.getImportQualityScore());
        verify(foodProductQualityIssueTracker).syncImportIssues(
                anyList(),
                eq("admin@test.com")
        );
    }

    @Test
    void importCsv_reportsGenericIngredientProductionReadinessWarnings() {
        when(foodItemRepository.findByNormalizedBarcodeIn(any(), any(Sort.class))).thenReturn(List.of());
        when(foodItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = csv("""
                catalog_type,name,calories,protein,fat,carbs,market_region,serving_size_grams,serving_unit
                GENERIC_INGREDIENT,Chicken Breast Raw,120,23,2,0,GLOBAL,100,g
                GENERIC_INGREDIENT,"Rice, Cooked",130,2.7,0.3,28,GLOBAL,100,g
                """);

        FoodProductImportResultDto result = foodProductImportService.importCsv(file, "admin@test.com");

        assertEquals(2, result.getSavedRows());
        assertEquals(2, result.getQualityWarningCounts().get("GENERIC_MISSING_PREPARATION_STATE"));
        assertEquals(1, result.getQualityWarningCounts().get("SUSPICIOUS_DISPLAY_NAME"));
    }
    private MockMultipartFile csv(String content) {
        return new MockMultipartFile(
                "file",
                "products.csv",
                "text/csv",
                content.stripIndent().getBytes(StandardCharsets.UTF_8)
        );
    }

    private MockMultipartFile tsv(String content) {
        return new MockMultipartFile(
                "file",
                "products.tsv",
                "text/tab-separated-values",
                content.stripIndent().getBytes(StandardCharsets.UTF_8)
        );
    }
}
