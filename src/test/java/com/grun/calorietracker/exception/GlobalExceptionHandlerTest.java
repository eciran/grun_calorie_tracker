package com.grun.calorietracker.exception;

import com.grun.calorietracker.security.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class GlobalExceptionHandlerTest {

    @Test
    void handleGeneric_whenInternalDetailsDisabled_hidesExceptionMessage() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(messageSource(), false);
        MockHttpServletRequest request = request();

        var response = handler.handleGeneric(new RuntimeException("database password leaked"), request);

        assertEquals(500, response.getStatusCode().value());
        assertNotEquals("database password leaked", response.getBody().getMessage());
        assertEquals("request-1", response.getBody().getCorrelationId());
    }

    @Test
    void handleGeneric_whenInternalDetailsEnabled_includesExceptionMessage() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(messageSource(), true);
        MockHttpServletRequest request = request();

        var response = handler.handleGeneric(new RuntimeException("debug detail"), request);

        assertEquals("debug detail", response.getBody().getMessage());
    }

    private StaticMessageSource messageSource() {
        StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.addMessage("error.unexpected", Locale.ENGLISH, "Unexpected error");
        return messageSource;
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
        request.setAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE, "request-1");
        return request;
    }
}
