package com.grun.calorietracker.repository;

import com.grun.calorietracker.entity.ProductCatalogAuditStateEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;

public interface ProductCatalogAuditStateRepository extends JpaRepository<ProductCatalogAuditStateEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ProductCatalogAuditStateEntity s where s.id = 1")
    Optional<ProductCatalogAuditStateEntity> lockState();
}
