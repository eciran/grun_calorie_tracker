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
