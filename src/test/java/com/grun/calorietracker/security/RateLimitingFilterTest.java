package com.grun.calorietracker.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RateLimitingFilterTest {

    @Test
    void protectedAuthPathReturnsTooManyRequestsAfterLimitIsExceeded() throws Exception {
        RateLimitingFilter filter = buildFilter(1);

        FilterChain filterChain = mock(FilterChain.class);

        MockHttpServletRequest firstRequest = post("/api/v1/auth/login");
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        filter.doFilter(firstRequest, firstResponse, filterChain);

        MockHttpServletRequest secondRequest = post("/api/v1/auth/login");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(secondRequest, secondResponse, filterChain);

        assertEquals(200, firstResponse.getStatus());
        assertEquals(429, secondResponse.getStatus());
        assertEquals("application/json", secondResponse.getContentType());
        verify(filterChain, times(1)).doFilter(firstRequest, firstResponse);
    }

    @Test
    void unprotectedPathDoesNotConsumeLimit() throws Exception {
        RateLimitingFilter filter = buildFilter(1);

        FilterChain filterChain = mock(FilterChain.class);

        MockHttpServletRequest request = post("/api/v1/food-logs");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void v1ProtectedAuthPathIsRateLimited() throws Exception {
        RateLimitingFilter filter = buildFilter(1);

        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(post("/api/v1/auth/refresh"), new MockHttpServletResponse(), filterChain);
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(post("/api/v1/auth/refresh"), secondResponse, filterChain);

        assertEquals(429, secondResponse.getStatus());
    }

    @Test
    void googleAuthPathIsRateLimited() throws Exception {
        RateLimitingFilter filter = buildFilter(1);

        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(post("/api/v1/auth/google"), new MockHttpServletResponse(), filterChain);
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(post("/api/v1/auth/google"), secondResponse, filterChain);

        assertEquals(429, secondResponse.getStatus());
    }

    @Test
    void appleAuthPathIsRateLimited() throws Exception {
        RateLimitingFilter filter = buildFilter(1);

        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(post("/api/v1/auth/apple"), new MockHttpServletResponse(), filterChain);
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(post("/api/v1/auth/apple"), secondResponse, filterChain);

        assertEquals(429, secondResponse.getStatus());
    }

    @Test
    void barcodeLookupPathIsRateLimited() throws Exception {
        RateLimitingFilter filter = buildFilter(1);

        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(get("/api/v1/products/barcode/3017620422003"), new MockHttpServletResponse(), filterChain);
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(get("/api/v1/products/barcode/3017620422003"), secondResponse, filterChain);

        assertEquals(429, secondResponse.getStatus());
    }

    @Test
    void passwordResetPathUsesDedicatedLimit() throws Exception {
        RateLimitingFilter filter = buildFilter(10);
        ReflectionTestUtils.setField(filter, "passwordResetMaxRequestsPerMinute", 1);

        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(post("/api/v1/auth/password-reset/request"), new MockHttpServletResponse(), filterChain);
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(post("/api/v1/auth/password-reset/request"), secondResponse, filterChain);

        assertEquals(429, secondResponse.getStatus());
    }

    @Test
    void emailVerificationResendPathUsesDedicatedLimit() throws Exception {
        RateLimitingFilter filter = buildFilter(10);
        ReflectionTestUtils.setField(filter, "emailVerificationResendMaxRequestsPerMinute", 1);

        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(post("/api/v1/auth/email-verification/resend"), new MockHttpServletResponse(), filterChain);
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(post("/api/v1/auth/email-verification/resend"), secondResponse, filterChain);

        assertEquals(429, secondResponse.getStatus());
    }

    @Test
    void aiDraftPathUsesDedicatedLimit() throws Exception {
        RateLimitingFilter filter = buildFilter(10);
        ReflectionTestUtils.setField(filter, "aiDraftMaxRequestsPerMinute", 1);

        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(post("/api/v1/ai/meal-drafts/voice"), new MockHttpServletResponse(), filterChain);
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(post("/api/v1/ai/meal-drafts/voice"), secondResponse, filterChain);

        assertEquals(429, secondResponse.getStatus());
    }

    @Test
    void aiRecipeGenerationPathUsesDedicatedLimit() throws Exception {
        RateLimitingFilter filter = buildFilter(10);
        ReflectionTestUtils.setField(filter, "aiDraftMaxRequestsPerMinute", 1);

        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(post("/api/v1/ai/recipes/generate"), new MockHttpServletResponse(), filterChain);
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(post("/api/v1/ai/recipes/generate"), secondResponse, filterChain);

        assertEquals(429, secondResponse.getStatus());
    }

    @Test
    void ignoresForwardedForWhenNoTrustedProxyIsConfigured() throws Exception {
        RateLimitingFilter filter = buildFilter(1);

        FilterChain filterChain = mock(FilterChain.class);

        MockHttpServletRequest firstRequest = post("/api/v1/auth/login");
        firstRequest.addHeader("X-Forwarded-For", "198.51.100.1");
        filter.doFilter(firstRequest, new MockHttpServletResponse(), filterChain);

        MockHttpServletRequest secondRequest = post("/api/v1/auth/login");
        secondRequest.addHeader("X-Forwarded-For", "203.0.113.99");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(secondRequest, secondResponse, filterChain);

        assertEquals(429, secondResponse.getStatus());
    }

    @Test
    void usesRightmostForwardedForValueWhenOneTrustedProxyIsConfigured() throws Exception {
        RateLimitingFilter filter = buildFilter(1);
        ReflectionTestUtils.setField(filter, "trustedProxyCount", 1);

        FilterChain filterChain = mock(FilterChain.class);

        MockHttpServletRequest firstRequest = post("/api/v1/auth/login");
        firstRequest.addHeader("X-Forwarded-For", "198.51.100.1, 10.0.0.10");
        filter.doFilter(firstRequest, new MockHttpServletResponse(), filterChain);

        MockHttpServletRequest secondRequest = post("/api/v1/auth/login");
        secondRequest.addHeader("X-Forwarded-For", "203.0.113.99, 10.0.0.10");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(secondRequest, secondResponse, filterChain);

        assertEquals(429, secondResponse.getStatus());
    }

    private MockHttpServletRequest post(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRemoteAddr("127.0.0.1");
        return request;
    }

    private MockHttpServletRequest get(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRemoteAddr("127.0.0.1");
        return request;
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private RateLimitingFilter buildFilter(int authMaxRequestsPerMinute) {
        RateLimitingFilter filter = new RateLimitingFilter(new InMemoryRateLimiter(), objectMapper());
        ReflectionTestUtils.setField(filter, "enabled", true);
        ReflectionTestUtils.setField(filter, "authMaxRequestsPerMinute", authMaxRequestsPerMinute);
        ReflectionTestUtils.setField(filter, "passwordResetMaxRequestsPerMinute", authMaxRequestsPerMinute);
        ReflectionTestUtils.setField(filter, "emailVerificationResendMaxRequestsPerMinute", authMaxRequestsPerMinute);
        ReflectionTestUtils.setField(filter, "aiDraftMaxRequestsPerMinute", authMaxRequestsPerMinute);
        ReflectionTestUtils.setField(filter, "trustedProxyCount", 0);
        return filter;
    }
}
