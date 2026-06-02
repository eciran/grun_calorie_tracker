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
            "/api/v1/auth/login",
            "/api/v1/auth/google",
            "/api/v1/auth/apple",
            "/api/v1/auth/register",
            "/api/v1/auth/password-reset/request",
            "/api/v1/auth/email-verification/resend",
            "/api/v1/auth/refresh"
    );

    private static final String PRODUCT_BARCODE_PATH_PREFIX = "/api/v1/products/barcode/";
    private static final Set<String> AI_DRAFT_PATHS = Set.of(
            "/api/v1/ai/meal-drafts/voice",
            "/api/v1/ai/meal-drafts/photo"
    );
    private static final String PASSWORD_RESET_REQUEST_PATH = "/api/v1/auth/password-reset/request";
    private static final String EMAIL_VERIFICATION_RESEND_PATH = "/api/v1/auth/email-verification/resend";

    private final RequestRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    @Value("${grun.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${grun.rate-limit.auth.max-requests-per-minute:20}")
    private int authMaxRequestsPerMinute;

    @Value("${grun.rate-limit.password-reset.max-requests-per-minute:5}")
    private int passwordResetMaxRequestsPerMinute;

    @Value("${grun.rate-limit.email-verification-resend.max-requests-per-minute:5}")
    private int emailVerificationResendMaxRequestsPerMinute;

    @Value("${grun.rate-limit.ai-draft.max-requests-per-minute:10}")
    private int aiDraftMaxRequestsPerMinute;

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
        boolean allowed = rateLimiter.isAllowed(key, resolveMaxRequests(request), 60_000);
        if (allowed) {
            filterChain.doFilter(request, response);
            return;
        }

        writeRateLimitResponse(request, response);
    }

    private boolean isProtectedRequest(HttpServletRequest request) {
        if ("POST".equalsIgnoreCase(request.getMethod()) && PROTECTED_AUTH_PATHS.contains(request.getRequestURI())) {
            return true;
        }
        if ("POST".equalsIgnoreCase(request.getMethod()) && AI_DRAFT_PATHS.contains(request.getRequestURI())) {
            return true;
        }
        return "GET".equalsIgnoreCase(request.getMethod())
                && request.getRequestURI().startsWith(PRODUCT_BARCODE_PATH_PREFIX);
    }

    private String clientKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String ip = forwardedFor == null || forwardedFor.isBlank()
                ? request.getRemoteAddr()
                : forwardedFor.split(",")[0].trim();
        return ip + ":" + request.getRequestURI();
    }

    private int resolveMaxRequests(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (PASSWORD_RESET_REQUEST_PATH.equals(path)) {
            return passwordResetMaxRequestsPerMinute;
        }
        if (EMAIL_VERIFICATION_RESEND_PATH.equals(path)) {
            return emailVerificationResendMaxRequestsPerMinute;
        }
        if (AI_DRAFT_PATHS.contains(path)) {
            return aiDraftMaxRequestsPerMinute;
        }
        return authMaxRequestsPerMinute;
    }

    private void writeRateLimitResponse(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiErrorResponseDto body = new ApiErrorResponseDto(
                LocalDateTime.now(),
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "Too many requests",
                "Too many requests. Please wait before trying again.",
                request.getRequestURI(),
                correlationId(request)
        );
        objectMapper.writeValue(response.getWriter(), body);
    }

    private String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE);
        return value == null ? request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER) : value.toString();
    }
}
