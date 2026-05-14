package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.UserProfileDto;
import com.grun.calorietracker.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Test
    @WithMockUser(username = "admin@example.com", roles = "ADMIN")
    void getAllUsers_whenAdmin_returnsUsers() throws Exception {
        UserProfileDto user = new UserProfileDto();
        user.setId(1L);
        user.setEmail("testuser@example.com");
        user.setName("Test User");

        when(userService.getAllUsers()).thenReturn(List.of(user));

        mockMvc.perform(get("/api/admin/users/userList"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("testuser@example.com"))
                .andExpect(jsonPath("$[0].name").value("Test User"));
    }

    @Test
    @WithMockUser(username = "testuser@example.com", roles = "USER")
    void getAllUsers_whenNotAdmin_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/users/userList"))
                .andExpect(status().isForbidden());
    }
}
