package com.grun.calorietracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.AdminAiQuotaGrantRequestDto;
import com.grun.calorietracker.dto.AdminSubscriptionUpdateRequestDto;
import com.grun.calorietracker.dto.SubscriptionDto;
import com.grun.calorietracker.enums.BillingPeriod;
import com.grun.calorietracker.enums.SubscriptionPlan;
import com.grun.calorietracker.enums.SubscriptionStatus;
import com.grun.calorietracker.service.AdminAuditService;
import com.grun.calorietracker.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminSubscriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SubscriptionService subscriptionService;

    @MockBean
    private AdminAuditService adminAuditService;

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void updateUserSubscription_whenAdmin_returnsSubscriptionState() throws Exception {
        AdminSubscriptionUpdateRequestDto request = new AdminSubscriptionUpdateRequestDto();
        request.setPlanType(SubscriptionPlan.PRO);
        request.setStatus(SubscriptionStatus.ACTIVE);
        request.setBillingPeriod(BillingPeriod.YEARLY);
        request.setAiMonthlyQuota(100);
        request.setAiUsedThisPeriod(12);
        request.setAutoRenew(true);

        SubscriptionDto response = new SubscriptionDto();
        response.setPlanType(SubscriptionPlan.PRO);
        response.setStatus(SubscriptionStatus.ACTIVE);
        response.setBillingPeriod(BillingPeriod.YEARLY);
        response.setAiMonthlyQuota(100);
        response.setAiUsedThisPeriod(12);
        response.setAiRemainingThisPeriod(88);
        response.setAutoRenew(true);

        when(subscriptionService.updateUserSubscription(eq(1L), any(AdminSubscriptionUpdateRequestDto.class)))
                .thenReturn(response);

        mockMvc.perform(patch("/api/v1/admin/subscriptions/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planType").value("PRO"))
                .andExpect(jsonPath("$.billingPeriod").value("YEARLY"))
                .andExpect(jsonPath("$.aiRemainingThisPeriod").value(88));

        verify(subscriptionService).updateUserSubscription(eq(1L), any(AdminSubscriptionUpdateRequestDto.class));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void resetUserAiQuota_whenAdmin_returnsResetQuotaState() throws Exception {
        SubscriptionDto response = new SubscriptionDto();
        response.setPlanType(SubscriptionPlan.PLUS);
        response.setStatus(SubscriptionStatus.ACTIVE);
        response.setBillingPeriod(BillingPeriod.MONTHLY);
        response.setAiMonthlyQuota(15);
        response.setAiUsedThisPeriod(0);
        response.setAiRemainingThisPeriod(15);

        when(subscriptionService.resetUserAiQuota(1L)).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/subscriptions/users/1/ai-quota/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planType").value("PLUS"))
                .andExpect(jsonPath("$.aiUsedThisPeriod").value(0))
                .andExpect(jsonPath("$.aiRemainingThisPeriod").value(15));

        verify(subscriptionService).resetUserAiQuota(1L);
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void grantAiAddonQuota_whenAdmin_returnsExpandedQuotaState() throws Exception {
        AdminAiQuotaGrantRequestDto request = new AdminAiQuotaGrantRequestDto();
        request.setAmount(50);
        request.setValidityDays(7);

        SubscriptionDto response = new SubscriptionDto();
        response.setPlanType(SubscriptionPlan.PRO);
        response.setStatus(SubscriptionStatus.ACTIVE);
        response.setBillingPeriod(BillingPeriod.MONTHLY);
        response.setAiMonthlyQuota(100);
        response.setAiAddonQuota(50);
        response.setAiTotalQuotaThisPeriod(150);
        response.setAiUsedThisPeriod(100);
        response.setAiBaseRemainingThisPeriod(0);
        response.setAiAddonRemainingThisPeriod(50);
        response.setAiRemainingThisPeriod(50);

        when(subscriptionService.grantAiAddonQuota(1L, 50, 7)).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/subscriptions/users/1/ai-quota/addon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planType").value("PRO"))
                .andExpect(jsonPath("$.aiMonthlyQuota").value(100))
                .andExpect(jsonPath("$.aiAddonQuota").value(50))
                .andExpect(jsonPath("$.aiTotalQuotaThisPeriod").value(150))
                .andExpect(jsonPath("$.aiRemainingThisPeriod").value(50));

        verify(subscriptionService).grantAiAddonQuota(1L, 50, 7);
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    void updateUserSubscription_whenNotAdmin_returnsForbidden() throws Exception {
        AdminSubscriptionUpdateRequestDto request = new AdminSubscriptionUpdateRequestDto();
        request.setPlanType(SubscriptionPlan.PLUS);
        request.setStatus(SubscriptionStatus.ACTIVE);
        request.setBillingPeriod(BillingPeriod.MONTHLY);

        mockMvc.perform(patch("/api/v1/admin/subscriptions/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void updateUserSubscription_whenRequiredFieldsMissing_returnsBadRequest() throws Exception {
        AdminSubscriptionUpdateRequestDto request = new AdminSubscriptionUpdateRequestDto();

        mockMvc.perform(patch("/api/v1/admin/subscriptions/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation error"));
    }
}
