package com.grun.calorietracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.MyProfileDto;
import com.grun.calorietracker.dto.MyProfileUpdateRequestDto;
import com.grun.calorietracker.dto.ProfileBodyDto;
import com.grun.calorietracker.dto.ProfileBodyUpdateRequestDto;
import com.grun.calorietracker.dto.ProfilePreferencesDto;
import com.grun.calorietracker.dto.ProfileSecurityDto;
import com.grun.calorietracker.enums.MarketRegion;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.UnitPreference;
import com.grun.calorietracker.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    private MyProfileDto profile;

    @BeforeEach
    void setUp() {
        profile = MyProfileDto.builder()
                .id(1L)
                .email("testuser@example.com")
                .name("Test User")
                .body(ProfileBodyDto.builder()
                        .age(30)
                        .gender("MALE")
                        .height(175.0)
                        .weight(70.0)
                        .bmi(22.86)
                        .bodyFat(12.75)
                        .build())
                .preferences(ProfilePreferencesDto.builder()
                        .marketRegion(MarketRegion.UK_IE)
                        .preferredLanguage(PreferredLanguage.EN)
                        .timeZone("Europe/Dublin")
                        .unitPreference(UnitPreference.METRIC)
                        .build())
                .security(ProfileSecurityDto.builder()
                        .emailVerified(true)
                        .passwordSet(false)
                        .build())
                .build();
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void usersRoot_isNotExposedFromUserController() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "testuser@example.com")
    void getCurrentUser_returnsSplitContractWithoutAdminFields() throws Exception {
        when(userService.getMyProfile("testuser@example.com")).thenReturn(profile);

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("testuser@example.com"))
                .andExpect(jsonPath("$.body.weight").value(70.0))
                .andExpect(jsonPath("$.preferences.marketRegion").value("UK_IE"))
                .andExpect(jsonPath("$.security.emailVerified").value(true))
                .andExpect(jsonPath("$.role").doesNotExist())
                .andExpect(jsonPath("$.accountEnabled").doesNotExist())
                .andExpect(jsonPath("$.accountLocked").doesNotExist());
    }

    @Test
    @WithMockUser(username = "unknown@example.com")
    void getCurrentUser_whenMissing_returnsUnauthorized() throws Exception {
        when(userService.getMyProfile("unknown@example.com"))
                .thenThrow(new UsernameNotFoundException("Invalid credentials"));

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "testuser@example.com")
    void updateCurrentUser_acceptsOnlyBasicProfileContract() throws Exception {
        MyProfileUpdateRequestDto request = new MyProfileUpdateRequestDto();
        request.setName("Updated Name");
        profile.setName("Updated Name");
        when(userService.updateMyProfile(any(MyProfileUpdateRequestDto.class), eq("testuser@example.com")))
                .thenReturn(profile);

        mockMvc.perform(put("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"))
                .andExpect(jsonPath("$.accountLocked").doesNotExist());
    }

    @Test
    @WithMockUser(username = "testuser@example.com")
    void updateBody_returnsGoalRecalculationSignal() throws Exception {
        profile.setGoalRecalculationRecommended(true);
        when(userService.updateProfileBody(any(ProfileBodyUpdateRequestDto.class), eq("testuser@example.com")))
                .thenReturn(profile);

        mockMvc.perform(patch("/api/v1/users/me/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"weight":75.0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goalRecalculationRecommended").value(true));
    }

    @Test
    @WithMockUser(username = "testuser@example.com")
    void getSecurity_returnsOnlyUserVisibleSecurityState() throws Exception {
        when(userService.getProfileSecurity("testuser@example.com")).thenReturn(profile.getSecurity());

        mockMvc.perform(get("/api/v1/users/me/security"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.passwordSet").value(false))
                .andExpect(jsonPath("$.accountEnabled").doesNotExist())
                .andExpect(jsonPath("$.accountLocked").doesNotExist());
    }

    @Test
    void updateCurrentUser_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(put("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Updated User"}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
