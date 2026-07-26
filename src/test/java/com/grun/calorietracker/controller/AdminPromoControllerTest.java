package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AdminPromoPageDto;
import com.grun.calorietracker.enums.*;
import com.grun.calorietracker.service.AdminPromoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AdminPromoControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean AdminPromoService promoService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void list_forwardsServerSideFiltersAndPagination() throws Exception {
        when(promoService.list("welcome", PromoStatus.ACTIVE, PromoType.INTRO_OFFER,
                PromoStore.REVENUECAT, 2, 20)).thenReturn(new AdminPromoPageDto(List.of(), 2, 20, 45, 3, false, true));

        mockMvc.perform(get("/api/v1/admin/promotions")
                        .param("search", "welcome").param("status", "ACTIVE")
                        .param("type", "INTRO_OFFER").param("store", "REVENUECAT")
                        .param("page", "2").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(45));

        verify(promoService).list("welcome", PromoStatus.ACTIVE, PromoType.INTRO_OFFER,
                PromoStore.REVENUECAT, 2, 20);
    }

    @Test
    @WithMockUser(roles = "USER")
    void list_rejectsNonAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/promotions")).andExpect(status().isForbidden());
    }
}
