package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodBrandAliasEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FoodBrandAliasRepository extends JpaRepository<FoodBrandAliasEntity, Long> {
    Optional<FoodBrandAliasEntity> findByNormalizedAlias(String normalizedAlias);
}
