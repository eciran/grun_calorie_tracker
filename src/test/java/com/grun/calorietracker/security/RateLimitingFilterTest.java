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
        RateLimitingFilter filter = new RateLimitingFilter(new InMemoryRateLimiter(), objectMapper());
        ReflectionTestUtils.setField(filter, "enabled", true);
        ReflectionTestUtils.setField(filter, "authMaxRequestsPerMinute", 1);

        FilterChain filterChain = mock(FilterChain.class);

        MockHttpServletRequest firstRequest = post("/api/auth/login");
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        filter.doFilter(firstRequest, firstResponse, filterChain);

        MockHttpServletRequest secondRequest = post("/api/auth/login");
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(secondRequest, secondResponse, filterChain);

        assertEquals(200, firstResponse.getStatus());
        assertEquals(429, secondResponse.getStatus());
        assertEquals("application/json", secondResponse.getContentType());
        verify(filterChain, times(1)).doFilter(firstRequest, firstResponse);
    }

    @Test
    void unprotectedPathDoesNotConsumeLimit() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(new InMemoryRateLimiter(), objectMapper());
        ReflectionTestUtils.setField(filter, "enabled", true);
        ReflectionTestUtils.setField(filter, "authMaxRequestsPerMinute", 1);

        FilterChain filterChain = mock(FilterChain.class);

        MockHttpServletRequest request = post("/api/food-logs");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void v1ProtectedAuthPathIsRateLimited() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter(new InMemoryRateLimiter(), objectMapper());
        ReflectionTestUtils.setField(filter, "enabled", true);
        ReflectionTestUtils.setField(filter, "authMaxRequestsPerMinute", 1);

        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(post("/api/v1/auth/refresh"), new MockHttpServletResponse(), filterChain);
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(post("/api/v1/auth/refresh"), secondResponse, filterChain);

        assertEquals(429, secondResponse.getStatus());
    }

    private MockHttpServletRequest post(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRemoteAddr("127.0.0.1");
        return request;
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }
}
