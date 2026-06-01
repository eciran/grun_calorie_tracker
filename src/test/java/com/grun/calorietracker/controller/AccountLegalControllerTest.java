package com.grun.calorietracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.UserConsentDto;
import com.grun.calorietracker.dto.UserConsentRequestDto;
import com.grun.calorietracker.enums.LegalConsentStatus;
import com.grun.calorietracker.enums.LegalConsentType;
import com.grun.calorietracker.service.LegalConsentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AccountLegalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LegalConsentService legalConsentService;

    @Test
    @WithMockUser(username = "user@grun.app")
    void listMyConsents_returnsConsentHistory() throws Exception {
        when(legalConsentService.listMyConsents("user@grun.app"))
                .thenReturn(List.of(new UserConsentDto(
                        1L,
                        LegalConsentType.PRIVACY_POLICY,
                        "privacy-2026-05",
                        LegalConsentStatus.ACCEPTED,
                        "MOBILE_ONBOARDING",
                        LocalDateTime.of(2026, 5, 31, 12, 0)
                )));

        mockMvc.perform(get("/api/v1/account/legal/consents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].consentType").value("PRIVACY_POLICY"))
                .andExpect(jsonPath("$[0].version").value("privacy-2026-05"))
                .andExpect(jsonPath("$[0].status").value("ACCEPTED"));
    }

    @Test
    @WithMockUser(username = "user@grun.app")
    void recordConsent_acceptsMobileConsentDecision() throws Exception {
        UserConsentRequestDto request = new UserConsentRequestDto(
                LegalConsentType.HEALTH_DATA_PROCESSING,
                "health-2026-05",
                LegalConsentStatus.ACCEPTED,
                "MOBILE_ONBOARDING"
        );
        when(legalConsentService.recordConsent(
                eq("user@grun.app"),
                eq(request),
                eq("203.0.113.10"),
                eq("JUnit")
        )).thenReturn(new UserConsentDto(
                2L,
                LegalConsentType.HEALTH_DATA_PROCESSING,
                "health-2026-05",
                LegalConsentStatus.ACCEPTED,
                "MOBILE_ONBOARDING",
                LocalDateTime.of(2026, 5, 31, 12, 30)
        ));

        mockMvc.perform(post("/api/v1/account/legal/consents")
                        .header("X-Forwarded-For", "203.0.113.10")
                        .header("User-Agent", "JUnit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.consentType").value("HEALTH_DATA_PROCESSING"));

        verify(legalConsentService).recordConsent(
                eq("user@grun.app"),
                eq(request),
                eq("203.0.113.10"),
                eq("JUnit")
        );
    }
}
