package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodBrandedDuplicateSearchCollapseMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FoodBrandedDuplicateSearchCollapseMemberRepository
        extends JpaRepository<FoodBrandedDuplicateSearchCollapseMemberEntity, Long> {
    List<FoodBrandedDuplicateSearchCollapseMemberEntity> findByCollapseIdOrderBySuppressedFoodItemId(Long collapseId);
    void deleteByCollapseId(Long collapseId);
}
