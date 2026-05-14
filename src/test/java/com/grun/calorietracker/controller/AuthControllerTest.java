package com.grun.calorietracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.AuthRequest;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
        AuthRequest request = new AuthRequest();
        request.setEmail("testuser@example.com");
        request.setPassword("Password1!");

        when(userRepository.findByEmail("testuser@example.com")).thenReturn(java.util.Optional.empty());
        when(userRepository.save(any(UserEntity.class))).thenReturn(sampleUser);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(userRepository).save(any(UserEntity.class));
    }

    @Test
    void register_whenEmailAlreadyExists_returnsStandardErrorResponse() throws Exception {
        AuthRequest request = new AuthRequest();
        request.setEmail("testuser@example.com");
        request.setPassword("Password1!");

        when(userRepository.findByEmail("testuser@example.com")).thenReturn(java.util.Optional.of(sampleUser));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation error"))
                .andExpect(jsonPath("$.message").value("Email already registered"))
                .andExpect(jsonPath("$.path").value("/api/auth/register"));
    }

    @Test
    void openApi_registerBadRequest_usesErrorResponseSchema() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/register'].post.responses['400'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ApiErrorResponseDto"));
    }

    @Test
    void openApi_productBarcodeErrors_useEndpointSpecificExamples() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/products/barcode/{barcode}'].get.responses['401'].content['application/json'].example.status")
                        .value(401))
                .andExpect(jsonPath("$.paths['/api/products/barcode/{barcode}'].get.responses['401'].content['application/json'].example.path")
                        .value("/api/products/barcode/{barcode}"))
                .andExpect(jsonPath("$.paths['/api/products/barcode/{barcode}'].get.responses['401'].content['application/json'].example.message")
                        .value("JWT token is missing or invalid."))
                .andExpect(jsonPath("$.paths['/api/products/barcode/{barcode}'].get.responses['404'].content['application/json'].example.status")
                        .value(404))
                .andExpect(jsonPath("$.paths['/api/products/barcode/{barcode}'].get.responses['404'].content['application/json'].example.path")
                        .value("/api/products/barcode/{barcode}"))
                .andExpect(jsonPath("$.paths['/api/products/barcode/{barcode}'].get.responses['404'].content['application/json'].example.message")
                        .value("Product could not be found locally or from the configured external source."));
    }
}
