package com.grun.calorietracker.service;
public interface FoodProductEvidenceRetentionService {
    int cleanupExpiredAndWithdrawn();
    void purgeForUser(Long userId);
}
