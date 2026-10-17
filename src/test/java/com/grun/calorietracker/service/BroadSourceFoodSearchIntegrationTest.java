package com.grun.calorietracker.service;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GRUN_RUN_MIXED_SOURCE_SEARCH", matches = "true")
class BroadSourceFoodSearchIntegrationTest extends RetailerDishFoodSearchIntegrationTest {
    @Override String judgmentResource() { return "r7-broad-source-judgments-v1.json"; }
}
