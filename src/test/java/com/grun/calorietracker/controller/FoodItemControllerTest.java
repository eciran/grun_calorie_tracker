package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.FoodProductDto;
import com.grun.calorietracker.dto.CustomFoodRequestDto;
import com.grun.calorietracker.dto.FoodProductSearchPageDto;
import com.grun.calorietracker.dto.FoodServingOptionDto;
import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.service.FoodItemService;
import com.grun.calorietracker.service.FoodServingOptionService;
import com.grun.calorietracker.service.UserProductLibraryService;
import com.grun.calorietracker.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FoodItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FoodItemService foodItemService;

    @MockBean
    private FoodServingOptionService foodServingOptionService;

    @MockBean
    private UserProductLibraryService userProductLibraryService;

    @MockBean
    private UserService userService;

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
        when(userService.findByEmail("user@test.com")).thenReturn(Optional.of(userWithRegion(MarketRegion.UK_IE)));

        mockMvc.perform(get("/api/v1/products/search")
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
    void searchProducts_whenRegionParamMissing_usesCurrentUserMarketRegion() throws Exception {
        FoodProductSearchPageDto page = new FoodProductSearchPageDto();
        page.setContent(List.of());
        page.setPage(0);
        page.setSize(25);
        page.setTotalElements(0L);
        page.setTotalPages(0);
        page.setFirst(true);
        page.setLast(true);

        when(userService.findByEmail("user@test.com")).thenReturn(Optional.of(userWithRegion(MarketRegion.TR)));
        when(foodItemService.searchFoodItems(
                argThat(criteria -> criteria != null && criteria.getMarketRegion() == MarketRegion.TR),
                eq(0),
                eq(25)
        )).thenReturn(page);

        mockMvc.perform(get("/api/v1/products/search")
                        .param("q", "sut"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    void searchProducts_whenRegionParamProvided_usesRequestedRegion() throws Exception {
        FoodProductSearchPageDto page = new FoodProductSearchPageDto();
        page.setContent(List.of());
        page.setPage(0);
        page.setSize(25);
        page.setTotalElements(0L);
        page.setTotalPages(0);
        page.setFirst(true);
        page.setLast(true);

        when(foodItemService.searchFoodItems(
                argThat(criteria -> criteria != null && criteria.getMarketRegion() == MarketRegion.UK_IE),
                eq(0),
                eq(25)
        )).thenReturn(page);

        mockMvc.perform(get("/api/v1/products/search")
                        .param("q", "milk")
                        .param("region", "UK_IE"))
                .andExpect(status().isOk());
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
        when(userService.findByEmail("user@test.com")).thenReturn(Optional.of(userWithRegion(MarketRegion.UK_IE)));

        mockMvc.perform(get("/api/v1/products/search")
                        .param("q", "nutella"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1L))
                .andExpect(jsonPath("$.content[0].barcode").value("3017620422003"))
                .andExpect(jsonPath("$.content[0].productName").value("Nutella"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    private UserEntity userWithRegion(MarketRegion marketRegion) {
        UserEntity user = new UserEntity();
        user.setEmail("user@test.com");
        user.setMarketRegion(marketRegion);
        return user;
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

        mockMvc.perform(get("/api/v1/products/barcode/3017620422003"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.barcode").value("3017620422003"))
                .andExpect(jsonPath("$.productName").value("Nutella"));
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    void getProductById_whenProductVisible_returnsProduct() throws Exception {
        FoodProductDto product = new FoodProductDto();
        product.setId(12L);
        product.setProductName("Greek yogurt");
        when(foodItemService.getFoodItemById(12L, "user@test.com")).thenReturn(product);

        mockMvc.perform(get("/api/v1/products/12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(12L))
                .andExpect(jsonPath("$.productName").value("Greek yogurt"));
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    void getServingOptions_returnsProductSpecificOptions() throws Exception {
        FoodServingOptionDto option = new FoodServingOptionDto();
        option.setId(5L);
        option.setFoodItemId(12L);
        option.setLabel("1 slice");
        option.setGramWeight(28.0);
        when(foodServingOptionService.getServingOptions(12L, "user@test.com")).thenReturn(List.of(option));

        mockMvc.perform(get("/api/v1/products/12/serving-options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5L))
                .andExpect(jsonPath("$[0].label").value("1 slice"))
                .andExpect(jsonPath("$[0].gramWeight").value(28.0));
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    void getRecentProducts_returnsCurrentUserProducts() throws Exception {
        FoodProductDto product = new FoodProductDto();
        product.setId(3L);
        product.setProductName("Greek yogurt");
        when(userProductLibraryService.getRecentProducts("user@test.com", 10)).thenReturn(List.of(product));

        mockMvc.perform(get("/api/v1/products/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(3L))
                .andExpect(jsonPath("$[0].productName").value("Greek yogurt"));
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    void addFavoriteProduct_returnsFavoritedProduct() throws Exception {
        FoodProductDto product = new FoodProductDto();
        product.setId(4L);
        product.setProductName("Banana");
        when(userProductLibraryService.addFavoriteProduct("user@test.com", 4L)).thenReturn(product);

        mockMvc.perform(post("/api/v1/products/4/favorite"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(4L))
                .andExpect(jsonPath("$.productName").value("Banana"));
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    void getFavoriteProducts_passesPagination() throws Exception {
        FoodProductDto product = new FoodProductDto();
        product.setId(4L);
        product.setProductName("Banana");
        when(userProductLibraryService.getFavoriteProducts("user@test.com", 2, 25)).thenReturn(List.of(product));

        mockMvc.perform(get("/api/v1/products/favorites")
                        .param("page", "2")
                        .param("size", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(4L));
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    void createCustomFood_returnsOwnedManualProduct() throws Exception {
        CustomFoodRequestDto request = new CustomFoodRequestDto();
        request.setName("Homemade soup");
        request.setCalories(80.0);
        FoodProductDto product = new FoodProductDto();
        product.setId(9L);
        product.setProductName("Homemade soup");
        product.setCustom(true);
        when(userProductLibraryService.createCustomFood(eq("user@test.com"), any(CustomFoodRequestDto.class)))
                .thenReturn(product);

        mockMvc.perform(post("/api/v1/products/custom")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(9L))
                .andExpect(jsonPath("$.custom").value(true));
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    void updateCustomFood_returnsOwnedManualProduct() throws Exception {
        CustomFoodRequestDto request = new CustomFoodRequestDto();
        request.setName("Updated soup");
        request.setCalories(90.0);
        FoodProductDto product = new FoodProductDto();
        product.setId(9L);
        product.setProductName("Updated soup");
        product.setCustom(true);
        when(userProductLibraryService.updateCustomFood(eq("user@test.com"), eq(9L), any(CustomFoodRequestDto.class)))
                .thenReturn(product);

        mockMvc.perform(put("/api/v1/products/custom/9")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productName").value("Updated soup"));
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    void deleteCustomFood_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/products/custom/9"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    void getCustomFoods_passesPagination() throws Exception {
        FoodProductDto product = new FoodProductDto();
        product.setId(9L);
        product.setProductName("Homemade soup");
        product.setCustom(true);
        when(userProductLibraryService.getCustomFoods("user@test.com", 1, 20)).thenReturn(List.of(product));

        mockMvc.perform(get("/api/v1/products/custom")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].custom").value(true));
    }
}

