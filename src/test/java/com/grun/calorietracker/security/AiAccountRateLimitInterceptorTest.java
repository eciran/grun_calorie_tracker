package com.grun.calorietracker.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiAccountRateLimitInterceptorTest {
    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test void repeatedMvcDispatchIsChargedOnceAndKeysContainNoEmail() throws Exception {
        RequestRateLimiter limiter = mock(RequestRateLimiter.class);
        when(limiter.isAllowed(anyString(), anyInt(), anyLong())).thenReturn(true);
        var interceptor = new AiAccountRateLimitInterceptor(limiter, new ObjectMapper().findAndRegisterModules());
        authenticate("Member@Example.com");
        var request = new MockHttpServletRequest("POST", "/api/v1/ai/insights/daily");
        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        verify(limiter, times(1)).isAllowed(matches("ai-account:[0-9a-f]{64}"), eq(10), eq(60_000L));
    }

    @Test void casingAndDynamicPlanIdsCannotSplitBudgetButGetRequestsAreFree() throws Exception {
        var interceptor = new AiAccountRateLimitInterceptor(new InMemoryRateLimiter(), new ObjectMapper().findAndRegisterModules());
        ReflectionTestUtils.setField(interceptor, "maximum", 1);
        authenticate("Member@Example.com");
        assertThat(interceptor.preHandle(new MockHttpServletRequest("POST", "/api/v1/meal-plans/1/preparation-guide/generate"),
                new MockHttpServletResponse(), new Object())).isTrue();
        authenticate("member@example.com");
        var response = new MockHttpServletResponse();
        assertThat(interceptor.preHandle(new MockHttpServletRequest("POST", "/api/v1/meal-plans/2/preparation-guide/regenerate"),
                response, new Object())).isFalse();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(interceptor.preHandle(new MockHttpServletRequest("GET", "/api/v1/ai/requests/history"),
                new MockHttpServletResponse(), new Object())).isTrue();
    }

    @Test void disabledAndUnauthenticatedRequestsDoNotSpendAccountBudget() throws Exception {
        RequestRateLimiter limiter = mock(RequestRateLimiter.class);
        var interceptor = new AiAccountRateLimitInterceptor(limiter, new ObjectMapper());
        var request = new MockHttpServletRequest("POST", "/api/v1/ai/insights/daily");
        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        authenticate("member@example.com");
        ReflectionTestUtils.setField(interceptor, "enabled", false);
        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        verifyNoInteractions(limiter);
    }

    private void authenticate(String name) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(name, "unused", List.of()));
    }
}
