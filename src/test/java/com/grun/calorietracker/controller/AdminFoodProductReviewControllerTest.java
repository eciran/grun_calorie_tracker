package com.grun.calorietracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.FoodProductDuplicateGroupDto;
import com.grun.calorietracker.dto.FoodProductDuplicateGroupPageDto;
import com.grun.calorietracker.dto.FoodProductImportResultDto;
import com.grun.calorietracker.dto.FoodProductMergeRequestDto;
import com.grun.calorietracker.dto.FoodProductMergeResponseDto;
import com.grun.calorietracker.dto.FoodProductNutritionCorrectionImportResultDto;
import com.grun.calorietracker.dto.FoodProductQualityIssueBackfillResultDto;
import com.grun.calorietracker.dto.FoodProductQualityIssueDto;
import com.grun.calorietracker.dto.FoodProductReviewAuditDto;
import com.grun.calorietracker.dto.FoodProductReviewAuditPageDto;
import com.grun.calorietracker.dto.FoodProductReviewPageDto;
import com.grun.calorietracker.dto.FoodProductReviewRequestDto;
import com.grun.calorietracker.dto.FoodSearchAliasDto;
import com.grun.calorietracker.dto.FoodSearchAliasRequestDto;
import com.grun.calorietracker.enums.FoodCatalogType;
import com.grun.calorietracker.enums.FoodDataSource;
import com.grun.calorietracker.enums.FoodProductImportFormat;
import com.grun.calorietracker.enums.FoodProductReviewAuditAction;
import com.grun.calorietracker.enums.FoodSearchAliasType;
import com.grun.calorietracker.enums.FoodProductImportMode;
import com.grun.calorietracker.enums.FoodProductQualityIssue;
import com.grun.calorietracker.enums.ImageSource;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.service.FoodProductImportService;
import com.grun.calorietracker.service.FoodProductReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminFoodProductReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FoodProductReviewService foodProductReviewService;

    @MockBean
    private FoodProductImportService foodProductImportService;

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void importProducts_whenAdmin_returnsImportResult() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "products.csv",
                "text/csv",
                "barcode,name\n3017620422003,Nutella\n".getBytes()
        );
        FoodProductImportResultDto response = new FoodProductImportResultDto(
                1,
                1,
                0,
                0,
                1,
                List.of()
        );

        when(foodProductImportService.importCsv(any(), eq("admin@test.com"), eq(FoodProductImportMode.RAW_EXTERNAL), eq(FoodProductImportFormat.OPEN_FOOD_FACTS_EXPORT))).thenReturn(response);

        mockMvc.perform(multipart("/api/v1/admin/products/import")
                        .file(file)
                        .param("importMode", "RAW_EXTERNAL")
                        .param("importFormat", "OPEN_FOOD_FACTS_EXPORT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(1))
                .andExpect(jsonPath("$.insertedRows").value(1))
                .andExpect(jsonPath("$.savedRows").value(1));

        verify(foodProductImportService).importCsv(any(), eq("admin@test.com"), eq(FoodProductImportMode.RAW_EXTERNAL), eq(FoodProductImportFormat.OPEN_FOOD_FACTS_EXPORT));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void getProductsForReview_whenAdmin_returnsProducts() throws Exception {
        FoodProductDto product = new FoodProductDto();
        product.setId(1L);
        product.setProductName("Imported Product");
        product.setVerificationStatus(VerificationStatus.RAW_IMPORTED);
        product.setImageStatus(ImageStatus.NEEDS_REVIEW);

        FoodProductReviewPageDto page = new FoodProductReviewPageDto();
        page.setContent(List.of(product));
        page.setPage(0);
        page.setSize(25);
        page.setTotalElements(1L);
        page.setTotalPages(1);
        page.setFirst(true);
        page.setLast(true);

        when(foodProductReviewService.getProductsForReview(
                VerificationStatus.RAW_IMPORTED,
                ImageStatus.NEEDS_REVIEW,
                null,
                null,
                null,
                null,
                null,
                0,
                25
        )).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/products/review")
                        .param("verificationStatus", "RAW_IMPORTED")
                        .param("imageStatus", "NEEDS_REVIEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1L))
                .andExpect(jsonPath("$.content[0].productName").value("Imported Product"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(25))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void getProductsForReview_whenRegionProvided_passesRegionFilter() throws Exception {
        FoodProductReviewPageDto page = new FoodProductReviewPageDto();
        page.setContent(List.of());
        page.setPage(0);
        page.setSize(25);
        page.setTotalElements(0L);
        page.setTotalPages(0);
        page.setFirst(true);
        page.setLast(true);

        when(foodProductReviewService.getProductsForReview(
                VerificationStatus.RAW_IMPORTED,
                ImageStatus.NEEDS_REVIEW,
                MarketRegion.TR,
                null,
                null,
                null,
                null,
                0,
                25
        )).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/products/review")
                        .param("verificationStatus", "RAW_IMPORTED")
                        .param("imageStatus", "NEEDS_REVIEW")
                        .param("region", "TR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }
    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void getProductSearchAliases_whenAdmin_returnsAliases() throws Exception {
        FoodSearchAliasDto alias = new FoodSearchAliasDto(
                10L,
                1L,
                "yarım yağlı süt",
                "yarim yagli sut",
                PreferredLanguage.TR,
                FoodSearchAliasType.TRANSLATION,
                "admin",
                true,
                "2026-06-19T13:45:00"
        );
        when(foodProductReviewService.getProductSearchAliases(1L, true)).thenReturn(List.of(alias));

        mockMvc.perform(get("/api/v1/admin/products/1/search-aliases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10L))
                .andExpect(jsonPath("$[0].alias").value("yarım yağlı süt"))
                .andExpect(jsonPath("$[0].normalizedAlias").value("yarim yagli sut"));

        verify(foodProductReviewService).getProductSearchAliases(1L, true);
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void addProductSearchAlias_whenAdmin_returnsAlias() throws Exception {
        FoodSearchAliasRequestDto request = new FoodSearchAliasRequestDto();
        request.setAlias("yarım yağlı süt");
        request.setLanguage(PreferredLanguage.TR);
        request.setAliasType(FoodSearchAliasType.TRANSLATION);

        FoodSearchAliasDto response = new FoodSearchAliasDto(
                10L,
                1L,
                "yarım yağlı süt",
                "yarim yagli sut",
                PreferredLanguage.TR,
                FoodSearchAliasType.TRANSLATION,
                "admin",
                true,
                "2026-06-19T13:45:00"
        );
        when(foodProductReviewService.addProductSearchAlias(eq(1L), any(FoodSearchAliasRequestDto.class), eq("admin@test.com")))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/products/1/search-aliases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10L))
                .andExpect(jsonPath("$.alias").value("yarım yağlı süt"))
                .andExpect(jsonPath("$.language").value("TR"));

        verify(foodProductReviewService).addProductSearchAlias(eq(1L), any(FoodSearchAliasRequestDto.class), eq("admin@test.com"));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void updateProductSearchAliasStatus_whenAdmin_returnsAlias() throws Exception {
        FoodSearchAliasDto response = new FoodSearchAliasDto(
                10L,
                1L,
                "yarım yağlı süt",
                "yarim yagli sut",
                PreferredLanguage.TR,
                FoodSearchAliasType.TRANSLATION,
                "admin",
                false,
                "2026-06-19T13:45:00"
        );
        when(foodProductReviewService.updateProductSearchAliasStatus(1L, 10L, false, "admin@test.com"))
                .thenReturn(response);

        mockMvc.perform(patch("/api/v1/admin/products/1/search-aliases/10/status")
                        .param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10L))
                .andExpect(jsonPath("$.active").value(false));

        verify(foodProductReviewService).updateProductSearchAliasStatus(1L, 10L, false, "admin@test.com");
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void backfillQualityIssues_whenAdmin_returnsBackfillResult() throws Exception {
        FoodProductQualityIssueBackfillResultDto response =
                new FoodProductQualityIssueBackfillResultDto(1250L, 3, 500);

        when(foodProductReviewService.backfillQualityIssues(500, "admin@test.com")).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/products/quality-issues/backfill")
                        .param("pageSize", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scannedProducts").value(1250L))
                .andExpect(jsonPath("$.processedBatches").value(3))
                .andExpect(jsonPath("$.pageSize").value(500));

        verify(foodProductReviewService).backfillQualityIssues(500, "admin@test.com");
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void importNutritionCorrections_whenAdmin_returnsCorrectionResult() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "corrections.csv",
                "text/csv",
                "barcode,calories\n3017620422003,539\n".getBytes()
        );
        FoodProductNutritionCorrectionImportResultDto response =
                new FoodProductNutritionCorrectionImportResultDto(1, 1, 0, List.of());

        when(foodProductReviewService.importNutritionCorrections(any(), eq("admin@test.com"), eq(false), eq(false))).thenReturn(response);

        mockMvc.perform(multipart("/api/v1/admin/products/nutrition-corrections/import")
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(1))
                .andExpect(jsonPath("$.updatedRows").value(1))
                .andExpect(jsonPath("$.skippedRows").value(0));

        verify(foodProductReviewService).importNutritionCorrections(any(), eq("admin@test.com"), eq(false), eq(false));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void exportProductsForReview_whenAdmin_returnsCsvForCurrentFilter() throws Exception {
        byte[] csv = "id,product_name\n1,Almond\n".getBytes();

        when(foodProductReviewService.exportProductsForReview(
                VerificationStatus.RAW_IMPORTED,
                ImageStatus.NEEDS_REVIEW,
                MarketRegion.UK_IE,
                FoodCatalogType.BRANDED_PRODUCT,
                FoodDataSource.OPEN_FOOD_FACTS,
                FoodProductQualityIssue.MISSING_CALORIES,
                "almond",
                1000
        )).thenReturn(csv);

        mockMvc.perform(get("/api/v1/admin/products/review/export")
                        .param("verificationStatus", "RAW_IMPORTED")
                        .param("imageStatus", "NEEDS_REVIEW")
                        .param("region", "UK_IE")
                        .param("catalogType", "BRANDED_PRODUCT")
                        .param("dataSource", "OPEN_FOOD_FACTS")
                        .param("qualityIssue", "MISSING_CALORIES")
                        .param("query", "almond")
                        .param("limit", "1000"))
                .andExpect(status().isOk())
                .andExpect(content().string("id,product_name\n1,Almond\n"));

        verify(foodProductReviewService).exportProductsForReview(
                VerificationStatus.RAW_IMPORTED,
                ImageStatus.NEEDS_REVIEW,
                MarketRegion.UK_IE,
                FoodCatalogType.BRANDED_PRODUCT,
                FoodDataSource.OPEN_FOOD_FACTS,
                FoodProductQualityIssue.MISSING_CALORIES,
                "almond",
                1000
        );
    }
    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void getProductQualityIssues_whenAdmin_returnsIssues() throws Exception {
        FoodProductQualityIssueDto issue = new FoodProductQualityIssueDto(
                7L,
                1L,
                FoodProductQualityIssue.SUSPICIOUS_CALORIES,
                "3017620422003",
                "Calories are suspiciously high.",
                false,
                LocalDateTime.of(2026, 6, 3, 10, 0),
                LocalDateTime.of(2026, 6, 3, 11, 0),
                null,
                null
        );

        when(foodProductReviewService.getProductQualityIssues(1L, false)).thenReturn(List.of(issue));

        mockMvc.perform(get("/api/v1/admin/products/1/quality-issues")
                        .param("activeOnly", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].foodItemId").value(1L))
                .andExpect(jsonPath("$[0].issueType").value("SUSPICIOUS_CALORIES"))
                .andExpect(jsonPath("$[0].resolved").value(false));

        verify(foodProductReviewService).getProductQualityIssues(1L, false);
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void getProductsForReview_whenCatalogTypeProvided_passesCatalogTypeFilter() throws Exception {
        FoodProductReviewPageDto page = new FoodProductReviewPageDto();
        page.setContent(List.of());
        page.setPage(0);
        page.setSize(25);
        page.setTotalElements(0L);
        page.setTotalPages(0);
        page.setFirst(true);
        page.setLast(true);

        when(foodProductReviewService.getProductsForReview(
                VerificationStatus.RAW_IMPORTED,
                ImageStatus.NEEDS_REVIEW,
                MarketRegion.TR,
                FoodCatalogType.LOCAL_DISH,
                null,
                null,
                null,
                0,
                25
        )).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/products/review")
                        .param("verificationStatus", "RAW_IMPORTED")
                        .param("imageStatus", "NEEDS_REVIEW")
                        .param("region", "TR")
                        .param("catalogType", "LOCAL_DISH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void getProductsForReview_whenDataSourceProvided_passesDataSourceFilter() throws Exception {
        FoodProductReviewPageDto page = new FoodProductReviewPageDto();
        page.setContent(List.of());
        page.setPage(0);
        page.setSize(25);
        page.setTotalElements(0L);
        page.setTotalPages(0);
        page.setFirst(true);
        page.setLast(true);

        when(foodProductReviewService.getProductsForReview(
                VerificationStatus.RAW_IMPORTED,
                ImageStatus.NEEDS_REVIEW,
                MarketRegion.TR,
                FoodCatalogType.BRANDED_PRODUCT,
                FoodDataSource.OPEN_FOOD_FACTS,
                null,
                null,
                0,
                25
        )).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/products/review")
                        .param("verificationStatus", "RAW_IMPORTED")
                        .param("imageStatus", "NEEDS_REVIEW")
                        .param("region", "TR")
                        .param("catalogType", "BRANDED_PRODUCT")
                        .param("dataSource", "OPEN_FOOD_FACTS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void getProductsForReview_whenQualityIssueProvided_passesQualityIssueFilter() throws Exception {
        FoodProductReviewPageDto page = new FoodProductReviewPageDto();
        page.setContent(List.of());
        page.setPage(0);
        page.setSize(25);
        page.setTotalElements(0L);
        page.setTotalPages(0);
        page.setFirst(true);
        page.setLast(true);

        when(foodProductReviewService.getProductsForReview(
                VerificationStatus.RAW_IMPORTED,
                ImageStatus.NEEDS_REVIEW,
                MarketRegion.TR,
                FoodCatalogType.BRANDED_PRODUCT,
                FoodDataSource.OPEN_FOOD_FACTS,
                FoodProductQualityIssue.MISSING_CALORIES,
                null,
                0,
                25
        )).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/products/review")
                        .param("verificationStatus", "RAW_IMPORTED")
                        .param("imageStatus", "NEEDS_REVIEW")
                        .param("region", "TR")
                        .param("catalogType", "BRANDED_PRODUCT")
                        .param("dataSource", "OPEN_FOOD_FACTS")
                        .param("qualityIssue", "MISSING_CALORIES"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    void getProductsForReview_whenNotAdmin_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/products/review"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void getDuplicateProductGroups_whenAdmin_returnsGroups() throws Exception {
        FoodProductDto firstProduct = new FoodProductDto();
        firstProduct.setId(1L);
        firstProduct.setProductName("Nutella");
        firstProduct.setNormalizedBarcode("3017620422003");

        FoodProductDto secondProduct = new FoodProductDto();
        secondProduct.setId(2L);
        secondProduct.setProductName("Nutella Duplicate");
        secondProduct.setNormalizedBarcode("3017620422003");

        FoodProductDuplicateGroupDto group = new FoodProductDuplicateGroupDto(
                "3017620422003",
                2,
                List.of(firstProduct, secondProduct)
        );
        FoodProductDuplicateGroupPageDto page = new FoodProductDuplicateGroupPageDto(
                List.of(group),
                0,
                25,
                1L,
                1,
                true,
                true
        );

        when(foodProductReviewService.getDuplicateProductGroups(0, 25)).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/products/duplicates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].normalizedBarcode").value("3017620422003"))
                .andExpect(jsonPath("$.content[0].productCount").value(2))
                .andExpect(jsonPath("$.content[0].products[0].productName").value("Nutella"));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void mergeDuplicateProducts_whenAdmin_returnsMergeResult() throws Exception {
        FoodProductMergeRequestDto request = new FoodProductMergeRequestDto(1L, List.of(2L));

        FoodProductDto targetProduct = new FoodProductDto();
        targetProduct.setId(1L);
        targetProduct.setProductName("Nutella");
        targetProduct.setNormalizedBarcode("3017620422003");

        FoodProductMergeResponseDto response = new FoodProductMergeResponseDto(
                targetProduct,
                List.of(2L),
                4,
                2,
                1
        );

        when(foodProductReviewService.mergeDuplicateProducts(any(FoodProductMergeRequestDto.class), eq("admin@test.com")))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/products/duplicates/merge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetProduct.id").value(1L))
                .andExpect(jsonPath("$.mergedProductIds[0]").value(2L))
                .andExpect(jsonPath("$.reassignedFoodLogCount").value(4))
                .andExpect(jsonPath("$.removedConflictingFavoriteCount").value(1));

        verify(foodProductReviewService).mergeDuplicateProducts(any(FoodProductMergeRequestDto.class), eq("admin@test.com"));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void updateProductReview_whenAdmin_returnsUpdatedProduct() throws Exception {
        FoodProductReviewRequestDto request = new FoodProductReviewRequestDto();
        request.setProductName("Verified Product");
        request.setDisplayImageUrl("https://cdn.grun.app/products/1.jpg");
        request.setVerificationStatus(VerificationStatus.VERIFIED);
        request.setImageSource(ImageSource.ADMIN_UPLOAD);
        request.setImageStatus(ImageStatus.APPROVED);

        FoodProductDto response = new FoodProductDto();
        response.setId(1L);
        response.setProductName("Verified Product");
        response.setDisplayImageUrl("https://cdn.grun.app/products/1.jpg");
        response.setVerificationStatus(VerificationStatus.VERIFIED);
        response.setImageSource(ImageSource.ADMIN_UPLOAD);
        response.setImageStatus(ImageStatus.APPROVED);

        when(foodProductReviewService.updateProductReview(eq(1L), any(FoodProductReviewRequestDto.class), eq("admin@test.com")))
                .thenReturn(response);

        mockMvc.perform(patch("/api/v1/admin/products/1/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.productName").value("Verified Product"))
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.imageStatus").value("APPROVED"));

        verify(foodProductReviewService).updateProductReview(eq(1L), any(FoodProductReviewRequestDto.class), eq("admin@test.com"));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void getProductReviewAudits_whenAdmin_returnsAuditEntries() throws Exception {
        FoodProductReviewAuditDto audit = new FoodProductReviewAuditDto();
        audit.setId(10L);
        audit.setFoodItemId(1L);
        audit.setReviewedBy("admin@test.com");
        audit.setActionType(FoodProductReviewAuditAction.STATUS_CHANGE);
        audit.setFieldName("verificationStatus");
        audit.setOldValue("RAW_IMPORTED");
        audit.setNewValue("VERIFIED");

        FoodProductReviewAuditPageDto page = new FoodProductReviewAuditPageDto();
        page.setContent(List.of(audit));
        page.setPage(0);
        page.setSize(25);
        page.setTotalElements(1L);
        page.setTotalPages(1);
        page.setFirst(true);
        page.setLast(true);

        when(foodProductReviewService.getProductReviewAudits(1L, 0, 25)).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/products/1/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10L))
                .andExpect(jsonPath("$.content[0].foodItemId").value(1L))
                .andExpect(jsonPath("$.content[0].reviewedBy").value("admin@test.com"))
                .andExpect(jsonPath("$.content[0].actionType").value("STATUS_CHANGE"))
                .andExpect(jsonPath("$.content[0].fieldName").value("verificationStatus"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void openApi_adminReviewDocumentsReviewNoteAndAuditSchema() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/admin/products/{id}/review'].patch.requestBody.content['application/json'].examples['Reject low quality image'].value.reviewNote")
                        .value("Image is blurry and product label is unreadable."))
                .andExpect(jsonPath("$.paths['/api/v1/admin/products/{id}/review'].patch.requestBody.content['application/json'].examples['Approve curated product and image'].value.displayImageUrl")
                        .value("https://cdn.grun.app/products/3017620422003.jpg"))
                .andExpect(jsonPath("$.paths['/api/v1/admin/products/{id}/audit'].get.responses['200'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/FoodProductReviewAuditPageDto"));
    }
}

