package com.grun.calorietracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.UserProfileDto;
import com.grun.calorietracker.enums.UserRole;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.repository.UserRepository;
import com.grun.calorietracker.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserRepository userRepository;

    private UserEntity sampleUser;
    private UserProfileDto sampleUserProfileDto;

    @BeforeEach
    void setUp() {
        sampleUser = new UserEntity();
        sampleUser.setId(1L);
        sampleUser.setName("Test User");
        sampleUser.setEmail("testuser@example.com");
        sampleUser.setPassword("password");
        sampleUser.setRole(UserRole.STANDARD);
        sampleUser.setAge(30);
        sampleUser.setGender("MALE");
        sampleUser.setHeight(175.0);
        sampleUser.setWeight(70.0);

        sampleUserProfileDto = new UserProfileDto();
        sampleUserProfileDto.setId(1L);
        sampleUserProfileDto.setName("Test User");
        sampleUserProfileDto.setEmail("testuser@example.com");
        sampleUserProfileDto.setAge(30);
        sampleUserProfileDto.setGender("MALE");
        sampleUserProfileDto.setHeight(175.0);
        sampleUserProfileDto.setWeight(70.0);
    }

    @Test
    void testRegisterUser() throws Exception {
        when(userService.registerUser(any(UserEntity.class))).thenReturn(sampleUserProfileDto);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleUser)))
                .andExpect(status().isOk());
    }
}