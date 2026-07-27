package com.grun.calorietracker.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.enums.AdminPermission;
import com.grun.calorietracker.enums.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminPermissionMatrixTest {

    private final AdminAuthorizationFilter filter = new AdminAuthorizationFilter(new ObjectMapper().findAndRegisterModules());

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void supportCannotWriteSubscriptionConfiguration() throws Exception {
        authenticate(UserRole.ADMIN_SUPPORT);
        MockHttpServletResponse response = execute("PATCH", "/api/v1/admin/subscriptions/users/7");

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("FINANCE_MANAGE"));
    }

    @Test
    void financeCanWriteSubscriptionConfiguration() throws Exception {
        authenticate(UserRole.ADMIN_FINANCE);
        MockHttpServletResponse response = execute("PATCH", "/api/v1/admin/subscriptions/users/7");

        assertEquals(200, response.getStatus());
    }

    @Test
    void readOnlyRoleCannotMutateCatalog() throws Exception {
        authenticate(UserRole.ADMIN_READ_ONLY);
        MockHttpServletResponse response = execute("POST", "/api/v1/admin/products/quality-scan");

        assertEquals(403, response.getStatus());
    }

    @Test
    void supportCannotManageExerciseCatalog() throws Exception {
        authenticate(UserRole.ADMIN_SUPPORT);
        MockHttpServletResponse response = execute("POST", "/api/v1/admin/catalog/exercises");

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("CATALOG_MANAGE"));
    }

    @Test
    void catalogAdminCanManageExerciseCatalog() throws Exception {
        authenticate(UserRole.ADMIN_CATALOG);
        MockHttpServletResponse response = execute("POST", "/api/v1/admin/catalog/exercises");

        assertEquals(200, response.getStatus());
    }

    @Test
    void technicalAdminCanManageRuntimeOperations() throws Exception {
        authenticate(UserRole.ADMIN_TECHNICAL);

        assertEquals(200, execute("PUT", "/api/v1/admin/system/operations/policy").getStatus());
        assertEquals(200, execute("POST", "/api/v1/admin/system/operations/records/9/retry").getStatus());
    }

    @Test
    void growthAdminCannotReadTechnicalOrFinancePayloads() throws Exception {
        authenticate(UserRole.ADMIN_GROWTH);

        assertEquals(403, execute("GET", "/api/v1/admin/system/operations/api-metrics").getStatus());
        assertEquals(403, execute("GET", "/api/v1/admin/subscriptions/provider-events").getStatus());
        assertEquals(200, execute("POST", "/api/v1/admin/notification-campaigns").getStatus());
    }

    @Test
    void catalogAdminCannotReadUsersOrAuditLogs() throws Exception {
        authenticate(UserRole.ADMIN_CATALOG);

        assertEquals(403, execute("GET", "/api/v1/admin/users").getStatus());
        assertEquals(403, execute("GET", "/api/v1/admin/audits").getStatus());
        assertEquals(200, execute("GET", "/api/v1/admin/products/reviews").getStatus());
    }

    @Test
    void readOnlyAdminCanReadButCannotMutateRuntimePolicy() throws Exception {
        authenticate(UserRole.ADMIN_READ_ONLY);

        assertEquals(200, execute("GET", "/api/v1/admin/system/operations/policy").getStatus());
        assertEquals(403, execute("PUT", "/api/v1/admin/system/operations/policy").getStatus());
    }

    @Test
    void supportCannotAccessAdminTeamManagement() throws Exception {
        authenticate(UserRole.ADMIN_SUPPORT);

        assertEquals(403, execute("GET", "/api/v1/admin/security/admins").getStatus());
        assertEquals(403, execute("PATCH", "/api/v1/admin/security/admins/4").getStatus());
    }
    private void authenticate(UserRole role) {
        List<SimpleGrantedAuthority> authorities = AdminPermissionMatrix.permissionsFor(role).stream()
                .map(AdminPermissionMatrix::authority)
                .map(SimpleGrantedAuthority::new)
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin@example.com", null, authorities)
        );
    }

    private MockHttpServletResponse execute(String method, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
