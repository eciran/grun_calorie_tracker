package com.grun.calorietracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.TypedNutrientEvidenceRequest;
import com.grun.calorietracker.dto.TypedSourceObservationRequest;
import com.grun.calorietracker.service.TypedNutrientEvidenceService;
import com.grun.calorietracker.service.support.ExternalFoodImportFence;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminTypedNutrientEvidenceControllerTest {
    @Configuration @EnableMethodSecurity
    static class Config {
        @Bean TypedNutrientEvidenceService evidence() { return mock(TypedNutrientEvidenceService.class); }
        @Bean ExternalFoodImportFence externalFoodImportFence() { return mock(ExternalFoodImportFence.class); }
        @Bean AdminTypedNutrientEvidenceController controller(TypedNutrientEvidenceService service) {
            return new AdminTypedNutrientEvidenceController(service);
        }
    }
    AnnotationConfigApplicationContext context;
    AdminTypedNutrientEvidenceController controller;
    TypedNutrientEvidenceService service;

    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext(Config.class);
        controller = context.getBean(AdminTypedNutrientEvidenceController.class);
        service = context.getBean(TypedNutrientEvidenceService.class);
        clearInvocations(service);
    }
    @AfterEach void close() { SecurityContextHolder.clearContext(); context.close(); }
    void role(String role) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("reviewer", "unused", role));
    }
    TypedNutrientEvidenceRequest request() {
        return new TypedNutrientEvidenceRequest("vitamin_a", "{}", "a".repeat(64), Instant.now(), "b".repeat(64), true, "Reviewed source observation");
    }
    TypedSourceObservationRequest observation() {
        return new TypedSourceObservationRequest("typed-source-observation-r5-v2", "USDA_FOODDATA", "vitamin_e",
                "{}", "a".repeat(64), Instant.now(), "b".repeat(64), true, "Reviewed source only, no nutrition update");
    }

    @Test void v2RoutesRemainAdminOnlyAndReviewerCannotBeSuppliedByBody() {
        role("ROLE_USER");
        assertThrows(AccessDeniedException.class, () -> controller.previewObservation(1, observation()));
        assertThrows(AccessDeniedException.class, () -> controller.appendObservation(1, observation(), null));
        verifyNoInteractions(service);
        role("ROLE_ADMIN");
        var request = observation();
        controller.appendObservation(1, request, User.withUsername("actual-admin").password("unused").roles("ADMIN").build());
        verify(service).appendObservation(1, request, "actual-admin");
    }
    @Test void v2HttpPreviewIsSeparateAndInvalidVersionCannotReachService() throws Exception {
        role("ROLE_ADMIN");
        var mapper = new ObjectMapper().findAndRegisterModules();
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        String body = mapper.writeValueAsString(observation());
        mvc.perform(post("/api/v1/admin/products/1/typed-nutrient-evidence/source-observations/preview")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/products/1/typed-nutrient-evidence/source-observations/preview")
                .contentType(MediaType.APPLICATION_JSON).content(body.replace("typed-source-observation-r5-v2", "v1")))
                .andExpect(status().isBadRequest());
        verify(service, times(1)).previewObservation(eq(1L), any());
        verify(service, never()).appendObservation(anyLong(), any(), any());
    }

    @Test void nonAdminCannotPreviewAppendListOrCompare() {
        role("ROLE_USER");
        assertThrows(AccessDeniedException.class, () -> controller.preview(1, request()));
        assertThrows(AccessDeniedException.class, () -> controller.append(1, request(), null));
        assertThrows(AccessDeniedException.class, () -> controller.history(1));
        assertThrows(AccessDeniedException.class, () -> controller.comparison(1, 2));
        verifyNoInteractions(service);
    }
    @Test void adminReviewerIsDerivedFromPrincipalNotRequestBody() {
        role("ROLE_ADMIN"); var request = request();
        var user = User.withUsername("actual-admin").password("unused").roles("ADMIN").build();
        controller.append(1, request, user);
        verify(service).append(1, request, "actual-admin");
    }
    @Test void invalidRequestCannotReachServiceThroughHttp() throws Exception {
        role("ROLE_ADMIN");
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(post("/api/v1/admin/products/1/typed-nutrient-evidence/preview")
                .contentType(MediaType.APPLICATION_JSON).content("{\"field\":\"vitamin_a\",\"sourceJson\":\"{}\",\"sourceSha256\":\"bad\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void validPreviewAndHistoryRoutesExistAndDoNotAppend() throws Exception {
        role("ROLE_ADMIN");
        var mapper = new ObjectMapper().findAndRegisterModules();
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(post("/api/v1/admin/products/1/typed-nutrient-evidence/preview")
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(request()))).andExpect(status().isOk());
        when(service.history(1)).thenReturn(List.of());
        mvc.perform(get("/api/v1/admin/products/1/typed-nutrient-evidence")).andExpect(status().isOk());
        verify(service, never()).append(anyLong(), any(), any());
    }
    @Test void requestBoundsPreventUnsupportedFieldsAndLargeBodies() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var invalid = new TypedNutrientEvidenceRequest("sodium", "x".repeat(1000001), "bad", null, "old", false, null);
            assertTrue(factory.getValidator().validate(invalid).size() >= 5);
            assertTrue(factory.getValidator().validate(request()).isEmpty());
        }
    }
}
