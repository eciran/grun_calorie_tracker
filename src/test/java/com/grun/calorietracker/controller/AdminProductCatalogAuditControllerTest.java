package com.grun.calorietracker.controller;

import com.grun.calorietracker.dto.ProductCatalogAuditStatusDto;
import com.grun.calorietracker.service.impl.ProductCatalogAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AdminProductCatalogAuditControllerTest {
    private static final String API = "/api/v1/admin/products/quality-audit";
    @Autowired MockMvc mvc;
    @MockBean ProductCatalogAuditService service;

    @Test
    void anonymousCannotReadOrStartAudit() throws Exception {
        mvc.perform(get(API)).andExpect(status().isUnauthorized());
        mvc.perform(post(API + "/start")).andExpect(status().isUnauthorized());
        verify(service, never()).start(any());
    }

    @Test
    @WithMockUser(roles = "USER")
    void normalUserCannotAccessAnyAuditEndpoint() throws Exception {
        mvc.perform(get(API)).andExpect(status().isForbidden());
        for (String command : new String[]{"start", "step", "cancel"}) {
            mvc.perform(post(API + "/" + command)).andExpect(status().isForbidden());
        }
        mvc.perform(get(API + "/items")).andExpect(status().isForbidden());
        verify(service, never()).step();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminWithoutCatalogPermissionCannotStart() throws Exception {
        mvc.perform(post(API + "/start")).andExpect(status().isForbidden());
        verify(service, never()).start(any());
    }

    @Test
    @WithMockUser(username = "catalog-admin@test", authorities = {"ROLE_ADMIN", "ADMIN_PERMISSION_CATALOG_MANAGE", "ADMIN_PERMISSION_CATALOG_READ"})
    void catalogAdminCanControlAuditAndSeeOutcomes() throws Exception {
        var response = new ProductCatalogAuditStatusDto(1L, "RUNNING", "test", 4, 10, 3, 1, 0, 0, false, null, null);
        when(service.start("catalog-admin@test")).thenReturn(response);
        when(service.status()).thenReturn(response);
        when(service.step()).thenReturn(response);
        when(service.cancel()).thenReturn(response);
        when(service.items("REVIEW_REQUIRED", 0, 50)).thenReturn(Page.empty());
        mvc.perform(post(API + "/start")).andExpect(status().isOk()).andExpect(jsonPath("$.cursorId").value(4));
        mvc.perform(get(API)).andExpect(status().isOk()).andExpect(jsonPath("$.reviewRequired").value(1));
        mvc.perform(post(API + "/step")).andExpect(status().isOk());
        mvc.perform(post(API + "/cancel")).andExpect(status().isOk());
        mvc.perform(get(API + "/items").param("outcome", "REVIEW_REQUIRED")).andExpect(status().isOk());
        verify(service).start("catalog-admin@test");
    }

    @Test
    @WithMockUser(authorities = {"ROLE_ADMIN", "ADMIN_PERMISSION_CATALOG_MANAGE", "ADMIN_PERMISSION_CATALOG_READ"})
    void invalidOutcomeReturnsBadRequest() throws Exception {
        when(service.items("INVALID", 0, 50)).thenThrow(new IllegalArgumentException("Unsupported audit outcome"));
        mvc.perform(get(API + "/items").param("outcome", "INVALID")).andExpect(status().isBadRequest());
    }
}
