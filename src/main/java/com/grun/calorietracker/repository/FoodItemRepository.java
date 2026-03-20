package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodItemEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FoodItemRepository extends JpaRepository<FoodItemEntity, Long> {

    Optional<FoodItemEntity> findByBarcode(String barcode);

    List<FoodItemEntity> findAll(Specification<FoodItemEntity> spec, Sort sort);

    @Query("""
            SELECT f
            FROM FoodItemEntity f
            WHERE (:query IS NULL OR LOWER(f.name) LIKE LOWER(CONCAT('%', :query, '%')))
              AND (:minCalories IS NULL OR f.calories >= :minCalories)
              AND (:maxCalories IS NULL OR f.calories <= :maxCalories)
              AND (:nutriScore IS NULL OR LOWER(f.nutriScore) = LOWER(:nutriScore))
            ORDER BY f.name ASC
            """)
    List<FoodItemEntity> searchFoodItems(
            @Param("query") String query,
            @Param("minCalories") Double minCalories,
            @Param("maxCalories") Double maxCalories,
            @Param("nutriScore") String nutriScore
    );
}