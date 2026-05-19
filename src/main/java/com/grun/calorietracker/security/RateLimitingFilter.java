package com.grun.calorietracker.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.ApiErrorResponseDto;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Set<String> PROTECTED_AUTH_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/password-reset/request",
            "/api/auth/email-verification/resend",
            "/api/auth/refresh",
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/password-reset/request",
            "/api/v1/auth/email-verification/resend",
            "/api/v1/auth/refresh"
    );

    private final InMemoryRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    @Value("${grun.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${grun.rate-limit.auth.max-requests-per-minute:20}")
    private int authMaxRequestsPerMinute;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        if (!enabled || !isProtectedRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = clientKey(request);
        boolean allowed = rateLimiter.isAllowed(key, authMaxRequestsPerMinute, 60_000);
        if (allowed) {
            filterChain.doFilter(request, response);
            return;
        }

        writeRateLimitResponse(request, response);
    }

    private boolean isProtectedRequest(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && PROTECTED_AUTH_PATHS.contains(request.getRequestURI());
    }

    private String clientKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String ip = forwardedFor == null || forwardedFor.isBlank()
                ? request.getRemoteAddr()
                : forwardedFor.split(",")[0].trim();
        return ip + ":" + request.getRequestURI();
    }

    private void writeRateLimitResponse(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiErrorResponseDto body = new ApiErrorResponseDto(
                LocalDateTime.now(),
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "Too many requests",
                "Too many requests. Please wait before trying again.",
                request.getRequestURI()
        );
        objectMapper.writeValue(response.getWriter(), body);
    }
}
