package com.grun.calorietracker.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_catalog_audit_items")
@Data
@NoArgsConstructor
public class ProductCatalogAuditItemEntity {
    @Id
    private Long productId;
    @Column(nullable = false)
    private Long scanRunId;
    @Column(nullable = false, length = 24)
    private String status;
    @Column(nullable = false, length = 80)
    private String rulesetVersion;
    @Column(length = 64)
    private String inputFingerprint;
    private int attempts;
    private LocalDateTime lastScannedAt;
    private LocalDateTime nextAttemptAt;
    @Column(columnDefinition = "text")
    private String findingsJson;
    @Column(length = 255)
    private String lastErrorCode;
}
