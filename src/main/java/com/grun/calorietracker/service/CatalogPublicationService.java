package com.grun.calorietracker.service;

import com.grun.calorietracker.entity.FoodItemEntity;

import java.util.List;

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

    List<FoodItemEntity> publishBatch(
            List<Long> productIds,
            String actor,
            String reason,
            String correlationId
    );
}
