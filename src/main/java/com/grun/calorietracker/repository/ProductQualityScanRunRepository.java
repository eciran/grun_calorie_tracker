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
import java.util.List;

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

    @Query(value = """
            SELECT CAST(started_at AS date) AS scan_date,
                   COALESCE(SUM(scanned_products), 0) AS scanned_products,
                   COALESCE(SUM(created_suggestions), 0) AS created_suggestions,
                   COALESCE(SUM(validated_products), 0) AS validated_products,
                   COUNT(*) FILTER (WHERE status = 'FAILED') AS failed_runs
            FROM product_quality_scan_runs
            WHERE started_at >= :from
            GROUP BY CAST(started_at AS date)
            ORDER BY scan_date
            """, nativeQuery = true)
    List<Object[]> summarizeDailySince(@Param("from") LocalDateTime from);
}
