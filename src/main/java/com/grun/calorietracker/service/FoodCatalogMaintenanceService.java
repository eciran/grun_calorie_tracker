package com.grun.calorietracker.service;

public interface FoodCatalogMaintenanceService {
    int refreshStaleSourceQueue();
    void runScheduledQualityQueue();
}