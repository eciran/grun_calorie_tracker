package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.ProductCatalogAuditItemEntity;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface ProductCatalogAuditItemRepository extends JpaRepository<ProductCatalogAuditItemEntity, Long> {
    @Query("select i.productId from ProductCatalogAuditItemEntity i where i.scanRunId = :runId and i.status = 'RETRY_PENDING' and i.nextAttemptAt <= :now order by i.productId")
    List<Long> dueRetryIds(@Param("runId") Long runId, @Param("now") LocalDateTime now, Pageable pageable);
    long countByScanRunIdAndStatus(Long scanRunId, String status);
    Page<ProductCatalogAuditItemEntity> findByScanRunIdAndStatusOrderByProductIdAsc(Long scanRunId, String status, Pageable pageable);
    Page<ProductCatalogAuditItemEntity> findByScanRunIdOrderByProductIdAsc(Long scanRunId, Pageable pageable);
}
