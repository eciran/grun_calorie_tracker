package com.grun.calorietracker.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_catalog_audit_state")
@Data
@NoArgsConstructor
public class ProductCatalogAuditStateEntity {
    @Id
    private Long id = 1L;
    private Long scanRunId;
    @Column(nullable = false, length = 24)
    private String status = "READY";
    @Column(length = 80)
    private String rulesetVersion;
    private long cursorId;
    private long highWaterId;
    @Column(length = 64)
    private String leaseOwner;
    private LocalDateTime leaseUntil;
    private LocalDateTime lastCompletedAt;
}
