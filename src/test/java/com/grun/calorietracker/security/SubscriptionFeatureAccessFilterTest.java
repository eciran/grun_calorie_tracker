package com.grun.calorietracker.security;

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
                SubscriptionFeatureAccessFilter.resolveFeature("GET", "/api/v1/progress/analytics"));
        assertEquals(SubscriptionFeature.WORKOUT_LOGGING,
                SubscriptionFeatureAccessFilter.resolveFeature("POST", "/api/v1/exercise-logs"));
        assertEquals(SubscriptionFeature.HEALTH_INTEGRATION,
                SubscriptionFeatureAccessFilter.resolveFeature("POST", "/api/v1/sleep/providers/APPLE_HEALTH/sessions"));
        assertEquals(SubscriptionFeature.ADVANCED_ANALYTICS,
                SubscriptionFeatureAccessFilter.resolveFeature("GET", "/api/v1/sleep/summary/weekly"));
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
        SubscriptionFeatureAccessFilter filter = new SubscriptionFeatureAccessFilter(subscriptionService);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/water-logs");
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user@grun.app", "n/a", List.of()));
        when(subscriptionService.hasFeatureAccess("user@grun.app", SubscriptionFeature.WATER_TRACKING))
                .thenReturn(false);

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertEquals(true, response.getContentAsString().contains("WATER_TRACKING"));
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void doFilter_whenResolvedAccessIsAllowed_continuesRequest() throws Exception {
        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        FilterChain chain = mock(FilterChain.class);
        SubscriptionFeatureAccessFilter filter = new SubscriptionFeatureAccessFilter(subscriptionService);
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
        SubscriptionFeatureAccessFilter filter = new SubscriptionFeatureAccessFilter(subscriptionService, false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/progress");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(subscriptionService);
    }
}
