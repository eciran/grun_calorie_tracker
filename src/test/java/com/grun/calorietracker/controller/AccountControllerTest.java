package com.grun.calorietracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.AccountPasswordRequestDto;
import com.grun.calorietracker.dto.AccountPasswordResponseDto;
import com.grun.calorietracker.dto.AccountLinkAuthorizationRequestDto;
import com.grun.calorietracker.dto.AccountLinkAuthorizationResponseDto;
import com.grun.calorietracker.dto.GdprDataExportDto;
import com.grun.calorietracker.dto.GdprDeleteRequestDto;
import com.grun.calorietracker.dto.LinkedIdentityDto;
import com.grun.calorietracker.dto.LinkGoogleRequestDto;
import com.grun.calorietracker.dto.NotificationPreferenceDto;
import com.grun.calorietracker.enums.AuthProvider;
import com.grun.calorietracker.enums.AccountLinkPurpose;
import com.grun.calorietracker.enums.AccountReauthenticationMethod;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.service.AccountGdprService;
import com.grun.calorietracker.service.AccountIdentityService;
import com.grun.calorietracker.service.AccountLinkAuthorizationService;
import com.grun.calorietracker.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AccountIdentityService accountIdentityService;

    @MockitoBean
    private AccountLinkAuthorizationService accountLinkAuthorizationService;

    @MockitoBean
    private AccountGdprService accountGdprService;

    @MockitoBean
    private UserService userService;

    @Test
    @WithMockUser(username = "user@grun.app")
    void listLinkedIdentities_returnsCurrentUsersProviders() throws Exception {
        when(accountIdentityService.listLinkedIdentities("user@grun.app"))
                .thenReturn(List.of(new LinkedIdentityDto(AuthProvider.GOOGLE, "google@grun.app", LocalDateTime.now())));

        mockMvc.perform(get("/api/v1/account/linked-identities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].provider").value("GOOGLE"))
                .andExpect(jsonPath("$[0].providerEmail").value("google@grun.app"));
    }

    @Test
    @WithMockUser(username = "user@grun.app")
    void createLinkAuthorization_returnsOpaqueToken() throws Exception {
        AccountLinkAuthorizationRequestDto request = new AccountLinkAuthorizationRequestDto();
        request.setPurpose(AccountLinkPurpose.ACCOUNT_LINK);
        request.setTargetProvider(AuthProvider.GOOGLE);
        request.setMethod(AccountReauthenticationMethod.PASSWORD);
        request.setCurrentPassword("CurrentPass1!");
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(5);
        when(accountLinkAuthorizationService.createAuthorization("user@grun.app", request))
                .thenReturn(new AccountLinkAuthorizationResponseDto(
                        "opaque-token", AccountLinkPurpose.ACCOUNT_LINK, AuthProvider.GOOGLE, expiresAt));

        mockMvc.perform(post("/api/v1/account/link-authorizations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorizationToken").value("opaque-token"))
                .andExpect(jsonPath("$.purpose").value("ACCOUNT_LINK"))
                .andExpect(jsonPath("$.targetProvider").value("GOOGLE"));
    }
    @Test
    @WithMockUser(username = "user@grun.app")
    void linkGoogle_returnsLinkedIdentity() throws Exception {
        LinkGoogleRequestDto request = new LinkGoogleRequestDto();
        request.setIdToken("google-token");
        when(accountIdentityService.linkGoogle("user@grun.app", "google-token", "opaque-token"))
                .thenReturn(new LinkedIdentityDto(AuthProvider.GOOGLE, "google@grun.app", LocalDateTime.now()));

        mockMvc.perform(post("/api/v1/account/link/google")
                        .header("X-Account-Link-Authorization", "opaque-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("GOOGLE"));
    }

    @Test
    @WithMockUser(username = "user@grun.app")
    void updatePassword_returnsSuccessMessage() throws Exception {
        AccountPasswordRequestDto request = new AccountPasswordRequestDto();
        request.setNewPassword("NewStrongPass1!");
        when(accountIdentityService.updatePassword("user@grun.app", request))
                .thenReturn(new AccountPasswordResponseDto("Password updated successfully."));

        mockMvc.perform(put("/api/v1/account/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password updated successfully."));
    }

    @Test
    @WithMockUser(username = "user@grun.app")
    void unlinkProvider_returnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/account/linked-identities/GOOGLE")
                        .header("X-Account-Link-Authorization", "opaque-token"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "user@grun.app")
    void getNotificationPreferences_returnsCurrentSettings() throws Exception {
        when(userService.getNotificationPreferences("user@grun.app"))
                .thenReturn(new NotificationPreferenceDto(true, false, true));

        mockMvc.perform(get("/api/v1/account/notification-preferences"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pushNotificationsEnabled").value(true))
                .andExpect(jsonPath("$.mealRemindersEnabled").value(false))
                .andExpect(jsonPath("$.hydrationRemindersEnabled").value(true))
                .andExpect(jsonPath("$.fastingRemindersEnabled").value(nullValue()));
    }

    @Test
    @WithMockUser(username = "user@grun.app")
    void updateNotificationPreferences_returnsUpdatedSettings() throws Exception {
        NotificationPreferenceDto request = new NotificationPreferenceDto(false, true, false);
        when(userService.updateNotificationPreferences("user@grun.app", request))
                .thenReturn(request);

        mockMvc.perform(put("/api/v1/account/notification-preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pushNotificationsEnabled").value(false))
                .andExpect(jsonPath("$.mealRemindersEnabled").value(true))
                .andExpect(jsonPath("$.hydrationRemindersEnabled").value(false));
    }

    @Test
    @WithMockUser(username = "user@grun.app")
    void exportMyData_returnsGdprSnapshot() throws Exception {
        GdprDataExportDto export = new GdprDataExportDto();
        export.setEmail("user@grun.app");
        export.setExerciseLogCount(2L);
        when(accountGdprService.exportMyData("user@grun.app")).thenReturn(export);

        mockMvc.perform(get("/api/v1/account/gdpr/export"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@grun.app"))
                .andExpect(jsonPath("$.exerciseLogCount").value(2));
    }

    @Test
    @WithMockUser(username = "user@grun.app")
    void anonymizeAndDelete_returnsSuccessMessage() throws Exception {
        GdprDeleteRequestDto request = new GdprDeleteRequestDto();
        request.setConfirmText("DELETE_MY_ACCOUNT");
        request.setCurrentPassword("CurrentPass1!");

        mockMvc.perform(delete("/api/v1/account/gdpr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Account anonymized and deleted successfully."));
    }
}
