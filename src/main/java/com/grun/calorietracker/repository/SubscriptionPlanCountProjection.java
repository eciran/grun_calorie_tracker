package com.grun.calorietracker.repository;

import com.grun.calorietracker.enums.SubscriptionPlan;

public interface SubscriptionPlanCountProjection {
    SubscriptionPlan getPlanType();

    long getSubscriptionCount();
}
