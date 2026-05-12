package com.grun.calorietracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.FoodProductReviewPageDto;
import com.grun.calorietracker.dto.FoodProductReviewRequestDto;
import com.grun.calorietracker.enums.ImageSource;
import com.grun.calorietracker.enums.ImageStatus;
import com.grun.calorietracker.enums.VerificationStatus;
import com.grun.calorietracker.service.FoodProductReviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

        mockMvc.perform(get("/api/admin/products/review")
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
        mockMvc.perform(get("/api/admin/products/review"))
                .andExpect(status().isForbidden());
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

        when(foodProductReviewService.updateProductReview(eq(1L), any(FoodProductReviewRequestDto.class)))
                .thenReturn(response);

        mockMvc.perform(patch("/api/admin/products/1/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.productName").value("Verified Product"))
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.imageStatus").value("APPROVED"));
    }
}
