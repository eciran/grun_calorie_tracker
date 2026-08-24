package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AdminCustomer360Dto;
import com.grun.calorietracker.dto.AdminUserSupportNoteDto;
import com.grun.calorietracker.service.AdminAuditService;
import com.grun.calorietracker.service.AdminCustomer360Service;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminCustomer360ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminCustomer360Service customer360Service;

    @MockBean
    private AdminAuditService adminAuditService;

    @Test
    @WithMockUser(username = "admin@example.com", authorities = {"ROLE_ADMIN", "ADMIN_PERMISSION_USERS_READ", "ADMIN_PERMISSION_USERS_MANAGE"})
    void getCustomer_returnsSanitizedSupportSummary() throws Exception {
        when(customer360Service.getCustomer(7L)).thenReturn(customer("user@example.com"));

        mockMvc.perform(get("/api/v1/admin/users/7/customer-360"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.email").value("user@example.com"))
                .andExpect(jsonPath("$.ai.totalRequests").value(4))
                .andExpect(jsonPath("$.security.activeSessions").value(2))
                .andExpect(jsonPath("$.profile.weight").doesNotExist())
                .andExpect(jsonPath("$.profile.bmi").doesNotExist());
    }

    @Test
    @WithMockUser(username = "admin@example.com", authorities = {"ROLE_ADMIN", "ADMIN_PERMISSION_USERS_READ", "ADMIN_PERMISSION_USERS_MANAGE"})
    void addSupportNote_recordsAuditedNote() throws Exception {
        AdminUserSupportNoteDto note = new AdminUserSupportNoteDto(
                3L, "Follow up after billing correction", List.of("BILLING"),
                "admin@example.com", LocalDateTime.now()
        );
        when(customer360Service.addSupportNote(eq(7L), any(), eq("admin@example.com"))).thenReturn(note);

        mockMvc.perform(post("/api/v1/admin/users/7/support-notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"note":"Follow up after billing correction","tags":["BILLING"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags[0]").value("BILLING"));

        verify(adminAuditService).record(eq("admin@example.com"), any(), any(), eq("7"), eq(null), eq(note), any());
    }

    @Test
    @WithMockUser(username = "admin@example.com", authorities = {"ROLE_ADMIN", "ADMIN_PERMISSION_USERS_READ", "ADMIN_PERMISSION_USERS_MANAGE"})
    void revokeSessions_requiresConfirmedReasonAndAuditsResult() throws Exception {
        when(customer360Service.getCustomer(7L)).thenReturn(customer("user@example.com"));
        when(customer360Service.revokeActiveSessions(7L)).thenReturn(2);

        mockMvc.perform(post("/api/v1/admin/users/7/sessions/revoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"User reported a compromised device","confirmed":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revokedSessions").value(2));

        verify(adminAuditService).record(eq("admin@example.com"), any(), any(), eq("7"), any(), any(), any());
    }

    private AdminCustomer360Dto customer(String email) {
        AdminCustomer360Dto.ProfileSummary profile = new AdminCustomer360Dto.ProfileSummary(
                7L, email, "Test User", "STANDARD", true, true,
                "UK_IE", "EN", true, false, null, null, null, null
        );
        return new AdminCustomer360Dto(
                profile,
                new AdminCustomer360Dto.SubscriptionSummary("PLUS", "ACTIVE", "MONTHLY", null, null, true, 50, 2, 0, null, List.of("FOOD_DIARY")),
                new AdminCustomer360Dto.AiSummary(4, null, java.util.Map.of("CONFIRMED", 3L), java.util.Map.of(), 4),
                new AdminCustomer360Dto.NotificationSummary(1, 1, List.of()),
                new AdminCustomer360Dto.SecuritySummary(2, List.of()),
                new AdminCustomer360Dto.ConsentSummary(1, List.of()),
                new AdminCustomer360Dto.ActivitySummary(5, null, 3, List.of()),
                List.of()
        );
    }
}
