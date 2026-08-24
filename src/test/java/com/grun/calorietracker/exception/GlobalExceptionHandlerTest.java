package com.grun.calorietracker.exception;

import com.grun.calorietracker.enums.SubscriptionFeature;

import com.grun.calorietracker.enums.AccountLinkErrorCode;
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
    void handleAdminMfaLoginException_returnsStableChallengeCode() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(messageSource(), false);
        MockHttpServletRequest request = request();

        var response = handler.handleAdminMfaLoginException(AdminMfaLoginException.required(), request);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("ADMIN_MFA_REQUIRED", response.getBody().getCode());
        assertEquals("Authenticator or recovery code is required.", response.getBody().getMessage());
        assertEquals("request-1", response.getBody().getCorrelationId());
    }

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
    void handleAiProviderException_hidesProviderDetails() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(messageSource(), true);
        MockHttpServletRequest request = request();

        var response = handler.handleAiProviderException(
                new AiProviderException("OpenAI provider request failed: HTTP 400 - invalid_request_error/invalid_value: Failed to download file."),
                request
        );

        assertEquals(502, response.getStatusCode().value());
        assertEquals("Bad Gateway", response.getBody().getError());
        assertEquals("AI_PROVIDER_ERROR", response.getBody().getCode());
        assertEquals("AI provider error", response.getBody().getMessage());
        assertEquals("request-1", response.getBody().getCorrelationId());
    }
    @Test
    void handleAiProviderTimeout_returnsStableRetryableCode() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(messageSource(), true);
        MockHttpServletRequest request = request();

        var response = handler.handleAiProviderTimeoutException(
                new AiProviderTimeoutException("private provider timeout detail"),
                request
        );

        assertEquals(504, response.getStatusCode().value());
        assertEquals("AI_TIMEOUT", response.getBody().getCode());
        assertEquals("AI request timed out", response.getBody().getMessage());
        assertEquals("request-1", response.getBody().getCorrelationId());
    }
    @Test
    void handleMaxUploadSizeExceeded_returnsPayloadTooLarge() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(messageSource(), false);
        MockHttpServletRequest request = request();

        var response = handler.handleMaxUploadSizeExceededException(new MaxUploadSizeExceededException(1024), request);

        assertEquals(413, response.getStatusCode().value());
        assertEquals("Payload Too Large", response.getBody().getError());
        assertEquals("UPLOAD_TOO_LARGE", response.getBody().getCode());
        assertEquals("Upload too large", response.getBody().getMessage());
    }

    @Test
    void handleDataIntegrityViolation_returnsBadRequest() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(messageSource(), false);
        MockHttpServletRequest request = request();

        var response = handler.handleDataIntegrityViolationException(new DataIntegrityViolationException("duplicate key"), request);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("Bad Request", response.getBody().getError());
        assertEquals("DATA_INTEGRITY_VIOLATION", response.getBody().getCode());
        assertEquals("Invalid request", response.getBody().getMessage());
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
        assertEquals("Conflict", response.getBody().getError());
        assertEquals("CONCURRENT_UPDATE", response.getBody().getCode());
        assertEquals("Concurrent update", response.getBody().getMessage());
    }

    @Test
    void handleAccountLinkException_preservesStableCodeAndStatus() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(messageSource(), false);
        MockHttpServletRequest request = request();

        var response = handler.handleAccountLinkException(
                new AccountLinkException(AccountLinkErrorCode.PROVIDER_IDENTITY_IN_USE,
                        "Provider identity is already linked to another account."),
                request
        );

        assertEquals(409, response.getStatusCode().value());
        assertEquals("PROVIDER_IDENTITY_IN_USE", response.getBody().getCode());
        assertEquals("request-1", response.getBody().getCorrelationId());
    }
    @Test
    void handleInvalidCredentials_usesTurkishCopyAndStableCode() {
        StaticMessageSource source = messageSource();
        source.addMessage("error.invalid.credentials", Locale.forLanguageTag("tr"), "Gecersiz kimlik bilgileri");
        GlobalExceptionHandler handler = new GlobalExceptionHandler(source, false);
        MockHttpServletRequest request = request();
        request.addPreferredLocale(Locale.forLanguageTag("tr"));

        var response = handler.handleInvalidCredentials(new InvalidCredentialsException("internal detail"), request);

        assertEquals("INVALID_CREDENTIALS", response.getBody().getCode());
        assertEquals("Unauthorized", response.getBody().getError());
        assertEquals("Gecersiz kimlik bilgileri", response.getBody().getMessage());
    }

    @Test
    void handleSubscriptionFeatureAccessDenied_returnsStandardForbiddenBody() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(messageSource(), false);
        MockHttpServletRequest request = request();

        var response = handler.handleSubscriptionFeatureAccessDenied(
                new SubscriptionFeatureAccessDeniedException(SubscriptionFeature.ADVANCED_ANALYTICS),
                request
        );

        assertEquals(403, response.getStatusCode().value());
        assertEquals("Forbidden", response.getBody().getError());
        assertEquals("SUBSCRIPTION_FEATURE_ACCESS_DENIED", response.getBody().getCode());
        assertEquals("request-1", response.getBody().getCorrelationId());
        assertEquals("/api/v1/test", response.getBody().getPath());
    }
    private StaticMessageSource messageSource() {
        StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.addMessage("error.unexpected", Locale.ENGLISH, "Unexpected error");
        messageSource.addMessage("error.upload.too-large", Locale.ENGLISH, "Upload too large");
        messageSource.addMessage("error.data-integrity", Locale.ENGLISH, "Invalid request");
        messageSource.addMessage("error.concurrent-update", Locale.ENGLISH, "Concurrent update");
        messageSource.addMessage("error.ai-provider", Locale.ENGLISH, "AI provider error");
        messageSource.addMessage("error.ai-timeout", Locale.ENGLISH, "AI request timed out");
        messageSource.addMessage("error.invalid.credentials", Locale.ENGLISH, "Invalid credentials");
        return messageSource;
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
        request.setAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE, "request-1");
        return request;
    }
}
