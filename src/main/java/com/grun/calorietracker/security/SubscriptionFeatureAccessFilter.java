package com.grun.calorietracker.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.service.SubscriptionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class SubscriptionFeatureAccessFilter extends OncePerRequestFilter {

    private final SubscriptionService subscriptionService;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public SubscriptionFeatureAccessFilter(SubscriptionService subscriptionService, ObjectMapper objectMapper) {
        this(subscriptionService, objectMapper, true);
    }

    public SubscriptionFeatureAccessFilter(
            SubscriptionService subscriptionService,
            ObjectMapper objectMapper,
            boolean enabled
    ) {
        this.subscriptionService = subscriptionService;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        SubscriptionFeature requiredFeature = resolveFeature(request.getMethod(), request.getRequestURI());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (requiredFeature != null
                && authentication != null
                && authentication.isAuthenticated()
                && !subscriptionService.hasFeatureAccess(authentication.getName(), requiredFeature)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            objectMapper.writeValue(
                    response.getOutputStream(),
                    SubscriptionFeatureAccessDeniedResponseFactory.create(request)
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    public static SubscriptionFeature resolveFeature(String method, String path) {
        if (HttpMethod.OPTIONS.matches(method) || path == null) return null;
        if (path.startsWith("/api/v1/water-logs")) return SubscriptionFeature.WATER_TRACKING;
        if (path.startsWith("/api/v1/progress/energy-balance")) return SubscriptionFeature.ADVANCED_ANALYTICS;
        if (path.startsWith("/api/v1/progress/analytics/basic")) return null;
        if (path.startsWith("/api/v1/progress/analytics")) return SubscriptionFeature.ADVANCED_ANALYTICS;
        if (path.startsWith("/api/v1/progress")) return SubscriptionFeature.WEIGHT_PROGRESS;
        if (path.startsWith("/api/v1/exercise-logs")) return SubscriptionFeature.WORKOUT_LOGGING;
        if (path.startsWith("/api/v1/meal-templates")) return SubscriptionFeature.SAVED_MEAL_TEMPLATES;
        if (path.startsWith("/api/v1/grocery-lists")) return SubscriptionFeature.GROCERY_LIST;
        if (path.matches("^/api/v1/meal-plans/\\d+/grocery-list/?$")) return SubscriptionFeature.GROCERY_LIST;
        if (path.startsWith("/api/v1/health")) return SubscriptionFeature.HEALTH_INTEGRATION;
        if (path.startsWith("/api/v1/sleep/providers")) return SubscriptionFeature.HEALTH_INTEGRATION;
        if (path.startsWith("/api/v1/sleep/summary/weekly")) return SubscriptionFeature.ADVANCED_ANALYTICS;
        if (path.startsWith("/api/v1/fasting/advanced")) return SubscriptionFeature.FASTING_ADVANCED;
        if (path.startsWith("/api/v1/fasting")) return SubscriptionFeature.FASTING_BASIC;
        if (path.startsWith("/api/v1/meal-coach")) return SubscriptionFeature.NEXT_MEAL_SUGGESTIONS;
        if (path.startsWith("/api/v1/products/custom")) return SubscriptionFeature.CUSTOM_FOOD_LIBRARY;
        if (path.startsWith("/api/v1/food-logs/stats")) return SubscriptionFeature.ADVANCED_ANALYTICS;
        if (path.startsWith("/api/v1/food-logs")) {
            return HttpMethod.GET.matches(method)
                    ? SubscriptionFeature.FOOD_DIARY
                    : SubscriptionFeature.MANUAL_FOOD_LOGGING;
        }
        if (path.startsWith("/api/v1/recipes/images")) return null;
        if (path.matches("^/api/v1/recipes/\\d+/publish-request/?$")) {
            return SubscriptionFeature.PUBLIC_RECIPE_LIBRARY;
        }
        if (path.startsWith("/api/v1/recipes/public")) return SubscriptionFeature.PUBLIC_RECIPE_LIBRARY;
        if (path.startsWith("/api/v1/recipes")) return SubscriptionFeature.RECIPE_BUILDER;
        return null;
    }
}