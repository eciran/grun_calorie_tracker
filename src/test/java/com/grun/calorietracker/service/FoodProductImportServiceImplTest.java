package com.grun.calorietracker.service;

import com.grun.calorietracker.dto.FoodProductImportResultDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodDataSource;
import com.grun.calorietracker.enums.FoodProductImportMode;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.repository.FoodItemRepository;
import com.grun.calorietracker.service.impl.FoodProductImportServiceImpl;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FoodProductImportServiceImplTest {

    @Mock
    private FoodItemRepository foodItemRepository;

    private FoodProductImportServiceImpl foodProductImportService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        foodProductImportService = new FoodProductImportServiceImpl(foodItemRepository);
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
        assertEquals(1, result.getReviewRequiredRows());
        assertEquals("CSV", result.getImportFormat());
        assertEquals(0, result.getMissingMarketRegionRows());
        assertEquals(0, result.getUnsupportedMarketRegionRows());
        assertEquals(1, result.getMarketRegionCounts().get("UK_IE"));
        assertEquals(1, result.getMarketRegionCounts().get("TR"));

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
    void importCsv_acceptsExplicitNonBarcodeCatalogRows() {
        when(foodItemRepository.findBySourceKeyIn(any(), any(Sort.class))).thenReturn(List.of());
        when(foodItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = csv("""
                catalog_type,name,calories,protein,fat,carbs,market_region,serving_size_grams,serving_unit
                LOCAL_DISH,Mercimek Corbasi,92,5.8,2.4,12.1,TR,250,bowl
                GENERIC_INGREDIENT,Rolled Oats,389,16.9,6.9,66.3,UK_IE,100,g
                """);

        FoodProductImportResultDto result = foodProductImportService.importCsv(file, "admin@test.com");

        assertEquals(2, result.getTotalRows());
        assertEquals(2, result.getSavedRows());
        assertEquals(0, result.getSkippedRows());

        ArgumentCaptor<List<FoodItemEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(foodItemRepository).saveAll(captor.capture());
        List<FoodItemEntity> savedProducts = captor.getValue();

        assertEquals(FoodCatalogType.LOCAL_DISH, savedProducts.get(0).getCatalogType());
        assertEquals("TR:LOCAL_DISH:mercimek_corbasi", savedProducts.get(0).getSourceKey());
        assertEquals(null, savedProducts.get(0).getBarcode());
        assertEquals(FoodCatalogType.GENERIC_INGREDIENT, savedProducts.get(1).getCatalogType());
        assertEquals("UK_IE:GENERIC_INGREDIENT:rolled_oats", savedProducts.get(1).getSourceKey());
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
        assertEquals(ImageStatus.NEEDS_REVIEW, imported.getImageStatus());
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
        assertEquals(ImageStatus.NEEDS_REVIEW, savedProducts.get(0).getImageStatus());
        assertEquals(FoodDataSource.OPEN_FOOD_FACTS, savedProducts.get(0).getDataSource());
    }

    @Test
    void importCsv_repositoryPilotSampleFile_isValidForRegionalPilot() throws Exception {
        when(foodItemRepository.findByNormalizedBarcodeIn(any(), any(Sort.class))).thenReturn(List.of());
        when(foodItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        byte[] sample = Files.readAllBytes(Path.of("docs/samples/open-food-facts-pilot-import.csv"));
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
        assertEquals(3, result.getSavedRows());
        assertEquals(1, result.getDuplicateInputRows());
        assertEquals(3, result.getReviewRequiredRows());
        assertEquals("TSV", result.getImportFormat());
        assertEquals(3, result.getMissingMarketRegionRows());
        assertEquals(3, result.getMarketRegionCounts().get("GLOBAL"));
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

        ArgumentCaptor<List<FoodItemEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(foodItemRepository).saveAll(captor.capture());
        assertEquals(MarketRegion.GLOBAL, captor.getValue().get(0).getMarketRegion());
        assertEquals(MarketRegion.GLOBAL, captor.getValue().get(1).getMarketRegion());
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
