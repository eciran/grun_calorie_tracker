package com.grun.calorietracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.AdminAiQuotaGrantRequestDto;
import com.grun.calorietracker.dto.AdminSubscriptionUpdateRequestDto;
import com.grun.calorietracker.dto.SubscriptionDto;
import com.grun.calorietracker.dto.SubscriptionFeatureAccessDto;
import com.grun.calorietracker.enums.AdminAuditActionType;
import com.grun.calorietracker.enums.AdminAuditTargetType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    void getUserSubscription_whenAdmin_returnsRenewalAndQuotaState() throws Exception {
        SubscriptionDto response = new SubscriptionDto();
        response.setPlanType(SubscriptionPlan.PLUS);
        response.setAutoRenew(true);
        response.setEndDate(java.time.LocalDate.of(2026, 8, 18));
        response.setQuotaResetDate(java.time.LocalDate.of(2026, 8, 19));
        response.setAiMonthlyQuota(50);
        response.setAiAddonQuota(1);
        response.setAiAddonQuotaExpiresAt(java.time.LocalDate.of(2026, 7, 18));

        when(subscriptionService.getUserSubscriptionForAdmin(1L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/subscriptions/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planType").value("PLUS"))
                .andExpect(jsonPath("$.autoRenew").value(true))
                .andExpect(jsonPath("$.aiMonthlyQuota").value(50))
                .andExpect(jsonPath("$.aiAddonQuota").value(1))
                .andExpect(jsonPath("$.aiAddonQuotaExpiresAt").value("2026-07-18"));

        verify(subscriptionService).getUserSubscriptionForAdmin(1L);
    }
    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void getUserFeatureAccess_whenAdmin_returnsResolvedAccess() throws Exception {
        SubscriptionFeatureAccessDto response = new SubscriptionFeatureAccessDto();
        response.setPlanType(SubscriptionPlan.FREE);
        response.setActiveEntitlement(true);
        response.setAiMealDrafts(true);
        response.setAiRecipeGeneration(true);
        response.setAiWorkoutPlanner(false);
        response.setAiInsights(true);
        response.setAiMonthlyQuota(3);
        response.setAiRemainingThisPeriod(2);

        when(subscriptionService.getUserFeatureAccessForAdmin(1L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/subscriptions/users/1/features"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planType").value("FREE"))
                .andExpect(jsonPath("$.activeEntitlement").value(true))
                .andExpect(jsonPath("$.aiMealDrafts").value(true))
                .andExpect(jsonPath("$.aiWorkoutPlanner").value(false))
                .andExpect(jsonPath("$.aiRemainingThisPeriod").value(2));

        verify(subscriptionService).getUserFeatureAccessForAdmin(1L);
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void applyCurrentFeatureMatrixToUser_whenAdmin_returnsUpdatedAccessAndAudits() throws Exception {
        SubscriptionFeatureAccessDto before = new SubscriptionFeatureAccessDto();
        before.setPlanType(SubscriptionPlan.PLUS);
        before.setWaterTracking(false);
        SubscriptionFeatureAccessDto response = new SubscriptionFeatureAccessDto();
        response.setPlanType(SubscriptionPlan.PLUS);
        response.setWaterTracking(true);

        when(subscriptionService.getUserFeatureAccessForAdmin(1L)).thenReturn(before);
        when(subscriptionService.applyCurrentFeatureMatrixToUser(1L)).thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/subscriptions/users/1/features/apply-current-matrix"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planType").value("PLUS"))
                .andExpect(jsonPath("$.waterTracking").value(true));

        verify(subscriptionService).applyCurrentFeatureMatrixToUser(1L);
        verify(adminAuditService).record(
                eq("admin@test.com"),
                eq(AdminAuditActionType.SUBSCRIPTION_ENTITLEMENT_MATRIX_APPLY),
                eq(AdminAuditTargetType.USER_SUBSCRIPTION),
                eq("1"),
                eq(before),
                eq(response),
                any()
        );
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
    @WithMockUser(username = "user@test.com", roles = "USER")
    void getUserFeatureAccess_whenNotAdmin_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/subscriptions/users/1/features"))
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
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Validation error"));
    }
}
