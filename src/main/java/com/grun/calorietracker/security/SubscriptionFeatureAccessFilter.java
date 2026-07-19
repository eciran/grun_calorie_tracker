package com.grun.calorietracker.security;

import com.grun.calorietracker.enums.SubscriptionFeature;
import com.grun.calorietracker.service.SubscriptionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
public class SubscriptionFeatureAccessFilter extends OncePerRequestFilter {

    private final SubscriptionService subscriptionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        SubscriptionFeature requiredFeature = resolveFeature(request.getMethod(), request.getRequestURI());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (requiredFeature != null
                && authentication != null
                && authentication.isAuthenticated()
                && !subscriptionService.hasFeatureAccess(authentication.getName(), requiredFeature)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Subscription feature access denied\",\"feature\":\""
                    + requiredFeature.name() + "\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    public static SubscriptionFeature resolveFeature(String method, String path) {
        if (HttpMethod.OPTIONS.matches(method) || path == null) return null;
        if (path.startsWith("/api/v1/water-logs")) return SubscriptionFeature.WATER_TRACKING;
        if (path.startsWith("/api/v1/progress")) return SubscriptionFeature.WEIGHT_PROGRESS;
        if (path.startsWith("/api/v1/exercise-logs")) return SubscriptionFeature.WORKOUT_LOGGING;
        if (path.startsWith("/api/v1/meal-templates")) return SubscriptionFeature.SAVED_MEAL_TEMPLATES;
        if (path.startsWith("/api/v1/health")) return SubscriptionFeature.HEALTH_INTEGRATION;
        if (path.startsWith("/api/v1/fasting")) return SubscriptionFeature.FASTING_BASIC;
        if (path.startsWith("/api/v1/products/custom")) return SubscriptionFeature.CUSTOM_FOOD_LIBRARY;
        if (path.startsWith("/api/v1/food-logs/stats")) return SubscriptionFeature.ADVANCED_ANALYTICS;
        if (path.startsWith("/api/v1/food-logs")) {
            return HttpMethod.GET.matches(method)
                    ? SubscriptionFeature.FOOD_DIARY
                    : SubscriptionFeature.MANUAL_FOOD_LOGGING;
        }
        if (path.startsWith("/api/v1/recipes/images")) return null;
        if (path.startsWith("/api/v1/recipes/public")) return SubscriptionFeature.PUBLIC_RECIPE_LIBRARY;
        if (path.startsWith("/api/v1/recipes")) return SubscriptionFeature.RECIPE_BUILDER;
        return null;
    }
}