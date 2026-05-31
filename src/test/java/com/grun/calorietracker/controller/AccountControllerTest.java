package com.grun.calorietracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.AccountPasswordRequestDto;
import com.grun.calorietracker.dto.AccountPasswordResponseDto;
import com.grun.calorietracker.dto.GdprDataExportDto;
import com.grun.calorietracker.dto.GdprDeleteRequestDto;
import com.grun.calorietracker.dto.LinkedIdentityDto;
import com.grun.calorietracker.dto.LinkGoogleRequestDto;
import com.grun.calorietracker.enums.AuthProvider;
import com.grun.calorietracker.enums.PreferredLanguage;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.service.AccountGdprService;
import com.grun.calorietracker.service.AccountIdentityService;
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
    private AccountGdprService accountGdprService;

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
    void linkGoogle_returnsLinkedIdentity() throws Exception {
        LinkGoogleRequestDto request = new LinkGoogleRequestDto();
        request.setIdToken("google-token");
        when(accountIdentityService.linkGoogle("user@grun.app", "google-token"))
                .thenReturn(new LinkedIdentityDto(AuthProvider.GOOGLE, "google@grun.app", LocalDateTime.now()));

        mockMvc.perform(post("/api/v1/account/link/google")
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
        mockMvc.perform(delete("/api/v1/account/linked-identities/GOOGLE"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "user@grun.app")
    void exportMyData_returnsGdprSnapshot() throws Exception {
        when(accountGdprService.exportMyData("user@grun.app"))
                .thenReturn(new GdprDataExportDto(
                        LocalDateTime.now(),
                        "user@grun.app",
                        "User",
                        UserRole.STANDARD,
                        null,
                        PreferredLanguage.EN,
                        true,
                        null,
                        null,
                        null,
                        1L,
                        2L,
                        3L,
                        4L,
                        1L,
                        0L,
                        0L,
                        0L,
                        0L,
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()
                ));

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
