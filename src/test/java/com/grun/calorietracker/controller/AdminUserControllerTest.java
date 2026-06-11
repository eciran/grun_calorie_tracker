package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.AdminUserPageDto;
import com.grun.calorietracker.dto.UserProfileDto;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.service.AdminAuditService;
import com.grun.calorietracker.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private AdminAuditService adminAuditService;

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void getAllUsers_whenAdmin_returnsUsers() throws Exception {
        UserProfileDto user = new UserProfileDto();
        user.setId(1L);
        user.setEmail("testuser@example.com");
        user.setName("Test User");

        when(userService.getAllUsers()).thenReturn(List.of(user));

        mockMvc.perform(get("/api/v1/admin/users/userList"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("testuser@example.com"))
                .andExpect(jsonPath("$[0].name").value("Test User"));
    }

    @Test
    @WithMockUser(username = "testuser@example.com", roles = "USER")
    void getAllUsers_whenNotAdmin_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/userList"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void listUsers_whenAdmin_returnsPaginatedUsers() throws Exception {
        UserProfileDto user = new UserProfileDto();
        user.setId(1L);
        user.setEmail("testuser@example.com");
        user.setName("Test User");
        user.setRole(UserRole.STANDARD);
        user.setAccountEnabled(true);
        user.setAccountLocked(false);

        AdminUserPageDto page = new AdminUserPageDto();
        page.setContent(List.of(user));
        page.setPage(0);
        page.setSize(25);
        page.setTotalElements(1);
        page.setTotalPages(1);
        page.setFirst(true);
        page.setLast(true);

        when(userService.listUsersForAdmin(UserRole.STANDARD, true, false, 0, 25)).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/users")
                        .param("role", "STANDARD")
                        .param("accountEnabled", "true")
                        .param("accountLocked", "false")
                        .param("page", "0")
                        .param("size", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("testuser@example.com"))
                .andExpect(jsonPath("$.content[0].accountEnabled").value(true))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void updateUserStatus_whenAdmin_returnsUpdatedUserAndRecordsAudit() throws Exception {
        UserProfileDto before = new UserProfileDto();
        before.setId(1L);
        before.setEmail("testuser@example.com");
        before.setAccountEnabled(true);
        before.setAccountLocked(false);

        UserProfileDto after = new UserProfileDto();
        after.setId(1L);
        after.setEmail("testuser@example.com");
        after.setAccountEnabled(false);
        after.setAccountLocked(true);

        when(userService.getById(1L)).thenReturn(Optional.of(before));
        when(userService.updateUserStatus(eq(1L), any(), eq("admin@example.com"))).thenReturn(after);

        mockMvc.perform(patch("/api/v1/admin/users/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountEnabled": false,
                                  "accountLocked": true,
                                  "reason": "Suspicious activity"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountEnabled").value(false))
                .andExpect(jsonPath("$.accountLocked").value(true));

        verify(adminAuditService).record(
                eq("admin@example.com"),
                any(),
                any(),
                eq("1"),
                eq(before),
                eq(after),
                any()
        );
    }

    @Test
    @WithMockUser(username = "testuser@example.com", roles = "USER")
    void updateUserStatus_whenNotAdmin_returnsForbidden() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountEnabled": false,
                                  "accountLocked": true
                                }
                                """))
                .andExpect(status().isForbidden());
    }
}

