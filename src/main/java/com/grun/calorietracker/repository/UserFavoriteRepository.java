package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodItemEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.entity.UserFavoriteEntity;
import com.grun.calorietracker.enums.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserFavoriteRepository extends JpaRepository<UserFavoriteEntity, Long> {
    long countByUser(UserEntity user);
    List<UserFavoriteEntity> findByUserOrderByCreatedAtDesc(UserEntity user);
    Optional<UserFavoriteEntity> findByUserAndFoodItem(UserEntity user, FoodItemEntity foodItem);
    void deleteByFoodItem(FoodItemEntity foodItem);
    long deleteByUser(UserEntity user);
    @Query("""
            SELECT favorite
            FROM UserFavoriteEntity favorite
            WHERE favorite.user = :user
              AND (favorite.foodItem.verificationStatus IS NULL OR favorite.foodItem.verificationStatus <> :rejectedStatus)
            ORDER BY favorite.createdAt DESC
            """)
    List<UserFavoriteEntity> findAvailableFavorites(
            @Param("user") UserEntity user,
            @Param("rejectedStatus") VerificationStatus rejectedStatus
    );

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
