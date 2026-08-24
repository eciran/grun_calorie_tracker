package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.FoodProductReviewCaseEntity;
import org.springframework.core.io.Resource;

public interface CatalogMediaService {
    boolean promoteApprovedFrontImage(FoodProductReviewCaseEntity reviewCase, FoodItemEntity product);

    Resource load(String publicToken);
}
