package com.grun.calorietracker.security;

import com.grun.calorietracker.dto.ApiErrorResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

public final class SubscriptionFeatureAccessDeniedResponseFactory {

    public static final String ERROR_CODE = "SUBSCRIPTION_FEATURE_ACCESS_DENIED";
    public static final String MESSAGE = "Subscription feature access denied";

    private SubscriptionFeatureAccessDeniedResponseFactory() {
    }

    public static ApiErrorResponseDto create(HttpServletRequest request) {
        ApiErrorResponseDto body = new ApiErrorResponseDto(
                LocalDateTime.now(),
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                MESSAGE,
                request.getRequestURI(),
                correlationId(request)
        );
        body.setCode(ERROR_CODE);
        return body;
    }

    private static String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE);
        return value == null ? request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER) : value.toString();
    }
}