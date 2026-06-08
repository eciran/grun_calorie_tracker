package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.RecipeEntity;
import com.grun.calorietracker.entity.UserEntity;
import com.grun.calorietracker.enums.RecipeVisibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RecipeRepository extends JpaRepository<RecipeEntity, Long> {
    List<RecipeEntity> findByOwnerUserAndArchivedFalseOrderByUpdatedAtDesc(UserEntity ownerUser);

    @Query("""
            SELECT r
            FROM RecipeEntity r
            WHERE r.ownerUser = :ownerUser
              AND r.archived = false
              AND (:query IS NULL OR lower(r.name) LIKE lower(concat('%', :query, '%')))
              AND (:mealType IS NULL OR r.mealType = :mealType)
            ORDER BY r.updatedAt DESC
            """)
    List<RecipeEntity> searchOwnedRecipes(@Param("ownerUser") UserEntity ownerUser,
                                           @Param("query") String query,
                                           @Param("mealType") String mealType);

    Optional<RecipeEntity> findByIdAndOwnerUserAndArchivedFalse(Long id, UserEntity ownerUser);

    List<RecipeEntity> findByVisibilityAndArchivedFalseOrderByUpdatedAtDesc(RecipeVisibility visibility);

    @Query("""
            SELECT r
            FROM RecipeEntity r
            WHERE r.id = :id
              AND r.archived = false
              AND (
                    r.ownerUser = :user
                    OR r.visibility = com.grun.calorietracker.enums.RecipeVisibility.PUBLIC_ADMIN
                  )
            """)
    Optional<RecipeEntity> findAccessibleRecipe(@Param("id") Long id, @Param("user") UserEntity user);
}
