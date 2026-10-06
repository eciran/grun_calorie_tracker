package com.grun.calorietracker.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.enums.ApiErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class AiAccountRateLimitInterceptor implements HandlerInterceptor {
    private static final String CHECKED = AiAccountRateLimitInterceptor.class.getName() + ".checked";
    private final RequestRateLimiter limiter;
    private final ObjectMapper mapper;
    @Value("${grun.rate-limit.enabled:true}")
    private boolean enabled = true;
    @Value("${grun.rate-limit.ai-draft.account-max-requests-per-minute:10}")
    private int maximum = 10;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!enabled || !"POST".equalsIgnoreCase(request.getMethod())
                || !RateLimitingFilter.isAiGenerationPath(request.getRequestURI())
                || Boolean.TRUE.equals(request.getAttribute(CHECKED))) return true;
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) return true;
        String key = "ai-account:" + hash(authentication.getName());
        if (limiter.isAllowed(key, maximum, 60_000)) {
            request.setAttribute(CHECKED, true);
            return true;
        }
        response.setStatus(429);
        response.setHeader("Retry-After", "60");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Object cid = request.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE);
        var body = new ApiErrorResponseDto(LocalDateTime.now(), 429, "Too many requests",
                "Too many requests. Please wait before trying again.", request.getRequestURI(),
                cid == null ? null : cid.toString());
        body.setCode(ApiErrorCode.RATE_LIMIT_EXCEEDED.name());
        mapper.writeValue(response.getWriter(), body);
        return false;
    }

    private String hash(String subject) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(subject.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
