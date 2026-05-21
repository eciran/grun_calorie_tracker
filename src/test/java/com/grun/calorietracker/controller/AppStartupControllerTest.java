package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AppStartupDto;
import com.grun.calorietracker.dto.UserGoalDto;
import com.grun.calorietracker.dto.UserProfileDto;
import com.grun.calorietracker.service.AppStartupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AppStartupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AppStartupService appStartupService;

    @Test
    @WithMockUser(username = "user@example.com", roles = "USER")
    void getStartupState_returnsAuthenticatedUserStartupState() throws Exception {
        UserProfileDto profile = UserProfileDto.builder()
                .id(1L)
                .email("user@example.com")
                .name("Demo User")
                .age(32)
                .gender("MALE")
                .height(180.0)
                .weight(82.0)
                .build();
        UserGoalDto goal = new UserGoalDto();
        goal.setId(10L);
        goal.setDailyCalorieGoal(2242);

        AppStartupDto response = AppStartupDto.builder()
                .profile(profile)
                .goal(goal)
                .profileComplete(true)
                .hasActiveGoal(true)
                .onboardingCompleted(true)
                .emailVerified(true)
                .dashboardReady(true)
                .nextStep("OPEN_DASHBOARD")
                .build();

        when(appStartupService.getStartupState("user@example.com")).thenReturn(response);

        mockMvc.perform(get("/api/v1/app/startup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.email").value("user@example.com"))
                .andExpect(jsonPath("$.goal.dailyCalorieGoal").value(2242))
                .andExpect(jsonPath("$.profileComplete").value(true))
                .andExpect(jsonPath("$.hasActiveGoal").value(true))
                .andExpect(jsonPath("$.onboardingCompleted").value(true))
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.dashboardReady").value(true))
                .andExpect(jsonPath("$.nextStep").value("OPEN_DASHBOARD"));

        verify(appStartupService).getStartupState("user@example.com");
    }
}
