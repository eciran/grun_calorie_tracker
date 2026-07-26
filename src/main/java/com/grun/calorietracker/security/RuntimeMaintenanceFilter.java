package com.grun.calorietracker.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.service.RuntimeOperationsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class RuntimeMaintenanceFilter extends OncePerRequestFilter {
    private final RuntimeOperationsService runtimeOperationsService;
    private final ObjectMapper objectMapper;

    public RuntimeMaintenanceFilter(
            RuntimeOperationsService runtimeOperationsService, ObjectMapper objectMapper) {
        this.runtimeOperationsService = runtimeOperationsService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!runtimeOperationsService.maintenanceEnabled() || isExempt(request) || isAdmin()) {
            filterChain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        body.put("error", "Maintenance mode");
        body.put("message", runtimeOperationsService.maintenanceMessage());
        body.put("path", request.getRequestURI());
        body.put("correlationId", request.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE));
        body.put("code", "MAINTENANCE_MODE");
        objectMapper.writeValue(response.getWriter(), body);
    }

    private boolean isExempt(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/")
                || path.startsWith("/api/v1/admin/")
                || path.startsWith("/api/v1/auth/")
                || path.startsWith("/api/v1/runtime/config")
                || path.startsWith("/api/v1/webhooks/");
    }

    private boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().startsWith("ROLE_ADMIN"));
    }
}
