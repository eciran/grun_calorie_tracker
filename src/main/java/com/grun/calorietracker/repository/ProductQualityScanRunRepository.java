package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.ProductQualityScanRunEntity;
import com.grun.calorietracker.enums.ProductQualityScanStatus;
import com.grun.calorietracker.enums.ProductQualitySuggestionSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface ProductQualityScanRunRepository extends JpaRepository<ProductQualityScanRunEntity, Long> {
    Page<ProductQualityScanRunEntity> findAllByOrderByStartedAtDesc(Pageable pageable);

    @Query("""
            select coalesce(sum(r.scannedProducts), 0)
            from ProductQualityScanRunEntity r
            where r.source = :source
              and r.status = :status
              and r.completedAt >= :from
            """)
    long sumScannedProductsSince(
            @Param("source") ProductQualitySuggestionSource source,
            @Param("status") ProductQualityScanStatus status,
            @Param("from") LocalDateTime from
    );
}
