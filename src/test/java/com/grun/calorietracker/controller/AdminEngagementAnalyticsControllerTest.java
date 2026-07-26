package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AdminEngagementAnalyticsDto;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.SubscriptionPlan;
import com.grun.calorietracker.service.AdminEngagementAnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminEngagementAnalyticsControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean AdminEngagementAnalyticsService analyticsService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void getSummary_forwardsServerSideFiltersAndReturnsAggregateOnly() throws Exception {
        when(analyticsService.getSummary(168, MarketRegion.TR,
                PreferredLanguage.TR, SubscriptionPlan.PLUS)).thenReturn(summary());

        mockMvc.perform(get("/api/v1/admin/engagement/analytics")
                        .param("hours", "168")
                        .param("region", "TR")
                        .param("language", "TR")
                        .param("plan", "PLUS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventContractVersion").value(1))
                .andExpect(jsonPath("$.onboarding.completed").value(6))
                .andExpect(jsonPath("$.foodLogging.firstCompletions").value(2))
                .andExpect(jsonPath("$.featureAdoption[0].feature").value("FOOD_LOG"))
                .andExpect(jsonPath("$.rawPrompt").doesNotExist())
                .andExpect(jsonPath("$.healthDetails").doesNotExist());

        verify(analyticsService).getSummary(168, MarketRegion.TR,
                PreferredLanguage.TR, SubscriptionPlan.PLUS);
    }

    @Test
    @WithMockUser(roles = "USER")
    void getSummary_rejectsNonAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/engagement/analytics"))
                .andExpect(status().isForbidden());
    }

    private AdminEngagementAnalyticsDto summary() {
        return new AdminEngagementAnalyticsDto(
                1, 168, LocalDateTime.now().minusDays(7), LocalDateTime.now(),
                new AdminEngagementAnalyticsDto.Filters("TR", "TR", "PLUS"),
                new AdminEngagementAnalyticsDto.OnboardingFunnel(
                        10, 30, 20, 1, 2, 8, 6, 2, 60, 3.3, 120000),
                new AdminEngagementAnalyticsDto.SearchQuality(
                        20, 3, 10, 4, 0.15, 0.5, false, List.of()),
                new AdminEngagementAnalyticsDto.FlowMetric(10, 8, 2, 1, 7, 80, 900),
                new AdminEngagementAnalyticsDto.FlowMetric(5, 4, 0, 1, 4, 80, 700),
                List.of(new AdminEngagementAnalyticsDto.FeatureAdoption(
                        "FOOD_LOG", 8, 7, 1, 900)),
                List.of(), List.of(), List.of()
        );
    }
}
