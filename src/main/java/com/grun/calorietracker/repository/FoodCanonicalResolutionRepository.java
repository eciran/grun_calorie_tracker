package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodCanonicalResolutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface FoodCanonicalResolutionRepository extends JpaRepository<FoodCanonicalResolutionEntity, String> {
    List<FoodCanonicalResolutionEntity> findByCanonicalFoodKeyIn(Collection<String> canonicalFoodKeys);
}