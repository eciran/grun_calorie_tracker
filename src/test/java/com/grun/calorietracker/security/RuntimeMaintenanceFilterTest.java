package com.grun.calorietracker.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.service.RuntimeOperationsService;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuntimeMaintenanceFilterTest {
    private final RuntimeOperationsService service = mock(RuntimeOperationsService.class);
    private final RuntimeMaintenanceFilter filter =
            new RuntimeMaintenanceFilter(service, new ObjectMapper().findAndRegisterModules());

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void blocksUserApiWithSafe503PayloadDuringMaintenance() throws Exception {
        when(service.maintenanceEnabled()).thenReturn(true);
        when(service.maintenanceMessage()).thenReturn("Back shortly");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/food-logs");
        request.setAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE, "cid-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(503, response.getStatus());
        assertTrue(response.getContentAsString().contains("MAINTENANCE_MODE"));
        assertTrue(response.getContentAsString().contains("cid-1"));
    }

    @Test
    void keepsAdminRecoveryRoutesAvailable() throws Exception {
        when(service.maintenanceEnabled()).thenReturn(true);
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/api/v1/admin/system/operations/policy");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
    }

    @Test
    void letsAuthenticatedAdminUseRegularApiDuringRecovery() throws Exception {
        when(service.maintenanceEnabled()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", "n/a", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/food-logs");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
    }
}
