package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.ProductionVerificationRunEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductionVerificationRunRepository extends JpaRepository<ProductionVerificationRunEntity, Long> {
    Page<ProductionVerificationRunEntity> findByProvider(String provider, Pageable pageable);
}
