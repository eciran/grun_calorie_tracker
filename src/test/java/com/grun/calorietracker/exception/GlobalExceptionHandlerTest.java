package com.grun.calorietracker.exception;

import com.grun.calorietracker.security.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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

    @Test
    void handleMaxUploadSizeExceeded_returnsPayloadTooLarge() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(messageSource(), false);
        MockHttpServletRequest request = request();

        var response = handler.handleMaxUploadSizeExceededException(new MaxUploadSizeExceededException(1024), request);

        assertEquals(413, response.getStatusCode().value());
        assertEquals("Uploaded file exceeds the maximum allowed size.", response.getBody().getMessage());
    }

    @Test
    void handleDataIntegrityViolation_returnsBadRequest() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(messageSource(), false);
        MockHttpServletRequest request = request();

        var response = handler.handleDataIntegrityViolationException(new DataIntegrityViolationException("duplicate key"), request);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("Request conflicts with existing data.", response.getBody().getMessage());
        assertEquals("Invalid request", response.getBody().getError());
    }

    @Test
    void handleOptimisticLockException_returnsConflict() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(messageSource(), false);
        MockHttpServletRequest request = request();

        var response = handler.handleOptimisticLockException(
                new ObjectOptimisticLockingFailureException("Subscription", 1L),
                request
        );

        assertEquals(409, response.getStatusCode().value());
        assertEquals("Resource was updated by another request. Please reload and retry.", response.getBody().getMessage());
        assertEquals("Concurrent update", response.getBody().getError());
    }

    private StaticMessageSource messageSource() {
        StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.addMessage("error.unexpected", Locale.ENGLISH, "Unexpected error");
        messageSource.addMessage("error.upload.too-large", Locale.ENGLISH, "Upload too large");
        messageSource.addMessage("error.data-integrity", Locale.ENGLISH, "Invalid request");
        messageSource.addMessage("error.concurrent-update", Locale.ENGLISH, "Concurrent update");
        return messageSource;
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
        request.setAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE, "request-1");
        return request;
    }
}
