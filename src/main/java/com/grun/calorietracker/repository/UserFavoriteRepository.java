package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.UserFavoriteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserFavoriteRepository extends JpaRepository<UserFavoriteEntity, Long> {

    @Modifying
    @Query(value = """
            DELETE FROM user_favorites source
            WHERE source.food_item_id IN (:sourceFoodItemIds)
              AND EXISTS (
                  SELECT 1
                  FROM user_favorites target
                  WHERE target.user_id = source.user_id
                    AND target.food_item_id = :targetFoodItemId
              )
            """, nativeQuery = true)
    int deleteConflictingFavoritesBeforeMerge(
            @Param("targetFoodItemId") Long targetFoodItemId,
            @Param("sourceFoodItemIds") List<Long> sourceFoodItemIds
    );

    @Modifying
    @Query("UPDATE UserFavoriteEntity favorite SET favorite.foodItem = :targetFoodItem WHERE favorite.foodItem.id IN :sourceFoodItemIds")
    int reassignFoodItemReferences(
            @Param("targetFoodItem") FoodItemEntity targetFoodItem,
            @Param("sourceFoodItemIds") List<Long> sourceFoodItemIds
    );
}
