package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.FoodProductSearchPageDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.service.FoodItemService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FoodItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FoodItemService foodItemService;

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    void searchProducts_whenNoProductsFound_returnsEmptyPageWithOkStatus() throws Exception {
        FoodProductSearchPageDto page = new FoodProductSearchPageDto();
        page.setContent(List.of());
        page.setPage(0);
        page.setSize(25);
        page.setTotalElements(0L);
        page.setTotalPages(0);
        page.setFirst(true);
        page.setLast(true);

        when(foodItemService.searchFoodItems(any(), eq(0), eq(25))).thenReturn(page);

        mockMvc.perform(get("/api/products/search")
                        .param("q", "unknown")
                        .param("page", "0")
                        .param("size", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(25))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    void searchProducts_whenProductsFound_returnsProductPage() throws Exception {
        FoodProductDto product = new FoodProductDto();
        product.setId(1L);
        product.setBarcode("3017620422003");
        product.setProductName("Nutella");

        FoodProductSearchPageDto page = new FoodProductSearchPageDto();
        page.setContent(List.of(product));
        page.setPage(0);
        page.setSize(25);
        page.setTotalElements(1L);
        page.setTotalPages(1);
        page.setFirst(true);
        page.setLast(true);

        when(foodItemService.searchFoodItems(any(), eq(0), eq(25))).thenReturn(page);

        mockMvc.perform(get("/api/products/search")
                        .param("q", "nutella"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1L))
                .andExpect(jsonPath("$.content[0].barcode").value("3017620422003"))
                .andExpect(jsonPath("$.content[0].productName").value("Nutella"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    void getProductByBarcode_whenProductFound_returnsProduct() throws Exception {
        FoodItemEntity foodItem = new FoodItemEntity();
        foodItem.setId(1L);
        foodItem.setBarcode("3017620422003");
        foodItem.setNormalizedBarcode("3017620422003");
        foodItem.setName("Nutella");

        when(foodItemService.getOrSaveFoodItemByBarcode("3017620422003")).thenReturn(foodItem);

        mockMvc.perform(get("/api/products/barcode/3017620422003"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.barcode").value("3017620422003"))
                .andExpect(jsonPath("$.productName").value("Nutella"));
    }
}
