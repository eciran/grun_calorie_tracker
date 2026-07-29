package com.grun.calorietracker.security;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.service.SubscriptionService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SubscriptionFeatureAccessFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolveFeature_mapsProtectedEndpointGroups() {
        assertEquals(SubscriptionFeature.WATER_TRACKING,
                SubscriptionFeatureAccessFilter.resolveFeature("POST", "/api/v1/water-logs"));
        assertEquals(SubscriptionFeature.WEIGHT_PROGRESS,
                SubscriptionFeatureAccessFilter.resolveFeature("GET", "/api/v1/progress"));
        assertEquals(SubscriptionFeature.ADVANCED_ANALYTICS,
                SubscriptionFeatureAccessFilter.resolveFeature("GET", "/api/v1/progress/energy-balance"));
        assertEquals(SubscriptionFeature.ADVANCED_ANALYTICS,
                SubscriptionFeatureAccessFilter.resolveFeature("GET", "/api/v1/progress/analytics"));
        assertNull(SubscriptionFeatureAccessFilter.resolveFeature(
                "GET", "/api/v1/progress/analytics/basic"));
        assertEquals(SubscriptionFeature.WORKOUT_LOGGING,
                SubscriptionFeatureAccessFilter.resolveFeature("POST", "/api/v1/exercise-logs"));
        assertEquals(SubscriptionFeature.GROCERY_LIST,
                SubscriptionFeatureAccessFilter.resolveFeature("GET", "/api/v1/meal-plans/42/grocery-list"));
        assertEquals(SubscriptionFeature.GROCERY_LIST,
                SubscriptionFeatureAccessFilter.resolveFeature("PATCH", "/api/v1/grocery-lists/20/items/30/purchased"));
        assertEquals(SubscriptionFeature.GROCERY_LIST,
                SubscriptionFeatureAccessFilter.resolveFeature("GET", "/api/v1/meal-plans/42/grocery-list/"));
        assertNull(SubscriptionFeatureAccessFilter.resolveFeature(
                "GET", "/api/v1/meal-plans/42"));
        assertEquals(SubscriptionFeature.HEALTH_INTEGRATION,
                SubscriptionFeatureAccessFilter.resolveFeature("POST", "/api/v1/sleep/providers/APPLE_HEALTH/sessions"));
        assertEquals(SubscriptionFeature.ADVANCED_ANALYTICS,
                SubscriptionFeatureAccessFilter.resolveFeature("GET", "/api/v1/sleep/summary/weekly"));
        assertEquals(SubscriptionFeature.FASTING_ADVANCED,
                SubscriptionFeatureAccessFilter.resolveFeature("POST", "/api/v1/fasting/advanced/eligibility"));
        assertEquals(SubscriptionFeature.FASTING_BASIC,
                SubscriptionFeatureAccessFilter.resolveFeature("PUT", "/api/v1/fasting/plan"));
        assertEquals(SubscriptionFeature.NEXT_MEAL_SUGGESTIONS,
                SubscriptionFeatureAccessFilter.resolveFeature("GET", "/api/v1/meal-coach/next"));
        assertEquals(null,
                SubscriptionFeatureAccessFilter.resolveFeature("POST", "/api/v1/sleep/sessions"));
        assertEquals(SubscriptionFeature.PUBLIC_RECIPE_LIBRARY,
                SubscriptionFeatureAccessFilter.resolveFeature("GET", "/api/v1/recipes/public"));
        assertEquals(SubscriptionFeature.PUBLIC_RECIPE_LIBRARY,
                SubscriptionFeatureAccessFilter.resolveFeature("POST", "/api/v1/recipes/42/publish-request"));
        assertEquals(SubscriptionFeature.RECIPE_BUILDER,
                SubscriptionFeatureAccessFilter.resolveFeature("POST", "/api/v1/recipes"));
        assertEquals(SubscriptionFeature.ADVANCED_ANALYTICS,
                SubscriptionFeatureAccessFilter.resolveFeature("GET", "/api/v1/food-logs/stats"));
        assertEquals(SubscriptionFeature.MANUAL_FOOD_LOGGING,
                SubscriptionFeatureAccessFilter.resolveFeature("POST", "/api/v1/food-logs"));
        assertEquals(SubscriptionFeature.FOOD_DIARY,
                SubscriptionFeatureAccessFilter.resolveFeature("GET", "/api/v1/food-logs"));
    }

    @Test
    void doFilter_whenResolvedAccessIsDenied_returnsForbiddenWithoutCallingController() throws Exception {
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        FilterChain chain = mock(FilterChain.class);
        SubscriptionFeatureAccessFilter filter = new SubscriptionFeatureAccessFilter(subscriptionService, objectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/water-logs");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE, "feature-request-1");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user@grun.app", "n/a", List.of()));
        when(subscriptionService.hasFeatureAccess("user@grun.app", SubscriptionFeature.WATER_TRACKING))
                .thenReturn(false);

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        var body = objectMapper().readTree(response.getContentAsString());
        assertEquals("SUBSCRIPTION_FEATURE_ACCESS_DENIED", body.get("code").asText());
        assertEquals("feature-request-1", body.get("correlationId").asText());
        assertEquals("/api/v1/water-logs", body.get("path").asText());
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void doFilter_whenGroceryListAccessIsDenied_returnsStandardForbidden() throws Exception {
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        FilterChain chain = mock(FilterChain.class);
        SubscriptionFeatureAccessFilter filter = new SubscriptionFeatureAccessFilter(subscriptionService, objectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/v1/meal-plans/42/grocery-list");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE, "grocery-request-1");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("free@grun.app", "n/a", List.of()));
        when(subscriptionService.hasFeatureAccess("free@grun.app", SubscriptionFeature.GROCERY_LIST))
                .thenReturn(false);

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        var body = objectMapper().readTree(response.getContentAsString());
        assertEquals("SUBSCRIPTION_FEATURE_ACCESS_DENIED", body.get("code").asText());
        assertEquals("grocery-request-1", body.get("correlationId").asText());
        assertEquals("/api/v1/meal-plans/42/grocery-list", body.get("path").asText());
        verify(chain, never()).doFilter(request, response);
    }
    @Test
    void doFilter_whenResolvedAccessIsAllowed_continuesRequest() throws Exception {
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        FilterChain chain = mock(FilterChain.class);
        SubscriptionFeatureAccessFilter filter = new SubscriptionFeatureAccessFilter(subscriptionService, objectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/progress");
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user@grun.app", "n/a", List.of()));
        when(subscriptionService.hasFeatureAccess("user@grun.app", SubscriptionFeature.WEIGHT_PROGRESS))
                .thenReturn(true);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_whenDisabled_continuesWithoutCheckingEntitlements() throws Exception {
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        FilterChain chain = mock(FilterChain.class);
        SubscriptionFeatureAccessFilter filter = new SubscriptionFeatureAccessFilter(subscriptionService, objectMapper(), false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/progress");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(subscriptionService);
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
