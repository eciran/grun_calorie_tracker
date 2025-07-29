package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodItemEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FoodItemRepository extends JpaRepository<FoodItemEntity, Long> {
    Optional<FoodItemEntity> findByBarcode(String barcode);
    List<FoodItemEntity> findAll(Specification<FoodItemEntity> spec, Sort sort);
}
