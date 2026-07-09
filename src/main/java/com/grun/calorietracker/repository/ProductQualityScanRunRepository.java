package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.ProductQualityScanRunEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductQualityScanRunRepository extends JpaRepository<ProductQualityScanRunEntity, Long> {
    Page<ProductQualityScanRunEntity> findAllByOrderByStartedAtDesc(Pageable pageable);
}
