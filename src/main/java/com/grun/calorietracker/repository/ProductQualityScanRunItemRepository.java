package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.ProductQualityScanRunItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductQualityScanRunItemRepository extends JpaRepository<ProductQualityScanRunItemEntity, Long> {
    List<ProductQualityScanRunItemEntity> findByScanRunIdOrderByIdAsc(Long scanRunId);
}