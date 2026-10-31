package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodProductFamilyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodProductFamilyRepository extends JpaRepository<FoodProductFamilyEntity, Long> {
    java.util.Optional<FoodProductFamilyEntity> findFirstByFamilyKeyIgnoreCaseAndActiveTrue(String familyKey);
}
