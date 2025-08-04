package com.grun.calorietracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.BodyFatResultDto;
import com.grun.calorietracker.dto.UserProfileDto;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    private UserEntity sampleUserEntity;
    private UserProfileDto sampleUserProfileDto;

    @BeforeEach
    void setUp() {
        sampleUserEntity = new UserEntity();
        sampleUserEntity.setId(1L);
        sampleUserEntity.setName("Test User");
        sampleUserEntity.setEmail("testuser@example.com");
        sampleUserEntity.setPassword("password");
        sampleUserEntity.setRole(UserRole.STANDARD);
        sampleUserEntity.setAge(30);
        sampleUserEntity.setGender("MALE");
        sampleUserEntity.setHeight(175.0);
        sampleUserEntity.setWeight(70.0);
        sampleUserEntity.setBmi(22.86);
        sampleUserEntity.setBodyFatPercentage(12.75);

        sampleUserProfileDto = new UserProfileDto();
        sampleUserProfileDto.setId(1L);
        sampleUserProfileDto.setName("Test User");
        sampleUserProfileDto.setEmail("testuser@example.com");
        sampleUserProfileDto.setAge(30);
        sampleUserProfileDto.setGender("MALE");
        sampleUserProfileDto.setHeight(175.0);
        sampleUserProfileDto.setWeight(70.0);
        sampleUserProfileDto.setBmi(22.86);
        sampleUserProfileDto.setBodyFat(12.75);
    }

    @Test
    @WithMockUser(username = "testuser@example.com")
    void testGetCurrentUser_Success() throws Exception {
        when(userService.getCurrentUser("testuser@example.com"))
                .thenReturn(sampleUserProfileDto);

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("testuser@example.com"))
                .andExpect(jsonPath("$.name").value("Test User"));
    }

    @Test
    @WithMockUser(username = "unknown@example.com")
    void testGetCurrentUser_Unauthorized() throws Exception {
        when(userService.getCurrentUser("unknown@example.com"))
                .thenThrow(new UsernameNotFoundException("Invalid credentials"));

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "testuser@example.com")
    void testUpdateCurrentUser_Success() throws Exception {
        UserProfileDto updatedDto = new UserProfileDto();
        updatedDto.setName("Updated Name");
        updatedDto.setEmail("testuser@example.com");
        updatedDto.setWeight(75.0);

        UserProfileDto returnedDto = new UserProfileDto();
        returnedDto.setId(1L);
        returnedDto.setName("Updated Name");
        returnedDto.setEmail("testuser@example.com");
        returnedDto.setWeight(75.0);

        when(userService.updateCurrentUser(any(UserProfileDto.class), eq("testuser@example.com")))
                .thenReturn(returnedDto);

        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatedDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"))
                .andExpect(jsonPath("$.weight").value(75.0));
    }

    @Test
    void testUpdateCurrentUser_Unauthorized() throws Exception {
        UserProfileDto updatedUser = new UserProfileDto();
        updatedUser.setName("Updated User");

        mockMvc.perform(put("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatedUser)))
                .andExpect(status().isForbidden());
    }
}