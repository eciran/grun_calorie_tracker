package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.SubscriptionDto;
import com.grun.calorietracker.dto.SubscriptionFeatureAccessDto;
import com.grun.calorietracker.enums.BillingPeriod;
import com.grun.calorietracker.enums.SubscriptionPlan;
import com.grun.calorietracker.enums.SubscriptionStatus;
import com.grun.calorietracker.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SubscriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SubscriptionService subscriptionService;

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void getCurrentSubscription_returnsQuotaState() throws Exception {
        SubscriptionDto dto = new SubscriptionDto();
        dto.setPlanType(SubscriptionPlan.PLUS);
        dto.setStatus(SubscriptionStatus.ACTIVE);
        dto.setBillingPeriod(BillingPeriod.MONTHLY);
        dto.setAiMonthlyQuota(15);
        dto.setAiUsedThisPeriod(5);
        dto.setAiRemainingThisPeriod(10);
        dto.setAutoRenew(true);
        when(subscriptionService.getCurrentSubscription("user@example.com")).thenReturn(dto);

        mockMvc.perform(get("/api/v1/subscriptions/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("PLUS"))
                .andExpect(jsonPath("$.planType").value("PLUS"))
                .andExpect(jsonPath("$.aiRemainingThisPeriod").value(10));
    }

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void getFeatureAccess_returnsResolvedFeatureMatrix() throws Exception {
        SubscriptionFeatureAccessDto dto = new SubscriptionFeatureAccessDto();
        dto.setPlanType(SubscriptionPlan.PRO);
        dto.setActiveEntitlement(true);
        dto.setAiWorkoutPlanner(true);
        dto.setAdvancedAnalytics(true);
        dto.setAdFree(true);
        dto.setCustomFoodLibrary(true);
        dto.setAiMonthlyQuota(100);
        dto.setAiRemainingThisPeriod(88);
        when(subscriptionService.getFeatureAccess("user@example.com")).thenReturn(dto);

        mockMvc.perform(get("/api/v1/subscriptions/me/features"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("PRO"))
                .andExpect(jsonPath("$.planType").value("PRO"))
                .andExpect(jsonPath("$.aiWorkoutPlanner").value(true))
                .andExpect(jsonPath("$.adFree").value(true))
                .andExpect(jsonPath("$.aiRemainingThisPeriod").value(88));
    }

}
