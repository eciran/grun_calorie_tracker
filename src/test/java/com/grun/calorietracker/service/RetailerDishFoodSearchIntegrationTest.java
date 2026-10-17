package com.grun.calorietracker.service;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GRUN_RUN_MIXED_SOURCE_SEARCH", matches = "true")
class RetailerDishFoodSearchIntegrationTest extends MixedSourceFoodSearchIntegrationTest {
    @Override String judgmentResource() { return "r7-retailer-dish-judgments-v1.json"; }
    @Override String corpusEnvironment() { return "GRUN_R7_RETAILER_SOURCE_CORPUS"; }
    @Override int expectedRecords() { return 59; }
}
