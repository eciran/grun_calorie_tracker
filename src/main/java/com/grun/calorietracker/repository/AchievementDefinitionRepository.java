package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.AchievementDefinitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AchievementDefinitionRepository extends JpaRepository<AchievementDefinitionEntity, Long> {
    List<AchievementDefinitionEntity> findByActiveTrueOrderBySortOrderAscCodeAsc();

    List<AchievementDefinitionEntity> findAllByOrderBySortOrderAscCodeAsc();

    Optional<AchievementDefinitionEntity> findByCode(String code);

    boolean existsByCode(String code);
}
