package com.grun.calorietracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.FoodProductDuplicateGroupDto;
import com.grun.calorietracker.dto.FoodProductDuplicateGroupPageDto;
import com.grun.calorietracker.dto.FoodProductImportResultDto;
import com.grun.calorietracker.dto.FoodProductMergeRequestDto;
import com.grun.calorietracker.dto.FoodProductMergeResponseDto;
import com.grun.calorietracker.dto.FoodProductReviewAuditDto;
import com.grun.calorietracker.dto.FoodProductReviewAuditPageDto;
import com.grun.calorietracker.dto.FoodProductReviewPageDto;
import com.grun.calorietracker.dto.FoodProductReviewRequestDto;
import com.grun.calorietracker.enums.FoodProductReviewAuditAction;
import com.grun.calorietracker.enums.FoodProductImportMode;
import com.grun.calorietracker.enums.ImageSource;
import com.grun.calorietracker.enums.ImageStatus;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

        when(foodProductImportService.importCsv(any(), eq("admin@test.com"), eq(FoodProductImportMode.RAW_EXTERNAL))).thenReturn(response);

        mockMvc.perform(multipart("/api/v1/admin/products/import")
                        .file(file)
                        .param("importMode", "RAW_EXTERNAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(1))
                .andExpect(jsonPath("$.insertedRows").value(1))
                .andExpect(jsonPath("$.savedRows").value(1));

        verify(foodProductImportService).importCsv(any(), eq("admin@test.com"), eq(FoodProductImportMode.RAW_EXTERNAL));
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

