package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.FoodProductSourceEvidenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface FoodProductSourceEvidenceRepository extends JpaRepository<FoodProductSourceEvidenceEntity, Long> {
    boolean existsByFingerprint(String fingerprint);

    @Query("select evidence.fingerprint from FoodProductSourceEvidenceEntity evidence where evidence.fingerprint in :fingerprints")
    List<String> findExistingFingerprints(@Param("fingerprints") Collection<String> fingerprints);
    List<FoodProductSourceEvidenceEntity> findByFoodItemIdOrderByObservedAtDescIdDesc(Long foodItemId);
    List<FoodProductSourceEvidenceEntity> findByFoodItemIdInOrderByObservedAtDescIdDesc(Collection<Long> foodItemIds);
}