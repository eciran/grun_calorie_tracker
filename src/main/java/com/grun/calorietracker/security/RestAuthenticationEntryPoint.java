package com.grun.calorietracker.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.config.LocaleConfig;
import com.grun.calorietracker.dto.ApiErrorResponseDto;
import com.grun.calorietracker.enums.ApiErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;
    private final MessageSource messageSource;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper, MessageSource messageSource) {
        this.objectMapper = objectMapper;
        this.messageSource = messageSource;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiErrorResponseDto body = new ApiErrorResponseDto(
                LocalDateTime.now(),
                HttpStatus.UNAUTHORIZED.value(),
                "Invalid credentials",
                "JWT token is missing or invalid.",
                request.getRequestURI(),
                correlationId(request)
        );
        body.setCode(ApiErrorCode.INVALID_CREDENTIALS.name());
        objectMapper.writeValue(response.getWriter(), body);
    }

    private String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE);
        return value == null ? request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER) : value.toString();
    }
}
