package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.FoodItemEntity;

public interface CatalogPublicationService {

    FoodItemEntity publishNew(
            FoodItemEntity product,
            String actor,
            String reason,
            String correlationId
    );

    FoodItemEntity publish(
            Long productId,
            String actor,
            String reason,
            String correlationId
    );
}
